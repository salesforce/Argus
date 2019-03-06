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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.salesforce.dva.argus.service.mq.kafka.KafkaMessageService.Property;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.BufferExhaustedException;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Produces events onto the Kafka broker.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultProducer implements Producer {

    //~ Instance fields ******************************************************************************************************************************

    private final Logger _logger = LoggerFactory.getLogger(getClass());
    protected SystemConfiguration _configuration;
    protected KafkaProducer<String, String> _producer;
    protected ExecutorService _executorService;
    private final ObjectMapper _mapper;

    //~ Constructors *********************************************************************************************************************************

    protected DefaultProducer() {
        _mapper = new ObjectMapper();
    }

    /**
     * Creates a new DefaultProducer object.
     *
     * @param  config  The system configuration.
     */
    public DefaultProducer(SystemConfiguration config) {
        this();
        _configuration = config;
        _producer = createProducer();
        _executorService = createExecutorService();
    }

    @VisibleForTesting
    protected DefaultProducer(SystemConfiguration config, KafkaProducer<String, String> producer, ExecutorService executorService, ObjectMapper mapper) {
        _configuration = config;
        _producer = producer;
        _executorService = executorService;
        _mapper = mapper;
    }

    //~ Methods **************************************************************************************************************************************

    protected ExecutorService createExecutorService() {
        int producerConnections = Math.max(Integer.valueOf(
            _configuration.getValue(Property.KAFKA_PRODUCER_CONNECTIONS.getName(), Property.KAFKA_PRODUCER_CONNECTIONS.getDefaultValue())), 10);

        return Executors.newFixedThreadPool(producerConnections, new ThreadFactory() {

                AtomicInteger id = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, MessageFormat.format("kafka-producer-worker-{0}", id.getAndIncrement()));
                }
            });
    }

    protected KafkaProducer<String, String> createProducer() {
        Map<String, Object> producerConfig = new HashMap<String, Object>();

        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
            _configuration.getValue(Property.KAFKA_BROKERS.getName(), Property.KAFKA_BROKERS.getDefaultValue()));
        producerConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        producerConfig.put(ProducerConfig.CLIENT_ID_CONFIG, "argus.producer");
        producerConfig.put(ProducerConfig.BUFFER_MEMORY_CONFIG,
            Long.parseLong(
                _configuration.getValue(Property.KAFKA_PRODUCER_BUFFER_MEMORY.getName(), Property.KAFKA_PRODUCER_BUFFER_MEMORY.getDefaultValue())));
        producerConfig.put(ProducerConfig.BATCH_SIZE_CONFIG,
            Integer.parseInt(
                _configuration.getValue(Property.KAFKA_PRODUCER_BATCH_SIZE.getName(), Property.KAFKA_PRODUCER_BATCH_SIZE.getDefaultValue())));
        // Set security props
        producerConfig.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                _configuration.getValue(Property.KAFKA_SECURITY_PROTOCOL.getName(), Property.KAFKA_SECURITY_PROTOCOL.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_PROVIDER_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_PROVIDER.getName(), Property.KAFKA_SSL_PROVIDER.getDefaultValue()));

        producerConfig.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_TYPE.getName(), Property.KAFKA_SSL_KEYSTORE_TYPE.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_LOCATION.getName(), Property.KAFKA_SSL_KEYSTORE_LOCATION.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_PASSWORD.getName(), Property.KAFKA_SSL_KEYSTORE_PASSWORD.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEY_PASSWORD.getName(), Property.KAFKA_SSL_KEY_PASSWORD.getDefaultValue()));

        producerConfig.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_TYPE.getName(), Property.KAFKA_SSL_TRUSTSTORE_TYPE.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_LOCATION.getName(), Property.KAFKA_SSL_TRUSTSTORE_LOCATION.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_PASSWORD.getName(), Property.KAFKA_SSL_TRUSTSTORE_PASSWORD.getDefaultValue()));
        return new KafkaProducer<String, String>(producerConfig, new StringSerializer(), new StringSerializer());
    }

    /**
     * Adds the messages to the DefaultProducer Buffer which will later be batched by Kafka and sent to the brokers.
     *
     * @param   <T>      The value type.
     * @param   topic    The topic to produce onto.
     * @param   objects  The list of objects to enqueue.
     *
     * @return  The number of objects that were successfully added to the DefaultProducer Buffer.
     */
    public <T extends Serializable> int enqueue(final String topic, List<T> objects) {
        int messagesBuffered = 0;

        for (T object : objects) {
            final String value;

            if (String.class.isAssignableFrom(object.getClass())) {
                value = String.class.cast(object);
            } else {
                try {
                    value = serialize(object);
                } catch (JsonProcessingException e) {
                    _logger.warn("Exception while serializing the object to a string. Skipping this object.", e);
                    continue;
                }
            }
            try {
                boolean addedToBuffer = _executorService.submit(new ProducerWorker(topic, value)).get();

                if (addedToBuffer) {
                    messagesBuffered++;
                }
            } catch (InterruptedException e) {
                _logger.warn("Enqueue operation was interrupted by calling code.");
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                throw new SystemException(e);
            }
        }
        return messagesBuffered;
    }

    /** Shuts down the producer. */
    public void shutdown() {
        if (_producer != null) {
            _producer.close();
        }
        _executorService.shutdown();
        try {
            if (!_executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                _logger.warn("Shutdown of Kafka executor service timed out after 10 seconds.");
                _executorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            _logger.warn("Shutdown of executor service was interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    @VisibleForTesting
    protected <T extends Serializable> String serialize(T obj) throws JsonProcessingException {
        return _mapper.writeValueAsString(obj);
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * The worker used by the producer executor service.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    private class ProducerWorker implements Callable<Boolean> {

        private final String _topic;
        private final String _message;

        /**
         * Creates a new DefaultProducer object.
         *
         * @param  topic    The topic to produce onto.
         * @param  message  The message to enqueue.
         */
        public ProducerWorker(String topic, String message) {
            this._topic = topic;
            this._message = message;
        }

        @Override
        public Boolean call() {
            ProducerRecord<String, String> record = new ProducerRecord<>(_topic, _message);

            try {
                _producer.send(record, new Callback() {
                        @Override
                        public void onCompletion(RecordMetadata metaData, Exception exception) {
                            if (exception != null) {
                                _logger.warn("Exception while sending message: {}", exception);
                            } else {
                                _logger.trace("Message sent to partition {} with offset {}.", metaData.partition(), metaData.offset());
                            }
                        }
                    });
            } catch (BufferExhaustedException e) {
                _logger.warn("Buffer exhausted on kafka producer. Skipping this message.", e);
                return false;
            } catch (Exception e) {
                _logger.warn("Exception occurred when executing producer send(). ", e);
                throw new SystemException(e);
            }
            return true;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
