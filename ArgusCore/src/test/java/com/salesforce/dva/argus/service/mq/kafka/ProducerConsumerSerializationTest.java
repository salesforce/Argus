package com.salesforce.dva.argus.service.mq.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ProducerConsumerSerializationTest {
    private SystemConfiguration config;
    @Mock
    private KafkaProducer<String, String> kafkaProducer;
    @Mock
    private ExecutorService executorService;
    private ObjectMapper mapper;
    private int maxBufferSize = 1000;

    private Producer producer;
    private Consumer consumer;

    @Before
    public void setUp() {
        config = new SystemConfiguration(new Properties());
        mapper = new ObjectMapper();
        producer = new Producer(config, kafkaProducer, executorService, mapper);
        consumer = new Consumer(config, mapper, maxBufferSize);
    }

    @Test
    public void serialize_deserialize_testAnnotation() throws Exception {
        Annotation a = new Annotation("testsource",
                "testid",
                "testtype",
                "testscope",
                "testmetric",
                1549656991903L);
        a.setTag("testtagkey", "testtagvalue");

        String json = producer.serialize(a);
        Annotation result = consumer.deserialize(json, Annotation.class);

        assertEquals(a, result);
    }

    @Test
    public void serialize_deserialize_testMetric() throws Exception {
        Metric m = new Metric("testscope", "testmetric");
        m.setTag("testtagkey", "testtagvalue");
        m.setDatapoints(ImmutableMap.of(1549656000000L, 3.14));

        String json = producer.serialize(m);
        Metric result = consumer.deserialize(json, Metric.class);

        assertEquals(m, result);
    }

}
