package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MetricStorageService;
import com.salesforce.dva.argus.system.SystemMain;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.powermock.reflect.Whitebox;

import static com.salesforce.perfeng.akc.consumer.InstrumentationService.METRIC_CONSUMER_LAG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AmurKafkaRunnerTest  {
    private ConsumerType consumerType = ConsumerType.METRICS;

    @Before
    public void setUp() {
        System.setProperty("akc.common.configuration", "src/test/resources/akc.config");
    }

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Mock
    private KafkaConsumer<String, String> mockConsumer;
    private InstrumentationService mockInstrumentationService;

    @Test
    public void testAmurTaskAssignableExits() {
        exit.expectSystemExitWithStatus(2);

        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                                                  props,
                                                  system,
                                                  new AtomicInteger(0),
                                                  Class.class);
    }

    @Test
    public void testVerifyParamsDoesntExit() {

        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                                                  props,
                                                  system,
                                                  new AtomicInteger(0),
                                                  AjnaConsumerTask.class);
    }

    @Test
    public void testAbsenceOfKeyValDeserExits() {
        exit.expectSystemExitWithStatus(2);

        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                                                  props,
                                                  system,
                                                  new AtomicInteger(0),
                                                  AjnaConsumerTask.class);
    }

    @Test
    public void testAbsenceOfGroupidExits() {
        exit.expectSystemExitWithStatus(2);

        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                                                  props,
                                                  system,
                                                  new AtomicInteger(0),
                                                  AjnaConsumerTask.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCatchupConsumerWithoutStartStopSettings(){
        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);
    }

    @Test(expected = NumberFormatException.class)
    public void testCatchupConsumerInvalidEpoch() {
        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("custom.start.time.epoch.ms", "600");
        mandatoryKafkaProps.put("custom.stop.time.epoch.ms", "abc");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCatchupConsumerNegativeEpoch(){
        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("custom.start.time.epoch.ms", "-1546131600");
        mandatoryKafkaProps.put("custom.stop.time.epoch.ms", "1546218000");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCatchupConsumerFutureStartEpoch(){
        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("custom.start.time.epoch.ms", "2808780816000");
        mandatoryKafkaProps.put("custom.stop.time.epoch.ms", "2808880816000");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);
    }

    @Test
    public void testCatchupConsumerPastStartFutureEnd(){
        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("custom.stop.time.epoch.ms", "2808780816");
        mandatoryKafkaProps.put("custom.start.time.epoch.ms", "1546218000");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCatchupConsumerSwapperStartandEnd(){
        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("custom.start.time.epoch.ms", "2808780816");
        mandatoryKafkaProps.put("custom.stop.time.epoch.ms", "1546218000");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);
    }

    @Test
    public void testOffsetComputationPerTopic() {

        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("custom.offset.set", "custom");
        mandatoryKafkaProps.put("custom.stop.time.epoch.ms", "2808780816");
        mandatoryKafkaProps.put("custom.start.time.epoch.ms", "1546218000");
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        mandatoryKafkaProps.put("value.deserializer",
                "org.apache.kafka.common.serialization.ByteArrayDeserializer");

        for (Map.Entry<String, String> entry : mandatoryKafkaProps.entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }

        SystemMain system = SystemMain.getInstance();
        AmurKafkaRunner akr = new AmurKafkaRunner(consumerType,
                props,
                system,
                new AtomicInteger(0),
                AjnaConsumerTask.class);

        // Creates 5 partition for 2 topics and computes the current offset by index % 3 and latest offset as index.
        /* Topic        Partition -> (produced, consumed)
        * "testTopicEven" -> { 0 -> (1, 0)
        *                      2 -> (3, 2)
        *                      4 -> (5, 1)
        *                      6 -> (7, 0)
        *                      8 -> (9, 2)
        *                    }
        * "testTopicOdd"  -> { 1 -> (2, 1)
        *                      3 -> (4, 0)
        *                      5 -> (6, 2)
        *                      7 -> (8, 1)
        *                      9 -> (10, 0)
        *                    }
        *
        */
        Map<TopicPartition, Long> mockLatestOffset = new HashMap<>();
        Map<TopicPartition, Long> expectedOffset = new HashMap<>();
        Set<TopicPartition> partitionSubscribed = new HashSet<>();
        for(int i = 0; i < 10; i++) {
            String topic = "testTopicEven";
            if(i % 2 == 1) {
                topic = "testTopicOdd";
            }
            TopicPartition tp = new TopicPartition(topic, i);
            mockLatestOffset.put(tp, (long) (i + 1));
            OffsetAndMetadata oM = mock(OffsetAndMetadata.class);
            when(mockConsumer.committed(tp)).thenReturn(oM);
            when(mockConsumer.committed(tp).offset()).thenReturn((long) (i % 3));
            partitionSubscribed.add(tp);
            expectedOffset.put(tp, (long) (i + 1 - i % 3));
        }

        mockInstrumentationService = mock(InstrumentationService.class);
        MetricStorageService mockConsumerOffsetMetricStorage = mock(MetricStorageService.class);
        when(mockConsumer.endOffsets(anyCollection())).thenReturn(mockLatestOffset);
        Metric m = new Metric("scope", "metric");
        when(mockInstrumentationService.constructMetric(anyString(),anyMap(),anyBoolean())).thenReturn(m);
        doNothing().when(mockInstrumentationService).setCounterValue(anyString(), anyDouble(), anyMap());
        doNothing().when(mockConsumerOffsetMetricStorage).putMetrics(any());
        Whitebox.setInternalState(akr, "consumer", mockConsumer);
        Whitebox.setInternalState(akr, "instrumentationService", mockInstrumentationService);
        Whitebox.setInternalState(akr, "consumerOffsetMetricStorageService", mockConsumerOffsetMetricStorage);

        try {
            Whitebox.invokeMethod(akr, "computeAndPushLagOffsetPerPartitionPerTopic");
        } catch (Exception e) {
            fail();
            System.out.println(e.getMessage());
        }

    }
}
