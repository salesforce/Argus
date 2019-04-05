package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.Lists;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.mandm.ajna.AjnaWire;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.mandm.avro.util.AjnaWireFormatDecoder;
import com.salesforce.mandm.avro.util.AvroUtils;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
import com.salesforce.quota.IInfraction;
import com.salesforce.quota.IInfractionList;
import com.salesforce.quota.IPrincipal;
import com.salesforce.quota.IQuotaService;
import com.salesforce.quota.InMemoryInfractionList;
import com.salesforce.quota.PolicyTrigger;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

import static com.salesforce.dva.argus.entity.MetricSchemaRecord.RETENTION_DISCOVERY;
import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;

abstract class BaseMetricConsumer extends BaseConsumer {
    private static final Logger logger = LoggerFactory.getLogger(BaseMetricConsumer.class);
    private static final String ULI_METRICS_TOPIC_NAME="FunnelProto";
    static final int DEFAULT_SLOWDOWN_SLEEP_MAX_MS=2000;
    static final String METRIC_SCHEMA_FINGERPRINT = AvroUtils.getSchemaFingerprint(com.salesforce.mandm.ajna.Metric.getClassSchema());

    boolean quotaSystemSwitch;
    private boolean scopeQuotaEnabled;
    private boolean quotaGroupThrottlingEnabled;

    private final Random random;
    private IQuotaService quotaService;
    private IQuotaInfoProvider quotaInfoProvider;

    private PlaintextMetricParser plainTextDecoder;
    AjnaWireFormatDecoder<com.salesforce.mandm.ajna.Metric> metricAvroDecoder;

    BaseMetricConsumer(InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        super(instrumentationService, blacklistService);

        quotaService = new PerformanceMetricsQuotaServiceDecorator(QuotaUtilFactory.getQuotaService(), instrumentationService);
        quotaInfoProvider = QuotaUtilFactory.getQuotaInfoProvider();

        plainTextDecoder = new PlaintextMetricParser();
        metricAvroDecoder = new AjnaWireFormatDecoder<>();

        random=new Random();
        quotaSystemSwitch = StringUtils.equalsIgnoreCase(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_SWITCH), "ON");
        scopeQuotaEnabled = StringUtils.equalsIgnoreCase(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_SCOPE_LEVEL_ENABLED), "true");
        quotaGroupThrottlingEnabled = StringUtils.equalsIgnoreCase(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_GROUP_THROTTLING_ENABLED), "true");
    }

    /**
     * To be implemented by subclasses
     * @param records kafka records
     * @param argusMetrics this is exposed for testing
     * @param argusHistograms this is exposed for testing
     */
    abstract void processAjnaMetricKafkaRecords(ConsumerRecords<byte[], byte[]> records, Map<String, Metric> argusMetrics, List<Histogram> argusHistograms);

    List<com.salesforce.mandm.ajna.Metric> extractAjnaMetrics(ConsumerRecord<byte[], byte[]> record, AjnaWire ajnaWire) {
        try {
            if (record.topic().equalsIgnoreCase(ULI_METRICS_TOPIC_NAME)) {
                return Lists.newArrayList(plainTextDecoder.parse(record.value()));
            } else {
                return metricAvroDecoder.listFromAjnaWire(ajnaWire, METRIC_SCHEMA_FINGERPRINT);
            }
        } catch (Exception e) {
            logger.warn("Exception while decoding message from bytes. Dropping this chunk of ajna metrics", e);
            return null;
        }
    }

    /**
     * Transforms an Ajna Metric into an Argus Metric.
     *
     * Ajna metric has the following fields. Service, tags, metricName, timestamp and value are mandatory:
     *
     * service: String
     * subservice: String
     * tags: Map<CharSequence, CharSequence>
     * metricName: List<Object>
     * timestamp: Long
     * value: Double
     *
     *
     * This entity is transformed into an Argus entity as follows:
     *
     * scope: <service>.<subservice>.<tag.datacenter>.<tag.superpod>.<tag.pod>
     * metric: metricName List serialized as a String using . as a delimiter
     * tags: all other entries from tags map
     * datapoints: [{timestamp, value}]
     *
     *
     * @param ajnaMetric - The Ajna Metric to transform
     *
     * @return argusMetric - The Argus Metric generated from the Ajna Metric or null if some exception occurs.
     */
    Metric transformToArgusMetric(com.salesforce.mandm.ajna.Metric ajnaMetric,
                                  Map<String, String> dpTags,
                                  Consumer<Map<String, String>> tooOld,
                                  Consumer<Map<String, String>> timestampInvalid,
                                  Consumer<Map<String, String>> blocked,
                                  Consumer<Map<String, String>> dropped) {
        logger.trace("Consumed Metric: " + ajnaMetric.toString());

        if (isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, tooOld, timestampInvalid)) {
            return null;
        }

        try {
            StringBuilder scopeSB = new StringBuilder();
            Map<String, String> metatagsMap = new HashMap<>();

            //service
            if(!StringUtils.isBlank(ajnaMetric.getService())) {
                scopeSB.append(ajnaMetric.getService().toString().toLowerCase()).append(DELIMITER);
                metatagsMap.put(SchemaField.SERVICE, ajnaMetric.getService().toString().toLowerCase());
            }

            //sub-service
            if(!StringUtils.isBlank(ajnaMetric.getSubservice())) {
                scopeSB.append(ajnaMetric.getSubservice().toString().toLowerCase()).append(DELIMITER);
                metatagsMap.put(SchemaField.SUB_SERVICE, ajnaMetric.getSubservice().toString().toLowerCase());
            }

            if(ajnaMetric.getTags() != null && ajnaMetric.getTags().size() > 0) {
                //Datacenter
                CharSequence dcValue = ajnaMetric.getTags().remove(SchemaField.TAG_KEY_DATACENTER);
                if(!StringUtils.isBlank(dcValue)) {
                    scopeSB.append(dcValue.toString().toUpperCase()).append(DELIMITER);
                    metatagsMap.put(SchemaField.TAG_KEY_DATACENTER, dcValue.toString().toUpperCase());
                }

                //Superpod
                CharSequence superpodValue = ajnaMetric.getTags().remove(SchemaField.TAG_KEY_SUPERPOD);
                if(!StringUtils.isBlank(superpodValue)) {
                    scopeSB.append(superpodValue.toString().toUpperCase()).append(DELIMITER);
                    metatagsMap.put(SchemaField.TAG_KEY_SUPERPOD, superpodValue.toString().toUpperCase());
                }

                //pod
                CharSequence podValue = ajnaMetric.getTags().remove(SchemaField.TAG_KEY_POD);
                if(!StringUtils.isBlank(podValue)) {
                    scopeSB.append(podValue.toString().toLowerCase()).append(DELIMITER);
                    metatagsMap.put(SchemaField.TAG_KEY_POD, podValue.toString().toLowerCase());
                }

                //retention
                CharSequence retentionValue = ajnaMetric.getTags().remove(RETENTION_DISCOVERY);
                if (!StringUtils.isBlank(retentionValue)) {
                    metatagsMap.put(RETENTION_DISCOVERY, retentionValue.toString());
                }
            }

            if(scopeSB.length() > 0) {
                scopeSB.deleteCharAt(scopeSB.length() - 1);
            }
            String scope = scopeSB.toString();

            if(blacklistService.isBlacklistedMetricScope(scope)) {
                logger.trace("Encountered a blacklisted scope: {}. Dropping {}", scope, ajnaMetric);
                Map<String,String> droppedScopes = new HashMap<>();
                droppedScopes.put("scope", scope);
                droppedScopes.putAll(dpTags);
                blocked.accept(droppedScopes);
                return null;
            }

            //create Argus metric.
            String metricName = buildArgusMetricName(ajnaMetric.getMetricName());
            Metric argusMetric = new Metric(replaceUnsupportedChars(scope), replaceUnsupportedChars(metricName));
            String metatagsKey = createMetatagsRecordKey(metatagsMap);
            MetatagsRecord metatags = new MetatagsRecord(metatagsMap, metatagsKey);
            argusMetric.setMetatagsRecord(metatags);
            extractAjnaTags(ajnaMetric.getTags(), argusMetric);

            Map<Long, Double> datapoints = new HashMap<>();
            datapoints.put(ajnaMetric.getTimestamp(), ajnaMetric.getMetricValue());
            argusMetric.setDatapoints(datapoints);

            return argusMetric;

        } catch(Exception e) {
            logger.warn("Failed to transform to Argus Metric. Dropping this Ajna Metric: " + ajnaMetric, e);
            dropped.accept(dpTags);
            return null;
        }
    }

    static String createMetatagsRecordKey(Map<String, String> metatagsMap) {
        StringBuilder keySb = new StringBuilder();

        // service
        if(metatagsMap.get(SchemaField.SERVICE) != null) {
            keySb.append(metatagsMap.get(SchemaField.SERVICE));
        } else {
            keySb.append(DELIMITER);
        }
        //subservice
        if(metatagsMap.get(SchemaField.SUB_SERVICE) != null) {
            keySb.append(metatagsMap.get(SchemaField.SUB_SERVICE));
        } else {
            keySb.append(DELIMITER);
        }
        //datacenter
        if(metatagsMap.get(SchemaField.TAG_KEY_DATACENTER) != null) {
            keySb.append(metatagsMap.get(SchemaField.TAG_KEY_DATACENTER));
        } else {
            keySb.append(DELIMITER);
        }
        //superpod
        if(metatagsMap.get(SchemaField.TAG_KEY_SUPERPOD) != null) {
            keySb.append(metatagsMap.get(SchemaField.TAG_KEY_SUPERPOD));
        } else {
            keySb.append(DELIMITER);
        }
        //pod
        if(metatagsMap.get(SchemaField.TAG_KEY_POD) != null) {
            keySb.append(metatagsMap.get(SchemaField.TAG_KEY_POD));
        } else {
            keySb.append(DELIMITER);
        }
        return keySb.toString();
    }


    boolean canIngest(com.salesforce.mandm.ajna.Metric ajnaMetric, int maxSleepTimeMS) {
        // global principal counter
        IPrincipal globalPrincipal = quotaInfoProvider.getGlobalPrincipal();
        quotaService.evaluate(globalPrincipal, System.currentTimeMillis() / 1000, 1);

        // Quota based on scope
        if (scopeQuotaEnabled) {
            //check if any of the quota policy is infracted by this datapoint
            IInfractionList infractionList = new InMemoryInfractionList();

            List<IPrincipal> principals = quotaInfoProvider.extractPrincipalsFromMetric(ajnaMetric);
            for (IPrincipal p : principals) {
                infractionList.mergeWith(quotaService.evaluate(p, ajnaMetric.getTimestamp(), 1));
            }
            if (infractionList.hasInfraction()) {
                quotaInfoProvider.logIfNeeded(infractionList);

                List<IInfraction> dropInfractions = infractionList.getInfractionByPolicyAction(PolicyTrigger.DROP);
                if (CollectionUtils.isNotEmpty(dropInfractions)) {
                    //do not ingest this due to policy says DROP for this infraction.
                    //Note that SLOWDOWN not supported here. SLOWDOWN should only be group Level Policies

                    return false;
                }
            }
        }

        // group principal counter
        IPrincipal groupLevelPrincipal = quotaInfoProvider.getGroupLevelPrincipal();
        IInfractionList throttledIL = quotaService.evaluate(groupLevelPrincipal, System.currentTimeMillis() / 1000, 1);
        quotaInfoProvider.logIfNeeded(throttledIL);

        //Throttle based on group.
        if(quotaGroupThrottlingEnabled) {
            while (true) {
                //if needs slowing down, sleep a while and evaluate again
                List<IInfraction> il = throttledIL.getInfractionByPolicyAction(PolicyTrigger.SLOWDOWN);
                if (CollectionUtils.isNotEmpty(il)) {
                    try {
                        Thread.sleep(random.nextInt(maxSleepTimeMS));
                        continue;
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted during throttled sleep");
                        Thread.currentThread().interrupt();
                    }
                }

                //no need to slowdown, get out of the loop directly
                break;
            }
        }
        return true;
    }

    /**
     * Verifies if all fields in the metric class are below a certain size
     *
     * @param argusMetric argus Metric
     * @return true if the metric field sizes are safe, otherwise false
     *
     **/

    static boolean isMetricSizeSafe(Metric argusMetric) {
        if(null == argusMetric) {
            return false;
        }

        if (!isTSDBEntitySafe(argusMetric)) {
            return false;
        }

        // Verify namespace size
        String namespace = argusMetric.getNamespace();
        if(null != namespace && namespace.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus metric namespace [" + namespace.substring(0,50) +
                    "] too large: " + namespace.length());
            return false;
        }
        // Verify displayname size
        String displayname = argusMetric.getDisplayName();
        if(null != displayname && displayname.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus metric displayname [" + displayname.substring(0,50) +
                    "] too large: " + displayname.length());
            return false;
        }
        // Verify units size
        String units = argusMetric.getUnits();
        if(null != units && units.length() > MAX_ARGUS_METRIC_FIELD_SIZE) {
            logger.trace("Argus metric units [" + units.substring(0,50) +
                    "] too large: " + units.length());
            return false;
        }
        // ToDO : once MetatagsRecord is available in ArgusCore built version, then
        // add size checks for it here.
        // argusMetric.getMetatagsRecord() ....

        return true;
    }

}
