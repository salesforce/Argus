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
     
package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.schema.HbaseSchemaService;
import com.salesforce.dva.argus.service.schema.HbaseSchemaService.TableType;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
@Ignore
public class SchemaServiceIT extends AbstractTest {

    private SchemaService _schemaService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        _schemaService = system.getServiceFactory().getSchemaService();
    }

    @Test
    public void testServiceIsSingleton() {
        assertTrue(_schemaService == system.getServiceFactory().getSchemaService());
    }

    @Test
    public void testMetricSchemaRecordPutAndGet() {
        List<MetricSchemaRecordQuery> queries = new ArrayList<MetricSchemaRecordQuery>();
        List<MetricSchemaRecord> expected = new ArrayList<MetricSchemaRecord>();
        List<Metric> metrics = new ArrayList<Metric>();

        for (int i = 0; i < 10; i++) {
            Metric m = createMetric();

            metrics.add(m);
            for (String tagKey : m.getTags().keySet()) {
                expected.add(new MetricSchemaRecord(m.getNamespace(), m.getScope(), m.getMetric(), tagKey, m.getTag(tagKey)));
                queries.add(new MetricSchemaRecordQuery(m.getNamespace(), m.getScope(), m.getMetric(), tagKey, m.getTag(tagKey)));
            }
        }
        _schemaService.put(metrics);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        List<MetricSchemaRecord> actual = new ArrayList<MetricSchemaRecord>();

        for (MetricSchemaRecordQuery query : queries) {
            actual.addAll(_schemaService.get(query, 1, 1));
        }
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);
    }

    private Metric _createNonRandomMetric(int i) {
        Metric m = new Metric("scope" + i, "metric" + i);

        m.setTags(tags);
        return m;
    }

    @Test
    public void testMetricSchemaRecordScan() {
        List<MetricSchemaRecord> expected = new ArrayList<MetricSchemaRecord>();
        List<Metric> metrics = new ArrayList<Metric>();

        for (int i = 0; i < 10; i++) {
            Metric m = _createNonRandomMetric(i);

            metrics.add(m);
            for (String tagKey : m.getTags().keySet()) {
                expected.add(new MetricSchemaRecord(m.getNamespace(), m.getScope(), m.getMetric(), tagKey, m.getTag(tagKey)));
            }
        }
        _schemaService.put(metrics);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        List<MetricSchemaRecord> actual = new ArrayList<MetricSchemaRecord>();

        actual.addAll(_schemaService.get(new MetricSchemaRecordQuery(null, "scope*", "metric*", "source", "unittest"), 10, 1));
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, actual);

        List<MetricSchemaRecord> actual_page1 = new ArrayList<MetricSchemaRecord>();

        actual_page1.addAll(_schemaService.get(new MetricSchemaRecordQuery(null, "scope*", "metric*", "source", "unittest"), 5, 1));
        assertEquals(5, actual_page1.size());
        assertEquals(expected.subList(0, 5), actual_page1);

        List<MetricSchemaRecord> actual_page2 = new ArrayList<MetricSchemaRecord>();

        actual_page2.addAll(_schemaService.get(new MetricSchemaRecordQuery(null, "scope*", "metric*", "source", "unittest"), 5, 2));
        assertEquals(5, actual_page2.size());
        assertEquals(expected.subList(5, 10), actual_page2);
    }

    @Test
    public void testMetricSchemaRecordScansWithDifferentRegexes() {
        List<Metric> metrics = new ArrayList<Metric>();

        for (int i = 0; i < 10; i++) {
            Metric m = _createNonRandomMetric(i);

            metrics.add(m);
        }
        _schemaService.put(metrics);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        List<String> uniqueScopes = _schemaService.getUnique(new MetricSchemaRecordQuery(null, "scope[4-5]", "metric*", "source", "unittest"), 10, 1,
            RecordType.SCOPE);

        assertFalse(uniqueScopes.contains("scope3"));
        assertTrue(uniqueScopes.contains("scope4"));
        assertTrue(uniqueScopes.contains("scope5"));
        assertFalse(uniqueScopes.contains("scope6"));

        List<String> uniqueMetrics = _schemaService.getUnique(new MetricSchemaRecordQuery(null, "scope*", "metric[2|3]", "source", "unittest"), 10, 1,
            RecordType.METRIC);

        assertFalse(uniqueMetrics.contains("metric1"));
        assertTrue(uniqueMetrics.contains("metric2"));
        assertTrue(uniqueMetrics.contains("metric3"));
        assertFalse(uniqueMetrics.contains("metric4"));
        uniqueMetrics = _schemaService.getUnique(new MetricSchemaRecordQuery(null, "scope0", "metr??0", "source", "unittest"), 10, 1,
            RecordType.METRIC);
        assertTrue(uniqueMetrics.contains("metric0"));
    }

    @Test
    public void testMetricSchemaUniqueScopes() {
        List<String> expectedScopes = new ArrayList<String>();
        List<Metric> metrics = new ArrayList<Metric>();

        for (int i = 0; i < 10; i++) {
            Metric m = _createNonRandomMetric(i);

            metrics.add(m);
            expectedScopes.add(m.getScope());
        }
        _schemaService.put(metrics);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            assertTrue(false);
        }

        List<String> actual = new ArrayList<String>();

        actual.addAll(_schemaService.getUnique(new MetricSchemaRecordQuery(null, "scope?", "metric?", "source", "unittest"), 10, 1,
                RecordType.SCOPE));
        assertEquals(expectedScopes.size(), actual.size());
        assertEquals(expectedScopes, actual);

        List<String> actual_page1 = new ArrayList<String>();

        actual_page1.addAll(_schemaService.getUnique(new MetricSchemaRecordQuery(null, "scope?", "metric?", "source", "unittest"), 5, 1,
                RecordType.SCOPE));
        assertEquals(5, actual_page1.size());
        assertEquals(expectedScopes.subList(0, 5), actual_page1);

        List<String> actual_page2 = new ArrayList<String>();

        actual_page2.addAll(_schemaService.getUnique(new MetricSchemaRecordQuery(null, "scope?", "metric?", "source", "unittest"), 5, 2,
                RecordType.SCOPE));
        assertEquals(5, actual_page2.size());
        assertEquals(expectedScopes.subList(5, 10), actual_page2);
    }

    @Test
    public void testConstructScanMetadata() {
        Method method;

        try {
            method = HbaseSchemaService.class.getDeclaredMethod("_constructScanMetadata", MetricSchemaRecordQuery.class);
            method.setAccessible(true);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new SystemException("Failed to get method via reflection", e);
        }

        ScanMetadataTest metadata = new ScanMetadataTest();

        metadata.startRow = "scope:metric:tagk:tagv:namespace";
        metadata.stopRow = "scope:metric:tagk:tagv:namespacf";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("namespace", "scope", "metric", "tagk", "tagv"), method, metadata);
        metadata.startRow = "system";
        metadata.stopRow = "systen";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system*", "*", "*", "*"), method, metadata);
        metadata.startRow = "system";
        metadata.stopRow = "systen";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system*", "Cpu[P]erc.cpu.interrupt", "*", "*"), method, metadata);
        metadata.startRow = "";
        metadata.stopRow = "";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "*", "*", "*", "*"), method, metadata);
        metadata.startRow = "system";
        metadata.stopRow = "systen";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system*", "CpuPer*", "*", "*"), method, metadata);
        metadata.startRow = "system.chi.agg.cs10:CpuPerc:";
        metadata.stopRow = "system.chi.agg.cs10:CpuPerc;";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system.chi.agg.cs10", "CpuPerc", "*", "*"), method, metadata);
        metadata.startRow = "CpuPerc.cpu.interrupt:system";
        metadata.stopRow = "CpuPerc.cpu.interrupt:systen";
        metadata.type = TableType.METRIC;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system*", "CpuPerc.cpu.interrupt", "*", "*"), method, metadata);
        metadata.startRow = "CpuPerc.cpu";
        metadata.stopRow = "CpuPerc.cpv";
        metadata.type = TableType.METRIC;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "sys*", "CpuPerc.cpu*", "*", "*"), method, metadata);
        metadata.startRow = "system:CpuPerc.cpu.interrupt:";
        metadata.stopRow = "system:CpuPerc.cpu.interrupt;";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system", "CpuPerc.cpu.interrupt", "*", "*"), method, metadata);
        metadata.startRow = "system:CpuPerc.cpu.interrupt:device:cs";
        metadata.stopRow = "system:CpuPerc.cpu.interrupt:device:ct";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "system", "CpuPerc.cpu.interrupt", "device", "cs*"), method, metadata);
        metadata.startRow = "sys";
        metadata.stopRow = "syt";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery("*", "sys?", "*", "device", "cs*"), method, metadata);
        metadata.startRow = "00D0010000062.na1:";
        metadata.stopRow = "00D0010000062.na1;";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery(null, "00D0010000062.na1", "runtime|cputime", "recordType", "A|U|V|R"), method,
            metadata);
        metadata.startRow = "00D0010000062.na1:app";
        metadata.stopRow = "00D0010000062.na1:apq";
        metadata.type = TableType.SCOPE;
        _testConstructScanMetadata(new MetricSchemaRecordQuery(null, "00D0010000062.na1", "app[run|cpu]time", "recordType", "A|U|V|R"), method,
            metadata);
    }

    private void _testConstructScanMetadata(MetricSchemaRecordQuery query, Method method, ScanMetadataTest expected) {
        HbaseSchemaService schemaService = new HbaseSchemaService(system.getConfiguration());

        try {
            Object metadata;
            byte[] startRow, stopRow;
            TableType type;

            metadata = method.invoke(schemaService, query);
            startRow = (byte[]) _getFieldValue(metadata, "startRow");
            assertTrue(Bytes.toString(startRow).equals(expected.startRow));
            stopRow = (byte[]) _getFieldValue(metadata, "stopRow");
            assertTrue(Bytes.toString(stopRow).equals(expected.stopRow));
            type = (TableType) _getFieldValue(metadata, "type");
            assertTrue(type.equals(expected.type));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new SystemException("Failed to invoke method via reflection", e);
        }
    }

    private Object _getFieldValue(Object obj, String fieldName) {
        Field field;

        try {
            field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            throw new SystemException("Failed to get field via reflection", e);
        }
    }

    private static class ScanMetadataTest {

        String startRow;
        String stopRow;
        TableType type;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
