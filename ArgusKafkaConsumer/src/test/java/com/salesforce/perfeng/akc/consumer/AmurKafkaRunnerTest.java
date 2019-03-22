package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.system.SystemMain;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Map;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;

public class AmurKafkaRunnerTest  {
    private ConsumerType consumerType = ConsumerType.METRICS;

    @Before
    public void setUp() {
        System.setProperty("akc.common.configuration", "src/test/resources/akc.config");
    }

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Test
    public void testAmurTaskAssignableExits() {
        exit.expectSystemExitWithStatus(2);

        Properties props = new Properties();
        Map<String, String> mandatoryKafkaProps = new HashMap<>();
        mandatoryKafkaProps.put("topics", "blah");
        mandatoryKafkaProps.put("group.id", "blah");
        mandatoryKafkaProps.put("bootstrap.servers", "blah");
        mandatoryKafkaProps.put("key.deserializer",
                                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
                "org.apache.kafka.common.serialization.ByteArrayDeserialize1r");
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
}
