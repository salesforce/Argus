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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.service.mq.kafka.KafkaMessageService.Property;
import com.salesforce.dva.argus.system.SystemConfiguration;
import kafka.Kafka;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.consumer.Whitelist;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Kafka client consumer.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Consumer {

    //~ Instance fields ******************************************************************************************************************************

    private final int MAX_BUFFER_SIZE;
    private final Logger _logger = LoggerFactory.getLogger(getClass());
    private final SystemConfiguration _configuration;
    private final Map<String, Topic> _topics = new HashMap<>();
    private final AtomicLong count = new AtomicLong(0);
    private final ObjectMapper _mapper;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Consumer object.
     *
     * @param  configuration  The system configuration.  Cannot be null.
     */
    public Consumer(SystemConfiguration configuration) {
        this._configuration = configuration;
        this._mapper = new ObjectMapper();
        MAX_BUFFER_SIZE = Integer.parseInt(_configuration.getValue(Property.KAFKA_CONSUMER_MESSAGES_TO_BUFFER.getName(),
                Property.KAFKA_CONSUMER_MESSAGES_TO_BUFFER.getDefaultValue()));
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * This method creates Kafka streams for a topic so that messages can be streamed to the local buffer. If the streams for the given topic have
     * already been initialized the returns. Information about a particular topic is stored in a HashMap. This method uses double-checked locking to
     * make sure only one client thread can initialize streams for a topic. Moreover, it also helps subsequent calls, to check if the topic has been
     * initialized, be not synchronized and hence return faster.
     *
     * @param  topic  The topic to initialize.
     */
    public void initializeTopic(String topic) {
        if (_topics.get(topic) == null) {
            synchronized (this) {
                if (_topics.get(topic) == null) {
                    _logger.info("Initializing streams for topic: {}", topic);

                    Properties props = new Properties();
                    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                            _configuration.getValue(Property.KAFKA_BROKERS.getName(), Property.KAFKA_BROKERS.getDefaultValue()));
                    props.setProperty("zookeeper.connect",
                        _configuration.getValue(Property.ZOOKEEPER_CONNECT.getName(), Property.ZOOKEEPER_CONNECT.getDefaultValue()));
                    props.setProperty("group.id",
                        _configuration.getValue(Property.KAFKA_CONSUMER_GROUPID.getName(), Property.KAFKA_CONSUMER_GROUPID.getDefaultValue()));
                    props.setProperty("auto.offset.reset", _configuration.getValue(Property.KAFKA_CONSUMER_OFFSET_RESET.getName(), Property.KAFKA_CONSUMER_OFFSET_RESET.getDefaultValue()));
                    props.setProperty("auto.commit.interval.ms", "5000");
                    props.setProperty("fetch.message.max.bytes", "2000000");
                    props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                    props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
                    props.setProperty("security.protocol",
                            _configuration.getValue(Property.KAFKA_SECURITY_PROTOCOL.getName(), Property.KAFKA_SECURITY_PROTOCOL.getDefaultValue()));
                    props.setProperty(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                            _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_LOCATION.getName(), Property.KAFKA_SSL_TRUSTSTORE_LOCATION.getDefaultValue()));
                    props.setProperty(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                            _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_PASSWORD.getName(), Property.KAFKA_SSL_TRUSTSTORE_PASSWORD.getDefaultValue()));
                    props.setProperty(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                            _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_LOCATION.getName(), Property.KAFKA_SSL_KEYSTORE_LOCATION.getDefaultValue()));
                    props.setProperty(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
                            _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_PASSWORD.getName(), Property.KAFKA_SSL_KEYSTORE_PASSWORD.getDefaultValue()));
                    props.setProperty(SslConfigs.SSL_KEY_PASSWORD_CONFIG,
                            _configuration.getValue(Property.KAFKA_SSL_KEY_PASSWORD.getName(), Property.KAFKA_SSL_KEY_PASSWORD.getDefaultValue()));

                    int numStreams = Math.max(Integer.parseInt(_configuration.getValue(Property.KAFKA_CONSUMER_STREAMS_PER_TOPIC.getName(),
                            Property.KAFKA_CONSUMER_STREAMS_PER_TOPIC.getDefaultValue())),
                            2);
                    Topic t = new Topic(topic, props, numStreams);
                    _topics.put(topic, t);
                }
            }
        }
    }

    /**
     * Dequeues messages from the local buffer as specified by the limit. If no messages are available to dequeue, then waits for at most timeout
     * milliseconds before returning.
     *
     * @param   <T>      The result type.
     * @param   topic    The topic to dequeue messages from.
     * @param   type     The type that each message should be converted to.
     * @param   timeout  The max amount of time in milliseconds that the function can take to dequeue limit number of messages. If number of dequeued
     *                   messages is less than limit, then only those messages are returned.
     * @param   limit    The max number of messages to dequeue.
     *
     * @return  Messages of the given type belonging to the given topic. Empty list if no such topic exists or the method times out.
     */
    public <T extends Serializable> List<T> dequeueFromBuffer(String topic, Class<T> type, int timeout, int limit) {
        List<T> result = new ArrayList<T>();
        long cutoff = System.currentTimeMillis() + timeout;
        BlockingQueue<String> queue = _topics.get(topic).getMessages();

        while (System.currentTimeMillis() < cutoff && (limit < 0 || result.size() < limit)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                String message = queue.poll(timeout, TimeUnit.MILLISECONDS);

                if (message != null && !message.isEmpty()) {
                    if (String.class.isAssignableFrom(type)) {
                        result.add(type.cast(message));
                    } else {
                        result.add(_mapper.readValue(message, type));
                    }
                    if (result.size() % 1000 == 0) {
                        _logger.debug("Dequeued {} messages from local buffer.", result.size());
                    }
                }
            } catch (InterruptedException e) {
                _logger.warn("Interrupted while waiting for poll() to return a message.");
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                _logger.warn("Exception while deserializing message to type: " + type + ". Skipping this message.", e);
            }
        }
        return result;
    }

    /**
     * Dequeues messages from the local buffer as specified by the limit. If no messages are available to dequeue, then waits for at most timeout
     * milliseconds before returning.
     *
     * @param   <T>      The result type.
     * @param   topic    The topic to dequeue messages from.
     * @param   type     The type that each message should be converted to.
     * @param   timeout  The max amount of time in milliseconds that the function can take to dequeue limit number of messages. If number of dequeued
     *                   messages is less than limit, then only those messages are returned.
     * @param   limit    The max number of messages to dequeue.
     *
     * @return  Messages of the given type belonging to the given topic. Empty list if no such topic exists or the method times out.
     */
    public <T extends Serializable> List<T> dequeueFromBuffer(String topic, JavaType type, int timeout, int limit) {
        List<T> result = new ArrayList<>();
        long cutoff = System.currentTimeMillis() + timeout;
        BlockingQueue<String> queue = _topics.get(topic).getMessages();

        while (System.currentTimeMillis() < cutoff && (limit < 0 || result.size() < limit)) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            try {
                String message = queue.poll(timeout, TimeUnit.MILLISECONDS);

                if (message != null && !message.isEmpty()) {
                    T object = _mapper.readValue(message, type);

                    result.add(object);
                    if (result.size() % 1000 == 0) {
                        _logger.debug("Dequeued {} messages from local buffer.", result.size());
                    }
                }
            } catch (InterruptedException e) {
                _logger.warn("Interrupted while waiting for poll() to return a message.");
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                _logger.warn("Exception while deserializing message to type: " + type + ". Skipping this message.", e);
            }
        }
        return result;
    }

    /** Enqueue un-flushed messages back on to Kafka. */
    public void shutdown() {
        for (Topic topic : _topics.values()) {
            for (ConsumerWorker worker: topic.getWorkers()) {
                worker.shutdown();
            }
            topic.getExecutorService().shutdownNow();
            try {
                topic.getExecutorService().awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                _logger.warn("Stream executor service was interrupted while awaiting termination. This should never happen.");
            }
        }
        _logger.debug("Pushing unflushed messages back to Kafka.");

        Producer producer = new Producer(_configuration);

        for (Map.Entry<String, Topic> entry : _topics.entrySet()) {
            String topicName = entry.getKey();
            Topic topic = entry.getValue();
            List<String> unflushedMessages = new ArrayList<String>();

            if (!topic.getMessages().isEmpty()) {
                topic.getMessages().drainTo(unflushedMessages);
                producer.enqueue(topicName, unflushedMessages);
            }
            _logger.debug("{} messages for topic {} enqueued on Kafka queue", unflushedMessages.size(), topicName);
        }
        producer.shutdown();
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * The Kafka consumer worker.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    private class ConsumerWorker implements Runnable {
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final KafkaConsumer<String, String> _consumer;
        private final String _topic;

        /**
         * Creates a new Consumer object.
         *
         */
        public ConsumerWorker(KafkaConsumer<String, String> consumer, String topic) {
            _logger.debug("Creating a new stream");
            _consumer = consumer;
            _topic = topic;
        }

        public void shutdown() {
            closed.set(true);
            _consumer.wakeup();
        }

        @Override
        public void run() {
            try {
                _consumer.subscribe(Arrays.asList(_topic), new ConsumerRebalanceListener() {
                    @Override
                    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                        for (TopicPartition tp: partitions)
                            _logger.info("Partitions revoked for topic=" + tp.topic() + " and partition=" + tp.partition());
                    }

                    @Override
                    public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                        for (TopicPartition tp: partitions)
                            _logger.info("Partitions assigned for topic=" + tp.topic() + " and partition=" + tp.partition());
                    }
                });
                while (!closed.get()) {
                    if (Thread.currentThread().isInterrupted()) {
                        _logger.info("Interrupted... Will exit now.");
                        break;
                    }

                    ConsumerRecords<String, String> records = _consumer.poll(Long.MAX_VALUE);
                    try {
                        for (ConsumerRecord<String, String> record : records) {
                            String message = record.value();
                            _logger.info("GOT MESSAGE " + message);
                            String topic = record.topic();

                            if (message != null) {
                                _topics.get(topic).getMessages().put(message);

                                long c = count.incrementAndGet();

                                if (c % 50000 == 0) {
                                    _logger.debug("Read {} messages.", count.get());
                                }
                                if (_topics.get(topic).getMessages().size() % 1000 == 0) {
                                    _logger.debug("Message queued. Queue size = {}", _topics.get(topic).getMessages().size());
                                }
                            }
                        }
                    } catch (InterruptedException ex) {
                        _logger.debug("Interrupted while consuming message.");
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (WakeupException e) {
                // Ignore exception if closing
                if (!closed.get()) throw e;
            } finally {
                _logger.info("ConsumerWorker hit finally block");
                _consumer.close();
            }
        }
    }

    /**
     * Inner class that contains information about a particular topic. It includes a kafka consumer connector, an executor service for maintaining the
     * streaming threads and a blocking queue to store the actual messages.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    private class Topic {

        ExecutorService executorService;
        BlockingQueue<String> messages;
        List<ConsumerWorker> workers = new ArrayList<>();

        /**
         * Creates a new Topic object.
         *
         * @param topicName
         * @param consumerProps
         * @param  numStreams         The number of streams with which to consume the topic.
         */
        public Topic(final String topicName, Properties consumerProps, int numStreams) {
            messages = new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);
            executorService = Executors.newFixedThreadPool(numStreams, new ThreadFactory() {
                AtomicInteger id = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, MessageFormat.format("{0}-consumerworker-{1}", topicName, id.getAndIncrement()));
                }
            });
            for (int i = 0; i < numStreams; i++) {
                KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
                ConsumerWorker worker = new ConsumerWorker(consumer, topicName);
                workers.add(worker);
                executorService.submit(worker);
            }
        }

        /**
         * Returns the consumer connector.
         *
         * @return  The consumer connector.
         */
        public List<ConsumerWorker> getWorkers() {
            return workers;
        }

        /**
         * Returns the stream executor service.
         *
         * @return  The stream executor service.
         */
        public ExecutorService getExecutorService(){
            return executorService;
        }

        /**
         * Returns the dequeued messages.
         *
         * @return  The dequeued messages.
         */
        public BlockingQueue<String> getMessages() {
            return messages;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
