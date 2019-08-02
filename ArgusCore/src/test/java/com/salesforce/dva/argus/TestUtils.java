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

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.HistogramBucket;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.system.SystemMain;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.Assert.fail;


public class TestUtils {

    public static final SecureRandom random = new SecureRandom();
    private static final String scopeNameTemplate = "MetricGenerator.{0,number,#}";
    private static final String metricNameTemplate = "app_record_count.{0,number,#}";

    protected static final Map<String, String> tags;
    static private Properties props;

    static {
        tags = new HashMap<>();
        tags.put("source", "unittest");
        props = new Properties();
        props.put("system.property.mail.enabled", "false");
        props.put("system.property.admin.email", "argus-admin@mycompany.com");
        props.put("system.property.log.level", "ERROR");
        props.put("service.binding.cache", "com.salesforce.dva.argus.service.cache.NoOperationCacheService");
        props.put("service.binding.tsdb", "com.salesforce.dva.argus.service.tsdb.DefaultTSDBService");
        props.put("service.binding.audit", "com.salesforce.dva.argus.service.audit.DefaultAuditService");
        props.put("service.property.mq.connection.count", "2");
        props.put("service.property.mq.endpoint", "vm://localhost?broker.persistent=false");
        props.put("service.property.auth.ldap.authtype", "simple");
        props.put("service.property.auth.ldap.endpoint", "ldaps://ldaps.mycomany.com:636");
        props.put("service.property.auth.ldap.searchbase", "OU=active,OU=users,DC=mycompany,DC=com:OU=active,OU=robot,DC=mycompany,DC=com");
        props.put("service.property.auth.ldap.searchdn", "CN=argus_service,OU=active,OU=users,DC=mycompany,DC=com");
        props.put("service.property.auth.ldap.searchpwd", "argus_service_password");
        props.put("service.property.auth.ldap.usernamefield", "sAMAccountName");
        props.put("service.property.mail.alerturl.template", "https://localhost:8443/argus/#/alerts/$alertid$");
        props.put("service.property.mail.metricurl.template", "https://localhost:8443/argus/#/viewmetrics?expression=$expression$");
        props.put("service.property.mail.smtp.auth", "false");
        props.put("service.property.mail.smtp.host", "smtprelay.mycompany.com");
        props.put("service.property.mail.smtp.starttls.enable", "false");
        props.put("service.property.tsdb.connection.count", "2");
        props.put("service.property.tsdb.endpoint.read", "http://tsdbread.mycompany.com:4466");
        props.put("service.property.tsdb.endpoint.timeout", "10000");
        props.put("service.property.tsdb.endpoint.write", "http://tsdbwrite.mycompany.com:4477");
        props.put("service.property.tsdb.phoenix.jdbc.url", "${service.property.tsdb.phoenix.jdbc.url}");
        props.put("service.property.cache.redis.cluster", "redis0.mycompany.com:6379,redis1.mycompany.com:6389");
    }



    static public SystemConfiguration getConfiguration() {
        Properties config = new Properties();
        InputStream is = null;

        try {
            is = TestUtils.class.getResourceAsStream("/argus.properties");
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
        return new SystemConfiguration(config);
    }

    static public SystemMain getInstance() {
        Properties config = new Properties();
        InputStream is = null;

        try {
            is = TestUtils.class.getResourceAsStream("/argus.properties");
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

    static public SystemMain getInstanceWithInMemProps() {
        long start = System.currentTimeMillis();
        SystemMain system = SystemMain.getInstance(props);
        long end = System.currentTimeMillis();
        //        System.out.println("getInstanceWithInMemProps " + (end - start) + " milliseconds");
        return system;
    }

    public static String createRandomName() {
        return createRandomName(TestUtils.class.getSimpleName());
    }

    public static String createRandomName(String name) {
        String className = name;
        Long randomTime = random.nextLong();
        Long systemTime = System.currentTimeMillis();

        return MessageFormat.format("{0}-{1}-{2}", className, Long.toHexString(systemTime), Long.toHexString(randomTime));
    }

    public static List<Metric> createRandomMetrics(String className, String scope, String metric, int count) {
        List<Metric> result = new ArrayList<>(count);

        scope = scope == null ? createRandomName(className) : scope;

        String tag = createRandomName(className);

        for (int i = 0; i < count; i++) {
            String metricName = metric == null ? createRandomName(className) : metric;
            Metric met = new Metric(scope, metricName);
            int datapointCount = random.nextInt(25) + 1;
            Map<Long, Double> datapoints = new HashMap<>();
            long start = System.currentTimeMillis() - 60000L;

            for (int j = 0; j < datapointCount; j++) {
                datapoints.put(start - (j * 60000L), (double)(random.nextInt(100) + 1));
            }
            met.setDatapoints(datapoints);
            met.setDisplayName(createRandomName(className));
            met.setUnits(createRandomName(className));
            met.setTag(tag, String.valueOf(i));
            result.add(met);
        }
        return result;
    }

    public static Metric createMetric() {
        return createMetric(((int) (random.nextDouble() * 500)) + 1);
    }

    public static Metric createMetric(int datapointCount) {
        int scopeIndex = ((int) (random.nextDouble() * 100));
        int metricIndex = ((int) (random.nextDouble() * 10));
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

    public static Histogram createHistogram(int numHistogramBuckets) {
        int scopeIndex = ((int) (random.nextDouble() * 100));
        int metricIndex = ((int) (random.nextDouble() * 10));
        String scope = MessageFormat.format(scopeNameTemplate, scopeIndex);
        String metric = MessageFormat.format(metricNameTemplate, metricIndex);
        long timestamp = System.currentTimeMillis();
        Histogram result = new Histogram(scope, metric);

        Map<HistogramBucket, Long> buckets = new HashMap<>();
        float lowerBound = 0;
        float upperBound = 50;
        for (int i = 0; i < numHistogramBuckets; i++) {
            HistogramBucket histogramBucket= new  HistogramBucket(lowerBound, upperBound);
            buckets.put(histogramBucket, random.nextLong());
            lowerBound = upperBound;
            upperBound = upperBound + 100;
        }
        result.setBuckets(buckets);
        result.setTimestamp(timestamp);;
        result.setTags(tags);
        return result;
    }

    public static Histogram createHistogramWrongBounds(int numHistogramBuckets) {
        int scopeIndex = ((int) (random.nextDouble() * 100));
        int metricIndex = ((int) (random.nextDouble() * 10));
        String scope = MessageFormat.format(scopeNameTemplate, scopeIndex);
        String metric = MessageFormat.format(metricNameTemplate, metricIndex);
        long timestamp = System.currentTimeMillis();
        Histogram result = new Histogram(scope, metric);

        Map<HistogramBucket, Long> buckets = new HashMap<>();
        float lowerBound = 2;
        float upperBound = 1;
        for (int i = 0; i < numHistogramBuckets; i++) {
            HistogramBucket histogramBucket= new  HistogramBucket(lowerBound, upperBound);
            buckets.put(histogramBucket, random.nextLong());
            lowerBound = upperBound;
            upperBound = upperBound + 100;
        }
        result.setBuckets(buckets);
        result.setTimestamp(timestamp);;
        result.setTags(tags);
        return result;
    }

    public static Alert generateAlert(String alertName, PrincipalUser user, String expression) {
        Alert alert = new Alert(user, user, alertName, expression, "* * * * *");
        return alert;
    }

    public static Notification generateNotification(String notificationName, Alert alert, List<Trigger> triggers) {
        Notification notification = new Notification(notificationName, alert, "notifier-name", new ArrayList<String>(), 5000L);
        notification.setAlert(alert);
        notification.setTriggers(triggers);
        return notification;
    }

    public static Trigger generateTrigger(String triggerName, Alert alert) {
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN, triggerName, 0.95, 5000L);
        trigger.setAlert(alert);
        return trigger;
    }

    public static void setStaticField(Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.set(field, field.getModifiers() & ~Modifier.FINAL);
            field.set(null, value);
        } catch (Exception ex) {
            fail(ex.getMessage());
        }
    }

    public static void setField(Object obj, String fieldName, Object value) {
        try {
            Whitebox.setInternalState(obj, fieldName, value);
        } catch (Exception ex) {
            fail(ex.getMessage());
        }
    }

    public static Notification getNotification(String notificationName, String notifierName, Alert alert, List<String> subscriptionList) {
        Notification notification = new Notification(notificationName, alert, notifierName,
                subscriptionList, 5000L);
        notification.setSeverityLevel(4);
        return notification;
    }

    public static History getHistory() {
        return new History("TEST HISTORY MESSAGE", "TEST_HOST", new BigInteger("100002"),
                History.JobStatus.STARTED, 10, System.currentTimeMillis() - 86400000);
    }

    public static Metric getMetric() {
        SecureRandom random = new SecureRandom();
        return createMetric(((int) (random.nextDouble() * 500)) + 1);
    }

    public static Trigger getTrigger(Alert alert, Trigger.TriggerType triggerType, String triggerName, String triggerThreshold, String triggerInertiaMillis) {
        return new Trigger(alert, triggerType, triggerName, Double.parseDouble(triggerThreshold), Long.parseLong(triggerInertiaMillis));
    }
}
