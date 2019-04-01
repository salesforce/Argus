package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.avro.AvroSchemaFactory;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.perfeng.akc.exceptions.AKCException;
import com.salesforce.quota.IBlacklistService;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AjnaConsumerTask implements AmurSinkTask<byte[], byte[]> {
    private static final Logger logger = LoggerFactory.getLogger(AjnaConsumerTask.class);

    private ConsumerType consumerType;
    AnnotationConsumer annotationConsumer;
    SchemaConsumer schemaConsumer;
    MetricConsumer metricConsumer;

    @Override
    public void init(TSDBService tsdbService,
                     SchemaService schemaService,
                     InstrumentationService instrumentationService,
                     IBlacklistService blacklistService) {
        consumerType = ConsumerType.valueOf(AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE));

        // Lookup Funnel VIP for respective DC
        AvroSchemaFactory.getInstance().setSchemaLookupVip(AKCConfiguration.getParameter(AKCConfiguration.Parameter.SCHEMA_LOOKUP_SERVER),
                Integer.parseInt(AKCConfiguration.getParameter(AKCConfiguration.Parameter.SCHEMA_LOOKUP_TIMEOUT_MS)));

        this.annotationConsumer = new AnnotationConsumer(tsdbService, instrumentationService, blacklistService);
        this.schemaConsumer = new SchemaConsumer(schemaService, instrumentationService, blacklistService);
        this.metricConsumer = new MetricConsumer(tsdbService, instrumentationService, blacklistService);
    }

    @Override
    public void shutdownTask() {
        logger.info("AjnaConsumerTask received shutdown call");
    }


    @Override
    public void handleBatch(ConsumerRecords<byte[], byte[]> records) {
        if (ConsumerType.METRICS.equals(consumerType) ){
            metricConsumer.processAjnaMetricKafkaRecords(records, Maps.newHashMap(), Lists.newArrayList());
        } else if (ConsumerType.SCHEMA.equals(consumerType)) {
            schemaConsumer.processAjnaMetricKafkaRecords(records, Maps.newHashMap(), null);
        } else if (ConsumerType.ANNOTATIONS.equals(consumerType)) {
            annotationConsumer.processAjnaAnnotationKafkaRecords(records, Lists.newArrayList());
        } else {
            throw new AKCException("Invalid consumer type: " + consumerType);
        }
    }
}
