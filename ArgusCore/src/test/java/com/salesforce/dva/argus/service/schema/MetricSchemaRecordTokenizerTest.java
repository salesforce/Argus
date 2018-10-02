package com.salesforce.dva.argus.service.schema;

import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.SchemaService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

import static org.junit.Assert.assertTrue;

public class MetricSchemaRecordTokenizerTest {

    @Test
    public void testScopeTokensLevelZero() {

        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope1", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope1", "metric1", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric2", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric3", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric4", "source", "unittest"));

        SortedSet<String> scopes = MetricSchemaRecordTokenizer.GetUniqueTokens(records, SchemaService.RecordType.SCOPE, 0);

        assertTrue(scopes.size() == 2);
        assertTrue(scopes.contains("scope1"));
        assertTrue(scopes.contains("scope2"));
    }

    @Test
    public void testScopeTokensLevelOne() {

        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope1.foo", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope1.foo", "metric1", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2.bar", "metric2", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2.bar", "metric3", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2.bar", "metric4", "source", "unittest"));

        SortedSet<String> scopes = MetricSchemaRecordTokenizer.GetUniqueTokens(records, SchemaService.RecordType.SCOPE, 1);

        assertTrue(scopes.size() == 2);
        assertTrue(scopes.contains("foo"));
        assertTrue(scopes.contains("bar"));
    }

    @Test
    public void testMetricTokensLevelZero() {

        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope1", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope1", "metric1", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric2", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric3", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric4", "source", "unittest"));

        SortedSet<String> metrics = MetricSchemaRecordTokenizer.GetUniqueTokens(records, SchemaService.RecordType.METRIC, 0);

        assertTrue(metrics.size() == 5);
        assertTrue(metrics.contains("metric0"));
        assertTrue(metrics.contains("metric4"));
    }

    @Test
    public void testTypeWithNullValue() {

        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope1", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope1", "metric1", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric2", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric3", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope2", "metric4", "source", "unittest"));

        SortedSet<String> ns = MetricSchemaRecordTokenizer.GetUniqueTokens(records, SchemaService.RecordType.NAMESPACE, 0);

        assertTrue(ns.size() == 0);
    }

}
