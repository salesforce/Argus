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
	 
package com.salesforce.dva.argus.service.schema;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HConnection;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.Filter;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.FirstKeyOnlyFilter;
import org.apache.hadoop.hbase.filter.KeyOnlyFilter;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.RowFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * HBASE implementation of the schema service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@Singleton
public class HbaseSchemaService extends DefaultService implements SchemaService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final byte[] COLUMN_FAMILY = "f".getBytes(Charset.forName("UTF-8"));
    private static final byte[] COLUMN_QUALIFIER = "c".getBytes(Charset.forName("UTF-8"));
    private static final byte[] CELL_VALUE = "1".getBytes(Charset.forName("UTF-8"));
    private static final char ROWKEY_SEPARATOR = ':';
    private static final char[] WILDCARD_CHARSET = new char[] { '*', '?', '[', ']', '|' };

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private HConnection _connection;
    private final SystemConfiguration _config;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new HbaseSchemaService object.
     *
     * @param  config  The system configuration.  Cannot be null.
     */
    @Inject
    public HbaseSchemaService(SystemConfiguration config) {
    	super(config);
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    private static boolean _isWildcardCharacter(char c) {
        for (char ch : WILDCARD_CHARSET) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }

    private static String _convertToRegex(String wildcardStr) {
        if (wildcardStr == null || wildcardStr.isEmpty()) {
            return wildcardStr;
        }

        char[] arr = wildcardStr.toCharArray();
        char[] result = new char[arr.length * 3];
        boolean flag = false;
        int j = -1, k = 0;

        for (int i = 0; i < arr.length; i++, k++) {
            k = _replace(result, arr, k, i);
            if (arr[i] == '[') {
                j = k;
            }
            if (arr[i] == '|') {
                if (j != -1) {
                    result[j] = '(';
                    while (i < arr.length && arr[i] != ']') {
                        k = _replace(result, arr, k, i);
                        i++;
                        k++;
                    }
                    if (i < arr.length) {
                        result[k] = ')';
                        j = -1;
                    }
                } else {
                    flag = true;
                }
            }
        }
        if (flag) {
            return "(" + new String(result).trim() + ")";
        }
        return new String(result).trim();
    }

    private static int _replace(char[] dest, char[] orig, int destIndex, int origIndex) {
        if (orig[origIndex] == '?') {
            dest[destIndex] = '.';
            return destIndex;
        } else if (orig[origIndex] == '*') {
            dest[destIndex] = '.';
            dest[destIndex + 1] = '*';
            return destIndex + 1;
        } else if (orig[origIndex] == '.') {
            dest[destIndex] = '\\';
            dest[destIndex + 1] = '.';
            return destIndex + 1;
        }
        dest[destIndex] = orig[origIndex];
        return destIndex;
    }

    private static String _constructRowKey(String namespace, String scope, String metric, String tagKey, String tagValue, TableType type) {
        namespace = namespace == null ? "null" : namespace;
        tagKey = tagKey == null ? "null" : tagKey;
        tagValue = tagValue == null ? "null" : tagValue;

        String key;

        if (type.equals(TableType.SCOPE)) {
            key = MessageFormat.format("{0}{5}{1}{5}{2}{5}{3}{5}{4}", scope, metric, tagKey, tagValue, namespace, ROWKEY_SEPARATOR);
        } else if (type.equals(TableType.METRIC)) {
            key = MessageFormat.format("{0}{5}{1}{5}{2}{5}{3}{5}{4}", metric, scope, tagKey, tagValue, namespace, ROWKEY_SEPARATOR);
        } else {
            throw new SystemException(new IllegalArgumentException("Unknown table type: " + type.name()));
        }
        return key;
    }

    private static MetricSchemaRecord _constructMetricSchemaRecord(String rowKey, TableType type) {
        SystemAssert.requireArgument(rowKey != null && !rowKey.isEmpty(), "This should never happen. Rowkey should never be null or empty.");

        String[] parts = rowKey.split(String.valueOf(ROWKEY_SEPARATOR));
        MetricSchemaRecord record;

        if (TableType.SCOPE.equals(type)) {
            record = new MetricSchemaRecord(parts[0], parts[1]);
        } else if (TableType.METRIC.equals(type)) {
            record = new MetricSchemaRecord(parts[1], parts[0]);
        } else {
            throw new SystemException(new IllegalArgumentException("Unknown table type: " + type.name()));
        }
        if (!"null".equals(parts[2])) {
            record.setTagKey(parts[2]);
        }
        if (!"null".equals(parts[3])) {
            record.setTagValue(parts[3]);
        }
        if (!"null".equals(parts[4])) {
            record.setNamespace(parts[4]);
        }
        return record;
    }

    //~ Methods **************************************************************************************************************************************

    private HConnection _createHConnection(SystemConfiguration systemConfig) {
        String value = systemConfig.getValue(Property.HBASE_ZOOKEEPER_CONNECT.getName(), Property.HBASE_ZOOKEEPER_CONNECT.getDefaultValue());
        String quorum = value.split(":")[0];
        String port = value.split(":")[1];
        Configuration config = HBaseConfiguration.create();

        config.set("hbase.zookeeper.quorum", quorum);
        config.set("hbase.zookeeper.property.clientPort", port);
        config.set("hbase.cluster.distributed", "true");
        try {
            return HConnectionManager.createConnection(config);
        } catch (IOException ioe) {
            throw new SystemException("Failed to instantiate HConnection object.", ioe);
        }
    }

    @Override
    public void put(Metric metric) {
        requireNotDisposed();
        SystemAssert.requireArgument(metric != null, "Metric cannot be null.");

        List<Metric> metrics = new ArrayList<Metric>(1);

        metrics.add(metric);
        put(metrics);
    }

    @Override
    public void put(List<Metric> metrics) {
        requireNotDisposed();
        SystemAssert.requireArgument(metrics != null && !metrics.isEmpty(), "Metric list cannot be null or empty.");

        Map<String, Put> scopePuts = new HashMap<String, Put>();
        Map<String, Put> metricPuts = new HashMap<String, Put>();

        for (Metric metric : metrics) {
            if (metric.getTags().isEmpty()) {
                String rowKeyStr = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), null, null, TableType.SCOPE);
                byte[] rowKey = Bytes.toBytes(rowKeyStr);
                Put put = new Put(rowKey);

                put.add(COLUMN_FAMILY, COLUMN_QUALIFIER, CELL_VALUE);
                if (!scopePuts.containsKey(rowKeyStr)) {
                    scopePuts.put(rowKeyStr, put);
                    _logger.debug(MessageFormat.format("Inserting row key {0} into table scope-schema", rowKeyStr));
                }
                rowKeyStr = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), null, null, TableType.METRIC);
                rowKey = Bytes.toBytes(rowKeyStr);
                put = new Put(rowKey);
                put.add(COLUMN_FAMILY, COLUMN_QUALIFIER, CELL_VALUE);
                if (!metricPuts.containsKey(rowKeyStr)) {
                    metricPuts.put(rowKeyStr, put);
                    _logger.debug(MessageFormat.format("Inserting row key {0} into table metric-schema", rowKeyStr));
                }
            }
            for (Entry<String, String> tag : metric.getTags().entrySet()) {
                String rowKeyStr = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), tag.getKey(), tag.getValue(),
                    TableType.SCOPE);
                byte[] rowKey = Bytes.toBytes(rowKeyStr);
                Put put = new Put(rowKey);

                put.add(COLUMN_FAMILY, COLUMN_QUALIFIER, CELL_VALUE);
                if (!scopePuts.containsKey(rowKeyStr)) {
                    scopePuts.put(rowKeyStr, put);
                    _logger.debug(MessageFormat.format("Inserting row key {0} into table scope-schema", rowKeyStr));
                }
                rowKeyStr = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), tag.getKey(), tag.getValue(),
                    TableType.METRIC);
                rowKey = Bytes.toBytes(rowKeyStr);
                put = new Put(rowKey);
                put.add(COLUMN_FAMILY, COLUMN_QUALIFIER, CELL_VALUE);
                if (!metricPuts.containsKey(rowKeyStr)) {
                    metricPuts.put(rowKeyStr, put);
                    _logger.debug(MessageFormat.format("Inserting row key {0} into table metric-schema", rowKeyStr));
                }
            }
        } // end for
        _logger.info("Metrics List size = " + metrics.size());

        HTableInterface scopeTable = null, metricTable = null;

        try {
            _logger.info("Scope Puts size = " + scopePuts.size());
            scopeTable = _getHbaseConnection().getTable(TableType.SCOPE.getTableName());
            scopeTable.put(new ArrayList<Put>(scopePuts.values()));
            _logger.info("Metric Puts size = " + metricPuts.size());
            metricTable = _getHbaseConnection().getTable(TableType.METRIC.getTableName());
            metricTable.put(new ArrayList<Put>(metricPuts.values()));
        } catch (IOException e) {
            throw new SystemException("Failed to put schema records to Hbase.", e);
        } finally {
            _closeTables(scopeTable, metricTable);
        }
    }

    private void _closeTables(HTableInterface... tables) {
        try {
            for (HTableInterface table : tables) {
                if (table != null) {
                    table.close();
                }
            }
        } catch (IOException ioe) {
            throw new SystemException("Failed to close HTable instance.", ioe);
        }
    }

    @Override
    public List<MetricSchemaRecord> get(MetricSchemaRecordQuery query, int limit, int page) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric Schema Record query cannot be null.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");
        SystemAssert.requireArgument(page > 0, "Page must be a positive integer.");

        List<MetricSchemaRecord> records = new ArrayList<MetricSchemaRecord>(limit);
        HTableInterface tableToUse = null;

        try {
            ScanMetadata metadata = _constructScanMetadata(query);
            String namespace = _convertToRegex(query.getNamespace());
            String scope = _convertToRegex(query.getScope());
            String metric = _convertToRegex(query.getMetric());
            String tagKey = _convertToRegex(query.getTagKey());
            String tagValue = _convertToRegex(query.getTagValue());
            String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.type) + "$";

            tableToUse = _getHbaseConnection().getTable(metadata.type.getTableName());
            _logger.debug("Using table: " + metadata.type.getTableName());
            _logger.debug("Rowkey: " + rowKeyRegex);
            _logger.debug("Scan startRow: " + Bytes.toString(metadata.startRow));
            _logger.debug("Scan stopRow: " + Bytes.toString(metadata.stopRow));

            Filter rowFilter = new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex));
            FilterList fl = new FilterList(FilterList.Operator.MUST_PASS_ALL, new KeyOnlyFilter(), new FirstKeyOnlyFilter(), rowFilter);
            Scan scan = new Scan();

            scan.setStartRow(metadata.startRow);
            scan.setStopRow(metadata.stopRow);
            scan.setFilter(fl);
            scan.setCaching(Math.min((limit * page), 10000));

            ResultScanner scanner = null;

            try {
                scanner = tableToUse.getScanner(scan);

                Iterator<Result> resultsIter = scanner.iterator();
                long start = System.nanoTime();
                long recordsToSkip = ((long) limit) * (page - 1);
                long count = 0;

                while (resultsIter.hasNext()) {
                    Result result = resultsIter.next();

                    count++;
                    if (count <= recordsToSkip) {
                        continue;
                    }

                    String rowKey = Bytes.toString(result.getRow());
                    MetricSchemaRecord record = _constructMetricSchemaRecord(rowKey, metadata.type);

                    records.add(record);
                    if (records.size() == limit) {
                        break;
                    }
                }
                _logger.debug("Rows iterated: " + count);
                _logger.debug("Time to iterate in ms: " + (System.nanoTime() - start) / 1000000);
            } catch (IOException ioe) {
                throw new SystemException("Failed to scan metric schema results.", ioe);
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
        } catch (IOException e) {
            throw new SystemException("Failed to create HTable instance.", e);
        } finally {
            _closeTables(tableToUse);
        } // end try-catch-finally
        return records;
    }

    @Override
    public List<String> getUnique(MetricSchemaRecordQuery query, int limit, int page, RecordType type) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric Schema Record query cannot be null.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");
        SystemAssert.requireArgument(page > 0, "Page must be a positive integer.");

        Set<String> records = new TreeSet<String>();
        Set<String> skip = new HashSet<String>();
        HTableInterface tableToUse = null;

        try {
            ScanMetadata metadata = _constructScanMetadata(query);
            String namespace = _convertToRegex(query.getNamespace());
            String scope = _convertToRegex(query.getScope());
            String metric = _convertToRegex(query.getMetric());
            String tagKey = _convertToRegex(query.getTagKey());
            String tagValue = _convertToRegex(query.getTagValue());
            String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.type) + "$";

            tableToUse = _getHbaseConnection().getTable(metadata.type.getTableName());
            _logger.debug("Using table: " + metadata.type.getTableName());
            _logger.debug("Rowkey: " + rowKeyRegex);
            _logger.debug("Scan startRow: " + Bytes.toString(metadata.startRow));
            _logger.debug("Scan stopRow: " + Bytes.toString(metadata.stopRow));

            Filter rowFilter = new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex));
            FilterList fl = new FilterList(FilterList.Operator.MUST_PASS_ALL, new KeyOnlyFilter(), new FirstKeyOnlyFilter(), rowFilter);
            Scan scan = new Scan();

            scan.setStartRow(metadata.startRow);
            scan.setStopRow(metadata.stopRow);
            scan.setFilter(fl);
            scan.setCaching(10000);

            ResultScanner scanner = null;

            try {
                scanner = tableToUse.getScanner(scan);

                Iterator<Result> resultsIter = scanner.iterator();
                long start = System.nanoTime();
                long recordsToSkip = ((long) limit) * (page - 1);
                long count = 0;

                while (resultsIter.hasNext()) {
                    Result result = resultsIter.next();

                    count++;

                    String rowKey = Bytes.toString(result.getRow());
                    MetricSchemaRecord record = _constructMetricSchemaRecord(rowKey, metadata.type);

                    if (skip.size() < recordsToSkip) {
                        skip.add(_getValueForType(record, type));
                        continue;
                    }
                    if (records.isEmpty() && !skip.contains(_getValueForType(record, type))) {
                        records.add(_getValueForType(record, type));
                    } else {
                        records.add(_getValueForType(record, type));
                    }
                    if (records.size() == limit) {
                        break;
                    }
                }
                _logger.debug("Rows iterated: " + count);
                _logger.debug("Time to iterate in ms: " + (System.nanoTime() - start) / 1000000);
            } catch (IOException ioe) {
                throw new SystemException("Failed to scan metric schema results.", ioe);
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
        } catch (IOException ioe) {
            throw new SystemException("Failed to create HTable instance.", ioe);
        } finally {
            _closeTables(tableToUse);
        } // end try-catch-finally
        return new ArrayList<String>(records);
    }

    @Override
    public void dispose() {
        super.dispose();
        try {
            if (_connection != null) {
                _connection.close();
            }
        } catch (IOException ioe) {
            _logger.warn("Failed to dispose HConnection instance", ioe);
        }
    }
    
    private synchronized HConnection _getHbaseConnection() {
        if(_connection == null) {
            _connection = _createHConnection(_config);            
        }
        return _connection;
    }

    private String _getValueForType(MetricSchemaRecord record, RecordType type) {
        switch (type) {
            case NAMESPACE:
                return record.getNamespace();
            case SCOPE:
                return record.getScope();
            case METRIC:
                return record.getMetric();
            case TAGK:
                return record.getTagKey();
            case TAGV:
                return record.getTagValue();
            default:
                return null;
        }
    }

    /**
     * Construct scan metadata depending on the query. This includes determining the table to query and the start and stop rows for the scan.
     *
     * <p>For e.g., if scope == "system.chi*" and metric = "app_runtime" then the 2 row keys will be,</p>
     *
     * <p>scopeRowKey = system.chi*:app_runtime:tagk:tagv:namespace metricRowKey = app_runtime:system.chi*:tagk:tagv:namespace</p>
     *
     * <p>Based on these 2 rowkeys we will select, tableType = METRIC startRow = "app_runtime:system.chi" and stopRow = "app_runtime:system.chj"</p>
     *
     * @param   query  The metric schema query.
     *
     * @return  A metadata object that contains information about the table to use for querying data, and the start and stop rows for our scan.
     */
    private ScanMetadata _constructScanMetadata(MetricSchemaRecordQuery query) {
        ScanMetadata metadata = new ScanMetadata();
        char[] scopeTableRowKey = _constructRowKey(query.getNamespace(), query.getScope(), query.getMetric(), query.getTagKey(), query.getTagValue(),
            TableType.SCOPE).toCharArray();
        char[] metricTableRowKey = _constructRowKey(query.getNamespace(), query.getScope(), query.getMetric(), query.getTagKey(), query.getTagValue(),
            TableType.METRIC).toCharArray();

        // Find first occurrence of any wildcard character in both rowKeys.
        // Everything until this character will represent our entry point into the table.
        // We will therefore use the corresponding table where the index of wildcard in the rowKey is higher.
        int i = 0, j = 0;

        for (; (i < scopeTableRowKey.length && j < metricTableRowKey.length); i++, j++) {
            if (_isWildcardCharacter(scopeTableRowKey[i]) || _isWildcardCharacter(metricTableRowKey[j])) {
                break;
            }
        }
        while (i < scopeTableRowKey.length && !_isWildcardCharacter(scopeTableRowKey[i])) {
            i++;
        }
        while (j < metricTableRowKey.length && !_isWildcardCharacter(metricTableRowKey[j])) {
            j++;
        }

        // If the first wildcard character is OR, then we have to backtrack until the last ROW_SEPARATOR occurence.
        if (i < scopeTableRowKey.length && scopeTableRowKey[i] == '|') {
            while (i >= 0 && scopeTableRowKey[i] != ROWKEY_SEPARATOR) {
                i--;
            }
            i++;
        }
        if (j < metricTableRowKey.length && metricTableRowKey[j] == '|') {
            while (j >= 0 && metricTableRowKey[j] != ROWKEY_SEPARATOR) {
                j--;
            }
            j++;
        }

        int indexOfWildcard;
        String rowKey;

        if (i < j) {
            metadata.type = TableType.METRIC;
            indexOfWildcard = j;
            rowKey = new String(metricTableRowKey);
        } else {
            metadata.type = TableType.SCOPE;
            indexOfWildcard = i;
            rowKey = new String(scopeTableRowKey);
        }

        String start = rowKey.substring(0, indexOfWildcard);

        metadata.startRow = start.getBytes(Charset.forName("UTF-8"));

        String end = "";

        if (indexOfWildcard > 0) {
            // Also determine the character before the wildcard and increment it by 1.
            // This will represent the stopping condition for our scan.
            char prev = rowKey.charAt(indexOfWildcard - 1);
            char prevPlusOne = (char) (prev + 1);

            end = rowKey.substring(0, indexOfWildcard - 1) + prevPlusOne;
        }
        metadata.stopRow = end.getBytes(Charset.forName("UTF-8"));
        return metadata;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Enumerates the schema table types.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum TableType {

        SCOPE("scope-schema"),
        METRIC("metric-schema");

        private String _tableName;

        private TableType(String tableName) {
            _tableName = tableName;
        }

        /**
         * Returns the table type for the given table name.
         *
         * @param   tableName  The table name.  Cannot be null.
         *
         * @return  The corresponding table name.
         *
         * @throws  SystemException  If no table type exists for the given name.
         */
        public static TableType fromTableName(String tableName) {
            for (TableType type : TableType.values()) {
                if (type.getTableName().equals(tableName)) {
                    return type;
                }
            }
            throw new SystemException(new IllegalArgumentException("Unknown table name: " + tableName));
        }

        /**
         * Returns the table name.
         *
         * @return  The table name.
         */
        public String getTableName() {
            return this._tableName;
        }
    }

    /**
     * The set of implementation specific configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        HBASE_ZOOKEEPER_CONNECT("service.property.schema.hbase.zookeeper.connect", "localhost:2181");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default value for the property.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Represents the scan meta data.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    static class ScanMetadata {

        /** The start row. */
        public byte[] startRow = new byte[0];

        /** The end row. */
        public byte[] stopRow = new byte[0];

        /** The table type. */
        public TableType type = TableType.SCOPE;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
