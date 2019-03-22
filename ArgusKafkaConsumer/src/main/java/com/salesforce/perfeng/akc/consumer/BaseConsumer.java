package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.entity.TSDBEntity;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;

abstract class BaseConsumer {

    static final int MAX_ARGUS_METRIC_FIELD_SIZE=256;
    static final String DELIMITER = ".";
    static final String NONE = "None";
    private static final int RETRIES = Integer.parseInt(AKCConfiguration.getParameter(AKCConfiguration.Parameter.RETRIES));
    private static final Logger logger = LoggerFactory.getLogger(BaseConsumer.class);

    static final long MAX_METRICS_AGE_MS = Long.parseLong(AKCConfiguration.getParameter(AKCConfiguration.Parameter.MAX_METRICS_AGE_MS));
    private final boolean MAX_METRICS_AGE_ENABLED = Boolean.parseBoolean(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ENABLE_MAX_METRICS_AGE));

    InstrumentationService instrumentationService;
    IBlacklistService blacklistService;

    BaseConsumer(InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        this.blacklistService = blacklistService;
        this.instrumentationService = instrumentationService;
    }

    /**
     * Retry Logic. Back off for increasing amount of time between retries. Eventually drop the chunk if unable
     * to send after configured number of retries.
     *
     * @param entities The objects to retry sending
     * @param consumer lambda that does the actual retry
     *
     * @return true if the retry was successful otherwise false.
     */
    static <T> boolean retryWithExponentialBackoff(List<T> entities, Consumer<List<T>> consumer) {
        long backoffIntervalMs = 200;

        for(int i=1; i <= RETRIES; i++) {
            logger.info("Retry {}: Backing off for {} ms before retrying.", i, backoffIntervalMs);

            try {
                Thread.sleep(backoffIntervalMs);
                consumer.accept(entities);

                return true;
            } catch (InterruptedException ie) {
                logger.warn("Retry was sleeping. Got interrupted.");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logger.warn("Exception while retrying to post.", e);
            }

            backoffIntervalMs = backoffIntervalMs * 2;
        }

        logger.warn("Failed to post messages after {} retries. Dropping this chunk.", RETRIES);
        return false;
    }

    static <T> int processInBatches(List<T> objects, int batchSize, Consumer<List<T>> processor) {
        if (batchSize == 0) {
            processor.accept(objects);
            return 1;
        } else {
            int i = 0;
            int batches = 0;
            while (i < objects.size()) {
                List<T> batch = objects.subList(i, Math.min(i + batchSize, objects.size()));
                processor.accept(batch);
                i += batchSize;
                batches++;
            }
            return batches;
        }
    }

    static String buildArgusMetricName(List<Object> ajnaMetricName) {
        StringBuilder metricSB = new StringBuilder();
        for(Object obj : ajnaMetricName) {
            if (obj instanceof String) {
                String name = obj.toString().trim();
                metricSB.append(name);
            } else if (obj instanceof Map<?, ?>) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> nameMap = (Map<String, String>) obj;
                    nameMap.forEach((key, value) -> {
                        metricSB.append(key);
                        metricSB.append("-");
                        metricSB.append(value);
                        metricSB.append("-");
                    });
                    metricSB.deleteCharAt(metricSB.length() - 1);
                } catch (ClassCastException e) {
                    logger.warn("Failed to cast object to a map.", e);
                }
            }
            else {
                logger.warn("Only String and Map object types are supported in metric name list.");
            }

            metricSB.append(DELIMITER);
        }
        if(metricSB.length() > 0) {
            metricSB.deleteCharAt(metricSB.length() - 1);
        }

        return metricSB.toString();
    }

    /**
     * go thru ajna tags and set them as TSDBEntity's argus tags
     * @param ajnaTags ajna tags
     * @param entity the argus entity that will get the ajnaTags after they are converted to argus tags
     */
    void extractAjnaTags(Map<CharSequence, CharSequence> ajnaTags, TSDBEntity entity) {
        if (ajnaTags == null) {
            return;
        }

        Map<String, String> argusTags = new HashMap<>();

        ajnaTags.forEach((key, value) -> {
            String keyString = key.toString();
            String valueString = value.toString();
            if (blacklistService.isBlacklistedTag(keyString)) {
                logger.trace("Encountered a blacklisted tag: {}. Ignoring...", keyString);
            } else if (!StringUtils.isBlank(valueString)) {
                argusTags.put(replaceUnsupportedChars(keyString), replaceUnsupportedChars(valueString));
            }
        });

        entity.setTags(argusTags);
    }

    boolean isTimestampInvalidOrOld(long ajnaTimestamp, Map<String, String> dpTags, Consumer<Map<String, String>> tooOld, Consumer<Map<String, String>> timestampInvalid) {
        // Ajna metric timestamp may be in seconds OR milliseconds
        // If timestamp has exactly 13 digits (non-zero quotient with 10^12), the timestamp units must be in milliseconds
        // Exactly 10 digits must be in seconds. This bounds the expected time range to between Sep 9, 2001 to Nov 20, 2286
        long millisQuotient = ajnaTimestamp / 1000000000000L;
        long secondsQuotient = ajnaTimestamp / 1000000000L;
        if (millisQuotient > 0 && millisQuotient < 10) {
            if (System.currentTimeMillis() - ajnaTimestamp > MAX_METRICS_AGE_MS) {
                tooOld.accept(dpTags);
                return MAX_METRICS_AGE_ENABLED;
            }
        } else if (secondsQuotient > 0 && secondsQuotient < 10){
            if (System.currentTimeMillis() - ajnaTimestamp*1000 > MAX_METRICS_AGE_MS) {
                tooOld.accept(dpTags);
                return MAX_METRICS_AGE_ENABLED;
            }
        } else {
            timestampInvalid.accept(dpTags);
            return true;
        }
        return false;
    }

    private static boolean areTagSizesSafe(Map<String, String> tags) {
        if(null != tags) {
            for(Map.Entry<String, String> entry : tags.entrySet()) {
                String tagk = entry.getKey();
                String tagv = entry.getValue();
                if(null != tagk && tagk.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
                    logger.trace("Argus metric tagk [" + tagk.substring(0,50) +
                            "] too large: " + tagk.length());
                    return false;
                }
                if(null != tagv && tagv.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
                    logger.trace("Argus metric tagv [" + tagv.substring(0,50) +
                            "] too large: " + tagv.length());
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isTSDBEntitySafe(TSDBEntity entity) {
        // Verify scope size
        String scope = entity.getScope();
        if(null != scope && scope.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus metric scope [" + scope.substring(0,50) +
                    "] too large: " + scope.length());
            return false;
        }
        // Verify metric name size
        String metricname = entity.getMetric();
        if(null != metricname && metricname.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus metric metricname [" + metricname.substring(0,50) +
                    "] too large: " + metricname.length());
            return false;
        }
        // Verify tag sizes
        return areTagSizesSafe(entity.getTags());
    }

}
