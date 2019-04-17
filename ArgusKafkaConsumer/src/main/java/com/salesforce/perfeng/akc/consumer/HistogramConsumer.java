package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.entity.HistogramBucket;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.mandm.ajna.AjnaWire;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.mandm.avro.util.AjnaWireFormatDecoder;
import com.salesforce.mandm.avro.util.AvroUtils;
import com.salesforce.quota.IBlacklistService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;
import static com.salesforce.perfeng.akc.AKCUtil.resolveCharSequence;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_BLOCKED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_DROPPED_TOOLARGE;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_POSTED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_PROCESS_LATENCY;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_TIMESTAMP_INVALID;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_TOO_OLD;

class HistogramConsumer extends BaseConsumer {
    private static final Logger logger = LoggerFactory.getLogger(HistogramConsumer.class);
    private static final String HISTOGRAM_SCHEMA_FINGERPRINT = AvroUtils.getSchemaFingerprint(com.salesforce.mandm.ajna.Histogram.getClassSchema());

    AjnaWireFormatDecoder<com.salesforce.mandm.ajna.Histogram> histogramAvroDecoder;
    private TSDBService tsdbService;

    HistogramConsumer(TSDBService tsdbService, InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        super(instrumentationService, blacklistService);
        this.tsdbService = tsdbService;

        this.histogramAvroDecoder = new AjnaWireFormatDecoder<>();
    }

    void processAjnaWireRecord(List<Histogram> argusHistograms, AjnaWire ajnaWire, Map<String, String> tags) {
        List<com.salesforce.mandm.ajna.Histogram> ajnaHistograms = extractAjnaHistograms(ajnaWire);
        if (ajnaHistograms == null) {
            instrumentationService.updateCounter(HISTOGRAM_DROPPED, 1, tags);
            return;
        }
        instrumentationService.updateCounter(HISTOGRAM_CONSUMED, ajnaHistograms.size(), tags);

        for (com.salesforce.mandm.ajna.Histogram ajnaHistogram : ajnaHistograms) {
            try {
                Histogram argusHistogram = transformToArgusHistogram(ajnaHistogram, tags,
                        this::updateHistogramTooOld, this::updateHistogramTimestampInvalid,
                        this::updateHistogramBlocked, this::updateHistogramDropped);
                if (argusHistogram == null) {
                    continue;
                }

                if (isHistogramSizeSafe(argusHistogram)) {
                    argusHistograms.add(argusHistogram);
                } else {
                    tags.put(SchemaField.SERVICE,
                            resolveCharSequence(ajnaHistogram.getService(), NONE, cs -> replaceUnsupportedChars(cs.subSequence(0, Math.min(cs.length(), 50)).toString().toLowerCase())));
                    instrumentationService.updateCounter(HISTOGRAM_DROPPED_TOOLARGE, 1, tags);
                }
            } catch(Exception e) {
                instrumentationService.updateCounter(HISTOGRAM_DROPPED, 1, tags);
                logger.warn("Exception while processing single ajnaHistogram.  Dropping this ajna histogram", e);
            }
        }
    }

    private void updateHistogramTooOld(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_TOO_OLD, 1, tags);
    }

    void updateHistogramTimestampInvalid(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_TIMESTAMP_INVALID, 1, tags);
    }

    void updateHistogramDropped(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_DROPPED, 1, tags);
    }

    void updateHistogramBlocked(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_BLOCKED, 1, tags);
    }

    List<com.salesforce.mandm.ajna.Histogram> extractAjnaHistograms(AjnaWire ajnaWire) {
        try {
            return histogramAvroDecoder.listFromAjnaWire(ajnaWire, HISTOGRAM_SCHEMA_FINGERPRINT);
        }
        catch (Exception e) {
            logger.warn("Exception while extracting ajna histogram.  Dropping this chunk of ajna histogram", e);
            return null;
        }
    }

    void processArgusHistogram(List<Histogram> histograms) {
        int size = histograms.size();
        double start = System.currentTimeMillis();
        try {
            tsdbService.putHistograms(histograms);
            instrumentationService.updateCounter(HISTOGRAM_POSTED, size, null);
        } catch (Exception ex) {
            logger.warn("Retrying histogram commit due to exception " + ex);
            if (retryWithExponentialBackoff(histograms, tsdbService::putHistograms)) {
                instrumentationService.updateCounter(HISTOGRAM_POSTED, size, null);
            } else {
                instrumentationService.updateCounter(HISTOGRAM_DROPPED, size, null);
            }
        } finally {
            instrumentationService.updateTimer(HISTOGRAM_PROCESS_LATENCY,
                    ((double)System.currentTimeMillis()-start),
                    null);
        }
    }

    Histogram transformToArgusHistogram(com.salesforce.mandm.ajna.Histogram ajnaHistogram, Map<String, String> tags,
                                        Consumer<Map<String, String>> tooOld,
                                        Consumer<Map<String, String>> timestampInvalid,
                                        Consumer<Map<String, String>> blocked,
                                        Consumer<Map<String, String>> dropped) {
        logger.trace("Consuming Histogram " +ajnaHistogram.toString());

        if (isTimestampInvalidOrOld(ajnaHistogram.getTimestamp(), tags, tooOld, timestampInvalid)) {
            return null;
        }

        try {
            StringBuilder scopeSB = new StringBuilder();

            //service
            if (!StringUtils.isBlank(ajnaHistogram.getService())) {
                scopeSB.append(ajnaHistogram.getService().toString().toLowerCase()).append(DELIMITER);
            }

            //sub-service
            if (!StringUtils.isBlank(ajnaHistogram.getSubservice())) {
                scopeSB.append(ajnaHistogram.getSubservice().toString().toLowerCase()).append(DELIMITER);
            }

            if (scopeSB.length() > 0) {
                scopeSB.deleteCharAt(scopeSB.length() - 1);
            }
            String scope = scopeSB.toString();

            //TODO see if blacklistservice should have dedicated support for histogram scope
            if(blacklistService.isBlacklistedMetricScope(scope)) {
                logger.trace("Encountered a histogram with blacklisted scope: {}. Dropping {}", scope, ajnaHistogram);
                Map<String,String> droppedScopes = new HashMap<>();
                droppedScopes.put("scope", scope);
                droppedScopes.putAll(tags);
                blocked.accept(droppedScopes);
                return null;
            }

            //create Argus histogram
            String metricName = buildArgusMetricName(ajnaHistogram.getMetricName());
            Histogram argusHistogram = new Histogram(replaceUnsupportedChars(scope), replaceUnsupportedChars(metricName));
            extractAjnaTags(ajnaHistogram.getTags(), argusHistogram);
            argusHistogram.setTimestamp(ajnaHistogram.getTimestamp());
            argusHistogram.setOverflow(ajnaHistogram.getOverflow());
            argusHistogram.setUnderflow(ajnaHistogram.getUnderflow());
            Map<HistogramBucket, Long> argusBuckets = new HashMap<>();
            ajnaHistogram.getBuckets().forEach((key, value)-> argusBuckets.put(new HistogramBucket(key.toString()), value));
            argusHistogram.setBuckets(argusBuckets);

            return argusHistogram;
        } catch (Exception e) {
            logger.warn("Failed to tranform to Argus Histogram.  Dropping this Ajna Histogram: " + ajnaHistogram, e);
            dropped.accept(tags);
            return null;
        }
    }

    static boolean isHistogramSizeSafe(Histogram argusHistogram) {
        if (argusHistogram == null) {
            return false;
        }

        if (!isTSDBEntitySafe(argusHistogram)) {
            return false;
        }

        // Verify displayname size
        String displayname = argusHistogram.getDisplayName();
        if(null != displayname && displayname.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus histogram displayname [" + displayname.substring(0,50) +
                    "] too large: " + displayname.length());
            return false;
        }
        // Verify units size
        String units = argusHistogram.getUnits();
        if(null != units && units.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus histogram units [" + units.substring(0,50) +
                    "] too large: " + units.length());
            return false;
        }

        return true;
    }

}
