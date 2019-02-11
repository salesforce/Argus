package com.salesforce.dva.argus.entity;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;

public abstract class AbstractSchemaRecord {
    private static Logger _logger = LoggerFactory.getLogger(AbstractSchemaRecord.class);

    private final static String randomBloomAppend;
    static {
        String appendValue;
        try {
            appendValue = Integer.toString(Math.abs(InetAddress.getLocalHost().getHostName().hashCode()));
        } catch (IOException io) {
            appendValue = "12345";
            _logger.error("Failed to create randomBloomAppend, using {}. {}", appendValue, io);
        }
        randomBloomAppend = appendValue;
    }

    public abstract String toBloomFilterKey();

    public static String getBloomAppend() {
        return randomBloomAppend;
    }

    public static String constructKey(String scope, String metric, String tagk, String tagv, String namespace, String retention) {
        StringBuilder sb = new StringBuilder(scope);
        if(!StringUtils.isEmpty(metric)) {
            sb.append('\0').append(metric);
        }
        if(!StringUtils.isEmpty(namespace)) {
            sb.append('\0').append(namespace);
        }
        if(!StringUtils.isEmpty(tagk)) {
            sb.append('\0').append(tagk);
        }
        if(!StringUtils.isEmpty(tagv)) {
            sb.append('\0').append(tagv);
        }
        //there is use case where users simply want to update the retention without touching rest of a metric
        if(!StringUtils.isEmpty(retention)) {
            sb.append('\0').append(retention);
        }
        sb.append('\0').append(randomBloomAppend);

        return sb.toString();
    }

    public static String constructKey(Metric metric, String tagk, String tagv) {
        return constructKey(metric.getScope(),
                metric.getMetric(),
                tagk,
                tagv,
                metric.getNamespace(),
                metric.getMetatagsRecord() == null ? null : metric.getMetatagsRecord().getMetatagValue(MetricSchemaRecord.RETENTION_DISCOVERY));
    }

    public static String constructKey(String scope) {
        return constructKey(scope, null, null, null, null, null);
    }
}
