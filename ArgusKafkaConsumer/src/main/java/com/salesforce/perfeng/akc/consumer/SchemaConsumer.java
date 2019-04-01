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

import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;
import static com.salesforce.perfeng.akc.AKCUtil.resolveCharSequence;
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

    private SchemaService schemaService;

    SchemaConsumer(SchemaService schemaService, InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        super(instrumentationService, blacklistService);
        this.schemaService = schemaService;
    }

    void processAjnaMetricKafkaRecords(ConsumerRecords<byte[], byte[]> records, Map<String, Metric> argusMetrics, List<Histogram> unused) {
        records.forEach(record -> {
            //ajnaWireFromBytes() is not affected by the template class, i.e. Metric in this case
            AjnaWire ajnaWire = metricAvroDecoder.ajnaWireFromBytes(record.value());
            Map<String, String> dpTags = new HashMap<>();
            dpTags.put("topic", record.topic());

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
        });

        if (!argusMetrics.isEmpty()) {
            int batches = processInBatches(new ArrayList<>(argusMetrics.values()),
                    SCHEMA_BATCH_SIZE,
                    this::processArgusMetricSchemas);
            instrumentationService.updateCounter(SCHEMA_BATCH_COUNT, batches, null);
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

    void processArgusMetricSchemas(List<Metric> metrics) {
        int size = metrics.size();
        double start = System.currentTimeMillis();
        try {
            schemaService.put(metrics);
            instrumentationService.updateCounter(SCHEMA_POSTED, size, null);
        } catch (Exception ex) {
            logger.warn("Retrying schema commit due to exception " + ex);
            if (retryWithExponentialBackoff(metrics, schemaService::put)) {
                instrumentationService.updateCounter(SCHEMA_POSTED, size, null);
            } else {
                instrumentationService.updateCounter(SCHEMA_DROPPED, size, null);
            }
        } finally {
            instrumentationService.updateTimer(SCHEMA_PROCESS_LATENCY,
                    ((double)System.currentTimeMillis()-start),
                    null);
        }
    }


}
