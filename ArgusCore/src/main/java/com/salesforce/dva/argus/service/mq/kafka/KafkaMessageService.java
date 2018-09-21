/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.service.mq.kafka;

import com.fasterxml.jackson.databind.JavaType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static com.salesforce.dva.argus.system.SystemAssert.requireState;

/**
 * The Kafka specific implementation of the message queue interface.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class KafkaMessageService extends DefaultService implements MQService {

    //~ Instance fields ******************************************************************************************************************************

    private Producer _producer = null;
    private Consumer _consumer = null;
    private final SystemConfiguration _config;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new KafkaMessageService object.
     *
     * @param  config  The system _configuration used to configure the service.
     */
    @Inject
    public KafkaMessageService(SystemConfiguration config) {
    	super(config);
        requireArgument(config != null, "System configuration cannot be null.");
        if (!Boolean.parseBoolean(config.getValue(Property.KAFKA_DISABLE_PRODUCER.getName(), Property.KAFKA_DISABLE_PRODUCER.getDefaultValue()))) {
            _producer = new Producer(config);
        }
        if (!Boolean.parseBoolean(config.getValue(Property.KAFKA_DISABLE_CONSUMER.getName(), Property.KAFKA_DISABLE_CONSUMER.getDefaultValue()))) {
            _consumer = new Consumer(config);
        }
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    private void requireProducerEnabled() {
        requireState(_producer != null, "Cannot perform this action when Producer is disabled");
    }

    private void requireConsumerEnabled() {
        requireState(_consumer != null, "Cannot perform this action when Consumer is disabled");
    }

    /*
     * Transform an MQService MQQueue name to the configured topic name if redefined in .properties file
     */
    private String toKafkaTopic(String topic) {
        if (topic.equals(MQQueue.ALERT.getQueueName())) {
            return _config.getValue(Property.KAFKA_ALERTS_TOPIC.getName(), Property.KAFKA_ALERTS_TOPIC.getDefaultValue());
        } else if (topic.equals(MQQueue.ANNOTATION.getQueueName())) {
            return _config.getValue(Property.KAFKA_ANNOTATIONS_TOPIC.getName(), Property.KAFKA_ANNOTATIONS_TOPIC.getDefaultValue());
        } else if (topic.equals(MQQueue.METRIC.getQueueName())) {
            return _config.getValue(Property.KAFKA_METRICS_TOPIC.getName(), Property.KAFKA_METRICS_TOPIC.getDefaultValue());
        } else {
            return topic;
        }
    }

    @Override
    public <T extends Serializable> void enqueue(String topic, T object) {
        requireNotDisposed();
        requireProducerEnabled();
        requireArgument(object != null, "The object to enqueue cannot be null.");

        List<T> messages = new ArrayList<>(1);

        messages.add(object);
        enqueue(topic, messages);
    }

    @Override
    public <T extends Serializable> void enqueue(final String topic, List<T> objects) {
        requireNotDisposed();
        requireProducerEnabled();
        requireArgument(topic != null && !topic.trim().isEmpty(), "Topic name cannot be null or empty.");
        requireArgument(objects != null, "The list of objects to enqueue cannot be null.");
        _producer.enqueue(toKafkaTopic(topic), objects);
    }

    @Override
    public String dequeue(String topic, int timeout) {
        return dequeue(topic, String.class, timeout);
    }

    @Override
    public List<String> dequeue(String topic, int timeout, int limit) {
        return dequeue(topic, String.class, timeout, limit);
    }

    @Override
    public <T extends Serializable> T dequeue(String topic, Class<T> type, int timeout) {
        List<T> objects = dequeue(topic, type, timeout, 1);

        return objects.isEmpty() ? null : objects.get(0);
    }

    @Override
    public <T extends Serializable> T dequeue(String topic, JavaType type, int timeout) {
        List<T> objects = dequeue(topic, type, timeout, 1);

        return objects.isEmpty() ? null : objects.get(0);
    }

    @Override
    public <T extends Serializable> List<T> dequeue(String topic, Class<T> type, int timeout, int limit) {
        requireNotDisposed();
        requireConsumerEnabled();
        requireArgument(topic != null && !topic.trim().isEmpty(), "Topic cannot be null or empty.");
        requireArgument(type != null, "Result object runtime type cannot be null.");
        requireArgument(timeout > 0, "Timeout in milliseconds must be greater than zero.");
        requireArgument(limit > 0, "Limit must be non-negative.");
        topic = toKafkaTopic(topic);
        _consumer.initializeTopic(topic);
        return _consumer.dequeueFromBuffer(topic, type, timeout, limit);
    }

    @Override
    public <T extends Serializable> List<T> dequeue(String topic, JavaType type, int timeout, int limit) {
        requireNotDisposed();
        requireConsumerEnabled();
        requireArgument(topic != null && !topic.trim().isEmpty(), "Topic cannot be null or empty.");
        requireArgument(type != null, "Result object runtime type cannot be null.");
        requireArgument(timeout > 0, "Timeout in milliseconds must be greater than zero.");
        requireArgument(limit > 0, "Limit must be non-negative.");
        topic = toKafkaTopic(topic);
        _consumer.initializeTopic(topic);
        return _consumer.dequeueFromBuffer(topic, type, timeout, limit);
    }

    @Override
    public void dispose() {
        super.dispose();
        if (_producer != null) {
            _producer.shutdown();
        }
        if (_consumer != null) {
            _consumer.shutdown();
        }
    }
    
    @Override
    public Properties getServiceProperties() {
            Properties serviceProps= new Properties();

            for(Property property:Property.values()){
                    serviceProps.put(property.getName(), property.getDefaultValue());
            }
            return serviceProps;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The implementation specific configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** Indicates the Kafka brokers. */
        KAFKA_BROKERS("service.property.mq.kafka.brokers", "localhost:9093"),
        /** Specifies the number of producer connections. Default is 10. */
        KAFKA_PRODUCER_CONNECTIONS("service.property.mq.kafka.producer.connections", "10"),
        /** Indicates whether the producer should block when the buffer is full. Default is false. */
        KAFKA_PRODUCER_BLOCK_ON_BUFFER_FULL("service.property.mq.kafka.producer.block.on.buffer.full", "false"),
        /** Specifies the allocated producer buffer memory in bytes. Default is 134217728. */
        KAFKA_PRODUCER_BUFFER_MEMORY("service.property.mq.kafka.producer.buffer.memory", "134217728"),
        /** Specifies the batch size.  Default is 102400. */
        KAFKA_PRODUCER_BATCH_SIZE("service.property.mq.kafka.producer.batch.size", "102400"),
        /** Specifies the number of streams per topic.  Default is 1. */
        KAFKA_CONSUMER_STREAMS_PER_TOPIC("service.property.mq.kafka.consumer.streams.per.topic", "1"),
        /** Specifies the number of consumer messages to buffer.  Default is 1. */
        KAFKA_CONSUMER_MESSAGES_TO_BUFFER("service.property.mq.kafka.consumer.messages.to.buffer", "1"),
        /** Specifies the default consumer group ID. */
        KAFKA_CONSUMER_GROUPID("service.property.mq.kafka.consumer.groupid", "argus-consumer-unit"),
        /** Specifies the default consumer group ID. */
        KAFKA_CONSUMER_OFFSET_RESET("service.property.mq.kafka.consumer.auto.offset.reset", "earliest"),
        KAFKA_ALERTS_TOPIC("service.property.mq.kafka.alerts.topic", "argusAlertQueue"),
        KAFKA_ANNOTATIONS_TOPIC("service.property.mq.kafka.annotations.topic", "argusAnnotationQueue"),
        KAFKA_METRICS_TOPIC("service.property.mq.kafka.metrics.topic", "argusMetricQueue"),
        KAFKA_SECURITY_PROTOCOL("service.property.mq.kafka.security.protocol", "PLAINTEXT"),
        KAFKA_SSL_TRUSTSTORE_LOCATION("service.property.mq.kafka.ssl.truststore.location", ""),
        KAFKA_SSL_TRUSTSTORE_PASSWORD("service.property.mq.kafka.ssl.truststore.password", ""),
        KAFKA_SSL_KEYSTORE_LOCATION("service.property.mq.kafka.ssl.keystore.location", ""),
        KAFKA_SSL_KEYSTORE_PASSWORD("service.property.mq.kafka.ssl.keystore.password", ""),
        KAFKA_SSL_KEY_PASSWORD("service.property.mq.kafka.ssl.key.password", ""),
        /** Specifies the Kafka ZooKeeper connection endpoint. */
        ZOOKEEPER_CONNECT("service.property.mq.zookeeper.connect", "localhost:2185"),
        /** Specifies the Kafka Zookeeper connection timeout in milliseconds.  Default is 10000. */
        ZOOKEEPER_CONNECTION_TIMEOUT_MS("service.property.mq.zookeeper.connection.timeout.ms", "10000"),
        /** Whether to disable KafkaProducer instances from being created */
        KAFKA_DISABLE_PRODUCER("service.property.mq.kafka.producer.disable", "false"),
        /** Whether to disable KafkaConsumer instances from being created */
        KAFKA_DISABLE_CONSUMER("service.property.mq.kafka.consumer.disable", "false");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default property value.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
