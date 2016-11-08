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
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.consumer.Whitelist;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.MessageAndMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
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

                    props.setProperty("zookeeper.connect",
                        _configuration.getValue(Property.ZOOKEEPER_CONNECT.getName(), Property.ZOOKEEPER_CONNECT.getDefaultValue()));
                    props.setProperty("group.id",
                        _configuration.getValue(Property.KAFKA_CONSUMER_GROUPID.getName(), Property.KAFKA_CONSUMER_GROUPID.getDefaultValue()));
                    props.setProperty("auto.offset.reset", "smallest");
                    props.setProperty("auto.commit.interval.ms", "60000");
                    props.setProperty("fetch.message.max.bytes", "2000000");

                    ConsumerConnector consumer = kafka.consumer.Consumer.createJavaConsumerConnector(new ConsumerConfig(props));
                    List<KafkaStream<byte[], byte[]>> streams = _createStreams(consumer, topic);
                    Topic t = new Topic(topic, consumer, streams.size());

                    _topics.put(topic, t);
                    _startStreamingMessages(topic, streams);
                }
            }
        }
    }

    private List<KafkaStream<byte[], byte[]>> _createStreams(ConsumerConnector consumer, String topicName) {
        int numStreams = Math.max(Integer.parseInt(
            _configuration.getValue(Property.KAFKA_CONSUMER_STREAMS_PER_TOPIC.getName(),
                Property.KAFKA_CONSUMER_STREAMS_PER_TOPIC.getDefaultValue())), 2);

        return consumer.createMessageStreamsByFilter(new Whitelist(topicName), numStreams);
    }

    /**
     * Retrieves the executor service for the given topic from the map of topics and submits a KafkaConsumer task for each stream in the list of
     * streams.
     *
     * @param  topic    The topic to start streaming messages from.
     * @param  streams  The streams for those messages.
     */
    private void _startStreamingMessages(String topic, List<KafkaStream<byte[], byte[]>> streams) {
        ExecutorService executorService = _topics.get(topic).getStreamExecutorService();

        for (final KafkaStream<byte[], byte[]> stream : streams) {
            executorService.submit(new KafkaConsumer(stream));
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
            if (topic.getConsumerConnector() != null) {
                topic.getConsumerConnector().shutdown();
            }
            topic.getStreamExecutorService().shutdownNow();
            try {
                topic.getStreamExecutorService().awaitTermination(60, TimeUnit.SECONDS);
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
    private class KafkaConsumer implements Runnable {

        private final KafkaStream<byte[], byte[]> _stream;

        /**
         * Creates a new Consumer object.
         *
         * @param  stream  The Kafka stream to consume.
         */
        public KafkaConsumer(KafkaStream<byte[], byte[]> stream) {
            _logger.debug("Creating a new stream");
            _stream = stream;
        }

        @Override
        public void run() {
            ConsumerIterator<byte[], byte[]> it = _stream.iterator();

            while (it.hasNext()) {
                Thread.yield();
                if (Thread.currentThread().isInterrupted()) {
                    _logger.info("Interrupted... Will exit now.");
                    break;
                }

                MessageAndMetadata<byte[], byte[]> m = it.next();

                try {
                    String message = new String(m.message());
                    String topic = m.topic();

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
                } catch (InterruptedException ie) {
                    _logger.debug("Interrupted while consuming message.");
                    Thread.currentThread().interrupt();
                }
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

        private ConsumerConnector _consumerConnector;
        private ExecutorService _streamExecutorService;
        private BlockingQueue<String> _messages;

        /**
         * Creates a new Topic object.
         *
         * @param  name               The topic name.
         * @param  consumerConnector  The Kafka consumer connector.
         * @param  numStreams         The number of streams with which to consume the topic.
         */
        public Topic(String name, ConsumerConnector consumerConnector, int numStreams) {
            this(name, consumerConnector, numStreams, new LinkedBlockingQueue<String>(MAX_BUFFER_SIZE));
        }

        /**
         * Creates a new Topic object.
         *
         * @param  name               The topic name.
         * @param  consumerConnector  The Kafka consumer connector.
         * @param  numStreams         The number of streams with which to consume the topic.
         * @param  messages           The queue into which messages will be consumed.
         */
        public Topic(final String name, ConsumerConnector consumerConnector, int numStreams, BlockingQueue<String> messages) {
            _consumerConnector = consumerConnector;
            _messages = messages;
            _streamExecutorService = Executors.newFixedThreadPool(numStreams, new ThreadFactory() {

                    AtomicInteger id = new AtomicInteger(0);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, MessageFormat.format("{0}-stream-{1}", name, id.getAndIncrement()));
                    }
                });
        }

        /**
         * Returns the consumer connector.
         *
         * @return  The consumer connector.
         */
        public ConsumerConnector getConsumerConnector() {
            return _consumerConnector;
        }

        /**
         * Returns the stream executor service.
         *
         * @return  The stream executor service.
         */
        public ExecutorService getStreamExecutorService() {
            return _streamExecutorService;
        }

        /**
         * Returns the dequeued messages.
         *
         * @return  The dequeued messages.
         */
        public BlockingQueue<String> getMessages() {
            return _messages;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
