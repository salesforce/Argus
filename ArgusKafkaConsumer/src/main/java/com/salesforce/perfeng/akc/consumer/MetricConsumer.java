package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.ajna.AjnaWire;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;
import static com.salesforce.perfeng.akc.AKCUtil.resolveCharSequence;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_BLOCKED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_CONSUMED_LATEST;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_DROPPED_TOOLARGE;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_POSTED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_TIMESTAMP_INVALID;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.DATAPOINTS_TOO_OLD;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.METRIC_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.METRIC_DATAPOINTS_DEDUPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.METRIC_PROCESS_LATENCY;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.QUOTA_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.QUOTA_SERVICE_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.QUOTA_SUBSERVICE_CONSUMED;

class MetricConsumer extends BaseMetricConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MetricConsumer.class);
    private static final int METRICS_BATCH_SIZE = Integer.parseInt(AKCConfiguration.getParameter(AKCConfiguration.Parameter.METRICS_BATCH_SIZE));

    HistogramConsumer histogramConsumer;
    private TSDBService tsdbService;

    MetricConsumer(TSDBService tsdbService, InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        super(instrumentationService, blacklistService);

        histogramConsumer = new HistogramConsumer(tsdbService, instrumentationService, blacklistService);
        this.tsdbService = tsdbService;
    }

    void processAjnaMetricKafkaRecords(ConsumerRecords<byte[], byte[]> records, Map<String, Metric> argusMetrics, List<Histogram> argusHistograms) {
        records.forEach(record -> {
            //ajnaWireFromBytes() is not affected by the template class, i.e. Metric in this case
            AjnaWire ajnaWire = metricAvroDecoder.ajnaWireFromBytes(record.value());
            Map<String, String> dpTags = new HashMap<>();
            dpTags.put("topic", record.topic());


            if (HISTOGRAM_SCHEMA_FINGERPRINT.equals(ajnaWire.getAvroSchemaFingerprint().toString())) {
                histogramConsumer.processAjnaWireRecord(argusHistograms, ajnaWire, dpTags);
            }
            else {
                processAjnaWireRecord(record, argusMetrics, ajnaWire, dpTags);
            }
        });

        if (!argusMetrics.isEmpty()) {
            int batches = processInBatches(new ArrayList<>(argusMetrics.values()),
                    METRICS_BATCH_SIZE,
                    this::processArgusMetrics);
            instrumentationService.updateCounter(METRIC_BATCH_COUNT, batches, null);
        }

        if (!argusHistograms.isEmpty()) {
            int batches = processInBatches(argusHistograms,
                    METRICS_BATCH_SIZE,
                    histogramConsumer::processArgusHistogram);
            instrumentationService.updateCounter(HISTOGRAM_BATCH_COUNT, batches, null);
        }
    }

    void processAjnaWireRecord(ConsumerRecord<byte[], byte[]> record, Map<String, Metric> argusMetrics, AjnaWire ajnaWire, Map<String, String> dpTags) {
        List<com.salesforce.mandm.ajna.Metric> ajnaMetrics = extractAjnaMetrics(record, ajnaWire);
        if (ajnaMetrics == null) {
            instrumentationService.updateCounter(DATAPOINTS_DROPPED, 1, dpTags);
            return;
        }
        instrumentationService.updateCounter(DATAPOINTS_CONSUMED, ajnaMetrics.size(), dpTags);

        for (com.salesforce.mandm.ajna.Metric ajnaMetric : ajnaMetrics) {
            try {
                if (quotaSystemSwitch && !canIngest(ajnaMetric, DEFAULT_SLOWDOWN_SLEEP_MAX_MS)) {
                    continue;
                }

                sendQuotaCounters(ajnaMetric);

                Metric argusMetric = transformToArgusMetric(ajnaMetric, dpTags,
                        this::updateDataPointsTooOld, this::updateDataPointsTimestampInvalid,
                        this::updateDataPointsBlocked, this::updateDataPointsDropped);
                if (argusMetric == null) {
                    continue;
                }

                if (isMetricSizeSafe(argusMetric)) {
                    String id = argusMetric.getIdentifier();
                    if (argusMetrics.containsKey(id)) {
                        Metric existingMetric = argusMetrics.get(id);
                        Map<Long, Double> datapoints = argusMetric.getDatapoints();
                        double deduped = existingMetric.addIfNotExistsDatapoints(datapoints);
                        instrumentationService.updateCounter(METRIC_DATAPOINTS_DEDUPED, deduped, null);
                    } else {
                        argusMetrics.put(id, argusMetric);
                    }
                } else {
                    dpTags.put(SchemaField.SERVICE,
                            resolveCharSequence(ajnaMetric.getService(), NONE, cs -> replaceUnsupportedChars(cs.subSequence(0, Math.min(cs.length(), 50)).toString().toLowerCase())));
                    instrumentationService.updateCounter(DATAPOINTS_DROPPED_TOOLARGE, 1, dpTags);
                }
            } catch (Exception ex) {
                instrumentationService.updateCounter(DATAPOINTS_DROPPED, 1, dpTags);
                logger.warn("Exception while processing single ajnaMetric. Dropping this ajna metric", ex);
            }
        }
    }

    void updateDataPointsDropped(Map<String, String> tags) {
        instrumentationService.updateCounter(DATAPOINTS_DROPPED, 1, tags);
    }

    void updateDataPointsBlocked(Map<String, String> tags) {
        instrumentationService.updateCounter(DATAPOINTS_BLOCKED, 1, tags);
    }

    void updateDataPointsTooOld(Map<String, String> tags) {
        instrumentationService.updateCounter(DATAPOINTS_TOO_OLD, 1, tags);
    }

    void updateDataPointsTimestampInvalid(Map<String, String> tags) {
        instrumentationService.updateCounter(DATAPOINTS_TIMESTAMP_INVALID, 1, tags);
    }

    void processArgusMetrics(List<Metric> metrics) {
        int size = metrics.size();
        double start = System.currentTimeMillis();
        try {
            tsdbService.putMetrics(metrics);
            instrumentationService.updateCounter(DATAPOINTS_POSTED, size, null);
        } catch (Exception ex) {
            logger.warn("Retrying metric commit due to exception " + ex);
            if (retryWithExponentialBackoff(metrics, tsdbService::putMetrics)) {
                instrumentationService.updateCounter(DATAPOINTS_POSTED, size, null);
            } else {
                instrumentationService.updateCounter(DATAPOINTS_DROPPED, size, null);
            }
        } finally {
            instrumentationService.updateTimer(METRIC_PROCESS_LATENCY,
                    ((double)System.currentTimeMillis()-start),
                    null);
        }
    }

    void sendQuotaCounters(com.salesforce.mandm.ajna.Metric ajnaMetric) {
        Map<String, String> latestTags = new HashMap<>();
        String dc = resolveCharSequence(ajnaMetric.getTags()==null?null:ajnaMetric.getTags().get(SchemaField.TAG_KEY_DATACENTER), NONE, cs -> cs.toString().toUpperCase());
        latestTags.put(SchemaField.DC, dc);
        instrumentationService.updateCounter(DATAPOINTS_CONSUMED_LATEST, 1, latestTags, true);

        String service = resolveCharSequence(ajnaMetric.getService(), NONE, cs -> replaceUnsupportedChars(cs.toString().toLowerCase()));
        String subservice = resolveCharSequence(ajnaMetric.getSubservice(), NONE, cs -> replaceUnsupportedChars(cs.toString().toLowerCase()));

        Map<String, String> serviceTags = new HashMap<>();
        serviceTags.put(SchemaField.SERVICE, service);
        instrumentationService.updateCounter(QUOTA_SERVICE_CONSUMED, 1, serviceTags, true);

        Map<String, String> subserviceTags = new HashMap<>();
        subserviceTags.put(SchemaField.SUB_SERVICE, subservice);
        subserviceTags.put(SchemaField.SERVICE, service);
        instrumentationService.updateCounter(QUOTA_SUBSERVICE_CONSUMED, 1, subserviceTags, true);

        Map<String, String> quotaTags = new HashMap<>();
        quotaTags.put(SchemaField.SERVICE, service);
        quotaTags.put(SchemaField.SUB_SERVICE, subservice);
        quotaTags.put(SchemaField.DC, dc);
        instrumentationService.updateCounter(QUOTA_CONSUMED, 1, quotaTags, true);
    }

}
