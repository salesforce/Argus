package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.mandm.ajna.AjnaWire;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;
import static com.salesforce.perfeng.akc.AKCUtil.resolveCharSequence;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_BLOCKED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_DROPPED_TOOLARGE;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_POSTED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_PROCESS_LATENCY;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_TIMESTAMP_INVALID;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_TOO_OLD;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_BLOCKED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_DROPPED_TOOLARGE;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_POSTED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_PROCESS_LATENCY;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_TIMESTAMP_INVALID;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_TOO_OLD;

class SchemaConsumer extends BaseMetricConsumer {
    private static final Logger logger = LoggerFactory.getLogger(SchemaConsumer.class);
    private static final int SCHEMA_BATCH_SIZE = Integer.parseInt(AKCConfiguration.getParameter(AKCConfiguration.Parameter.SCHEMA_BATCH_SIZE));

    HistogramConsumer histogramConsumer;
    private SchemaService schemaService;

    SchemaConsumer(SchemaService schemaService, InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        super(instrumentationService, blacklistService);
        histogramConsumer = new HistogramConsumer(null, instrumentationService, blacklistService);
        this.schemaService = schemaService;
    }

    void processAjnaMetricKafkaRecords(ConsumerRecords<byte[], byte[]> records, Map<String, Metric> argusMetrics, List<Histogram> argusHistograms) {
        records.forEach(record -> {
            //ajnaWireFromBytes() is not affected by the template class, i.e. Metric in this case
            AjnaWire ajnaWire = metricAvroDecoder.ajnaWireFromBytes(record.value());
            Map<String, String> dpTags = new HashMap<>();
            dpTags.put("topic", record.topic());

            if (HISTOGRAM_SCHEMA_FINGERPRINT.equals(ajnaWire.getAvroSchemaFingerprint().toString())) {
                List<com.salesforce.mandm.ajna.Histogram> ajnaHistograms = histogramConsumer.extractAjnaHistograms(ajnaWire);
                if (ajnaHistograms == null) {
                    instrumentationService.updateCounter(HISTOGRAM_SCHEMA_DROPPED, 1, dpTags);
                    return;
                }
                instrumentationService.updateCounter(HISTOGRAM_SCHEMA_CONSUMED, ajnaHistograms.size(), dpTags);

                for (com.salesforce.mandm.ajna.Histogram ajnaHistogram : ajnaHistograms) {
                    try {
                        Histogram argusHistogram = histogramConsumer.transformToArgusHistogram(ajnaHistogram, dpTags,
                                this::updateHistogramSchemasTooOld, this::updateHistogramSchemasTimestampInvalid,
                                this::updateHistogramSchemasBlocked, this::updateHistogramSchemasDropped);
                        if (argusHistogram == null) {
                            continue;
                        }

                        if (HistogramConsumer.isHistogramSizeSafe(argusHistogram)) {
                            argusHistograms.add(argusHistogram);
                        } else {
                            dpTags.put(SchemaField.SERVICE,
                                    resolveCharSequence(ajnaHistogram.getService(), NONE, cs -> replaceUnsupportedChars(cs.subSequence(0, Math.min(cs.length(), 50)).toString().toLowerCase())));
                            instrumentationService.updateCounter(HISTOGRAM_SCHEMA_DROPPED_TOOLARGE, 1, dpTags);
                        }
                    } catch(Exception e) {
                        instrumentationService.updateCounter(HISTOGRAM_SCHEMA_DROPPED, 1, dpTags);
                        logger.warn("Exception while processing single ajnaHistogram.  Dropping this ajna histogram", e);
                    }
                }
            }
            else { //this is just a regular Metric
                List<com.salesforce.mandm.ajna.Metric> ajnaMetrics = extractAjnaMetrics(record, ajnaWire);
                if (ajnaMetrics == null) {
                    instrumentationService.updateCounter(SCHEMA_DROPPED, 1, dpTags);
                    return;
                }
                instrumentationService.updateCounter(SCHEMA_CONSUMED, ajnaMetrics.size(), dpTags);

                for (com.salesforce.mandm.ajna.Metric ajnaMetric : ajnaMetrics) {
                    try {
                        if (quotaSystemSwitch && !canIngest(ajnaMetric, DEFAULT_SLOWDOWN_SLEEP_MAX_MS)) {
                            continue;
                        }

                        Metric argusMetric = transformToArgusMetric(ajnaMetric, dpTags,
                                this::updateSchemasTooOld, this::updateSchemasTimestampInvalid,
                                this::updateSchemasBlocked, this::updateSchemasDropped);
                        if (argusMetric == null) {
                            continue;
                        }

                        if (isMetricSizeSafe(argusMetric)) {
                            String id = argusMetric.getIdentifier();
                            argusMetric.clearDatapoints();
                            argusMetrics.put(id, argusMetric);
                        } else {
                            dpTags.put(SchemaField.SERVICE,
                                    resolveCharSequence(ajnaMetric.getService(), NONE, cs -> replaceUnsupportedChars(cs.subSequence(0, Math.min(cs.length(), 50)).toString().toLowerCase())));
                            instrumentationService.updateCounter(SCHEMA_DROPPED_TOOLARGE, 1, dpTags);
                        }
                    } catch (Exception ex) {
                        instrumentationService.updateCounter(SCHEMA_DROPPED, 1, dpTags);
                        logger.warn("Exception while processing single ajnaMetric for schema. Dropping this ajna metric", ex);
                    }
                }
            }
        });

        if (!argusMetrics.isEmpty()) {
            int batches = processInBatches(new ArrayList<>(argusMetrics.values()),
                    SCHEMA_BATCH_SIZE,
                    this::processArgusMetricSchemas);
            instrumentationService.updateCounter(SCHEMA_BATCH_COUNT, batches, null);
        }

        if (!argusHistograms.isEmpty()) {
            int batches = processInBatches(argusHistograms,
                    SCHEMA_BATCH_SIZE,
                    this::processArgusHistogramSchemas);
            instrumentationService.updateCounter(HISTOGRAM_SCHEMA_BATCH_COUNT, batches, null);
        }
    }

    private void updateSchemasDropped(Map<String, String> tags) {
        instrumentationService.updateCounter(SCHEMA_DROPPED, 1, tags);
    }

    private void updateSchemasBlocked(Map<String, String> tags) {
        instrumentationService.updateCounter(SCHEMA_BLOCKED, 1, tags);
    }

    private void updateSchemasTooOld(Map<String, String> tags) {
        instrumentationService.updateCounter(SCHEMA_TOO_OLD, 1, tags);
    }

    private void updateSchemasTimestampInvalid(Map<String, String> tags) {
        instrumentationService.updateCounter(SCHEMA_TIMESTAMP_INVALID, 1, tags);
    }


    private void updateHistogramSchemasDropped(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_SCHEMA_DROPPED, 1, tags);
    }

    private void updateHistogramSchemasBlocked(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_SCHEMA_BLOCKED, 1, tags);
    }

    private void updateHistogramSchemasTooOld(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_SCHEMA_TOO_OLD, 1, tags);
    }

    private void updateHistogramSchemasTimestampInvalid(Map<String, String> tags) {
        instrumentationService.updateCounter(HISTOGRAM_SCHEMA_TIMESTAMP_INVALID, 1, tags);
    }

    /**
     *
     * @param metrics list of Metric objects
     * @param posted name of metric for posted count
     * @param dropped name of metric for dropped count
     * @param processLatency name of metric for process latency
     */
    private void sendToSchemaService(List<Metric> metrics, String posted, String dropped, String processLatency) {
        int size = metrics.size();
        double start = System.currentTimeMillis();
        try {
            schemaService.put(metrics);
            instrumentationService.updateCounter(posted, size, null);
        } catch (Exception ex) {
            logger.warn("Retrying schema commit due to exception " + ex);
            if (retryWithExponentialBackoff(metrics, schemaService::put)) {
                instrumentationService.updateCounter(posted, size, null);
            } else {
                instrumentationService.updateCounter(dropped, size, null);
            }
        } finally {
            instrumentationService.updateTimer(processLatency,
                    ((double)System.currentTimeMillis()-start),
                    null);
        }

    }

    void processArgusMetricSchemas(List<Metric> metrics) {
        sendToSchemaService(metrics, SCHEMA_POSTED, SCHEMA_DROPPED, SCHEMA_PROCESS_LATENCY);
    }

    /**
     * It's a conscious decision to convert Histogram into an empty (no datapoints) Metric object for schema processing
     * because 1) there is no difference in schema between Metric and Histogram 2) SchemaService is tailored to only accept
     * Metric objects for now.
     * @param histograms list of histograms
     */
    private void processArgusHistogramSchemas(List<Histogram> histograms) {

        List<Metric> histogramSchemas = histograms.stream()
                .map(h -> {Metric m = new Metric(h.getScope(), h.getMetric());m.setTags(h.getTags());return m;})
                .collect(Collectors.toList());

        sendToSchemaService(histogramSchemas, HISTOGRAM_SCHEMA_POSTED, HISTOGRAM_SCHEMA_DROPPED, HISTOGRAM_SCHEMA_PROCESS_LATENCY);
    }
}
