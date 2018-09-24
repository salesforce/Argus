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
     
package com.salesforce.dva.argus;

import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.system.SystemMain;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.DriverManager;
import java.sql.SQLNonTransientConnectionException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.Assert.fail;

@Ignore
public abstract class AbstractTest {

    private static final String scopeNameTemplate = "MetricGenerator.{0,number,#}";
    private static final String metricNameTemplate = "app_record_count.{0,number,#}";
    protected static final SecureRandom random = new SecureRandom();
    protected static final Map<String, String> tags;

    static {
        tags = new HashMap<>();
        tags.put("source", "unittest");
        ch.qos.logback.classic.Logger apacheLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache");
        apacheLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        ch.qos.logback.classic.Logger kafkaLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("kafka");
        kafkaLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        ch.qos.logback.classic.Logger zkLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.I0Itec.zkclient");
        zkLogger.setLevel(ch.qos.logback.classic.Level.OFF);
    }

    protected TestingServer zkTestServer;
    protected SystemMain system;
    protected KafkaServerStartable kafkaServer;
    private String tempDir = "";
    
    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
    }

    public static String createRandomName() {
        String className = AbstractTest.class.getSimpleName();
        Long randomTime = random.nextLong();
        Long systemTime = System.currentTimeMillis();

        return MessageFormat.format("{0}-{1}-{2}", className, Long.toHexString(systemTime), Long.toHexString(randomTime));
    }

    public static Metric createMetric() {
        int scopeIndex = ((int) (random.nextDouble() * 100));
        int metricIndex = ((int) (random.nextDouble() * 10));
        int datapointCount = ((int) (random.nextDouble() * 500)) + 1;
        String scope = MessageFormat.format(scopeNameTemplate, scopeIndex);
        String metric = MessageFormat.format(metricNameTemplate, metricIndex);
        Metric result = new Metric(scope, metric);
        Map<Long, Double> datapoints = new TreeMap<>();

        for (int i = 0; i < datapointCount; i++) {
            datapoints.put(System.currentTimeMillis(), random.nextDouble() * 500);
        }
        result.setDatapoints(datapoints);
        result.setTags(tags);
        return result;
    }
    
    public List<Metric> createRandomMetrics(String scope, String metric, int count) {
        List<Metric> result = new ArrayList<>(count);

        scope = scope == null ? createRandomName() : scope;

        String tag = createRandomName();

        for (int i = 0; i < count; i++) {
            String metricName = metric == null ? createRandomName() : metric;
            Metric met = new Metric(scope, metricName);
            int datapointCount = random.nextInt(25) + 1;
            Map<Long, Double> datapoints = new HashMap<>();
            long start = System.currentTimeMillis() - 60000L;

            for (int j = 0; j < datapointCount; j++) {
                datapoints.put(start - (j * 60000L), (double)(random.nextInt(100) + 1));
            }
            met.setDatapoints(datapoints);
            met.setDisplayName(createRandomName());
            met.setUnits(createRandomName());
            met.setTag(tag, String.valueOf(i));
            result.add(met);
        }
        return result;
    }

    public static Annotation createAnnotation() {
        int scopeIndex = ((int) (random.nextDouble() * 100));
        int metricIndex = ((int) (random.nextDouble() * 10));
        String scope = MessageFormat.format(scopeNameTemplate, scopeIndex);
        String metric = MessageFormat.format(metricNameTemplate, metricIndex);
        long timestamp = System.currentTimeMillis();
        Annotation result = new Annotation("unittest", Long.toHexString(timestamp), "unittest", scope, metric, timestamp);

        result.setTags(tags);
        return result;
    }

    private void setupEmbeddedKafka() {
        Properties properties = new Properties();

        properties.put("zookeeper.connect", zkTestServer.getConnectString());
        properties.put("host.name", "localhost");
        properties.put("port", "9093");
        properties.put("broker.id", "0");
        properties.put("num.partitions", "2");
        properties.put("log.flush.interval.ms", "10");
        properties.put("log.dir", "/tmp/kafka-logs/" + createRandomName());

        KafkaConfig config = new KafkaConfig(properties);

        kafkaServer = new KafkaServerStartable(config);
        kafkaServer.startup();
    }

    private void tearDownEmbeddedKafka() {
        if (kafkaServer != null) {
            kafkaServer.shutdown();
            kafkaServer.awaitShutdown();
            deleteFolder(new File(tempDir));
        }
    }

    @Before
    public void setUp() {
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            DriverManager.getConnection("jdbc:derby:memory:argus;create=true").close();
            zkTestServer = new TestingServer(2185);
        } catch (Exception ex) {
            LoggerFactory.getLogger(getClass()).error("Exception in setUp:{}", ex.getMessage());
            fail("Exception during database startup.");
        }
        setupEmbeddedKafka();
        system = getInstance();
        system.start();
    }

    @After
    public void tearDown() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
        tearDownEmbeddedKafka();
        try {
            zkTestServer.close();
            DriverManager.getConnection("jdbc:derby:memory:argus;shutdown=true").close();
        } catch (SQLNonTransientConnectionException ex) {
            if (ex.getErrorCode() >= 50000 || ex.getErrorCode() < 40000) {
                throw new RuntimeException(ex);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public SystemMain getInstance() {
        Properties config = new Properties();
        InputStream is = null;

        try {
            is = getClass().getResourceAsStream("/argus.properties");
            config.load(is);
        } catch (IOException ex) {
            throw new SystemException(ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    assert false : "This should never occur.";
                }
            }
        }
        return SystemMain.getInstance(config);
    } 
    
    public SystemMain getInstance(Properties props) {
        Properties config = new Properties();
        InputStream is = null;

        try {
            is = getClass().getResourceAsStream("/argus.properties");
            config.load(is);
            config.putAll(props);
        } catch (IOException ex) {
            throw new SystemException(ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    assert false : "This should never occur.";
                }
            }
        }
        system.stop();
        system = SystemMain.getInstance(config);
        system.start();
        return system;
    } 
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
