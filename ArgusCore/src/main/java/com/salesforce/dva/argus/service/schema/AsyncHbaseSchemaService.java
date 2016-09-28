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
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import com.stumbleupon.async.TimeoutException;

import org.apache.hadoop.hbase.util.Bytes;
import org.hbase.async.CompareFilter.CompareOp;
import org.hbase.async.Config;
import org.hbase.async.FilterList;
import org.hbase.async.FirstKeyOnlyFilter;
import org.hbase.async.HBaseClient;
import org.hbase.async.KeyOnlyFilter;
import org.hbase.async.KeyValue;
import org.hbase.async.PutRequest;
import org.hbase.async.RegexStringComparator;
import org.hbase.async.RowFilter;
import org.hbase.async.ScanFilter;
import org.hbase.async.Scanner;
import org.slf4j.Logger;

import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of the schema service using Asynchbase.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class AsyncHbaseSchemaService extends DefaultService implements SchemaService {

    //~ Static fields/initializers *******************************************************************************************************************

	private static final String METRIC_SCHEMA_TABLENAME = "metric-schema";
	private static final String SCOPE_SCHEMA_TABLENAME = "scope-schema";
    private static final byte[] COLUMN_FAMILY = "f".getBytes(Charset.forName("UTF-8"));
    private static final byte[] COLUMN_QUALIFIER = "c".getBytes(Charset.forName("UTF-8"));
    private static final byte[] CELL_VALUE = "1".getBytes(Charset.forName("UTF-8"));
    private static final char ROWKEY_SEPARATOR = ':';
    private static final char[] WILDCARD_CHARSET = new char[] { '*', '?', '[', ']', '|' };
    
    private static final long TIMEOUT = 2 * 60 * 1000;

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final HBaseClient _client;
    private final boolean _syncPut; 

    //~ Constructors *********************************************************************************************************************************

    @Inject
    private AsyncHbaseSchemaService(SystemConfiguration systemConfig) {
    	super(systemConfig);
        
    	_syncPut = Boolean.getBoolean(systemConfig.getValue(Property.HBASE_SYNC_PUT.getName(), Property.HBASE_SYNC_PUT.getDefaultValue()));
    	
    	Config config = new Config();
        
    	config.overrideConfig("hbase.zookeeper.quorum",
                systemConfig.getValue(Property.HBASE_ZOOKEEPER_CONNECT.getName(), Property.HBASE_ZOOKEEPER_CONNECT.getDefaultValue()));
    	config.overrideConfig("hbase.zookeeper.session.timeout",
        		systemConfig.getValue(Property.HBASE_ZOOKEEPER_SESSION_TIMEOUT.getName(), Property.HBASE_ZOOKEEPER_SESSION_TIMEOUT.getDefaultValue()));
    	
    	config.overrideConfig("hbase.rpcs.batch.size",
    			systemConfig.getValue(Property.HBASE_RPCS_BATCH_SIZE.getName(), Property.HBASE_RPCS_BATCH_SIZE.getDefaultValue()));
        config.overrideConfig("hbase.rpcs.buffered_flush_interval",
        		systemConfig.getValue(Property.HBASE_RPCS_BUFFERED_FLUSH_INTERVAL.getName(), Property.HBASE_RPCS_BUFFERED_FLUSH_INTERVAL.getDefaultValue()));
        config.overrideConfig("hbase.rpc.timeout",
    			systemConfig.getValue(Property.HBASE_RPC_TIMEOUT.getName(), Property.HBASE_RPC_TIMEOUT.getDefaultValue()));
        
        config.overrideConfig("hbase.security.auth.enable",
                systemConfig.getValue(Property.HBASE_SECURITY_AUTH_ENABLE.getName(), Property.HBASE_SECURITY_AUTH_ENABLE.getDefaultValue()));
        config.overrideConfig("hbase.rpc.protection", 
        		systemConfig.getValue(Property.HBASE_RPC_PROTECTION.getName(), Property.HBASE_RPC_PROTECTION.getDefaultValue()));
        config.overrideConfig("hbase.sasl.clientconfig",
        		systemConfig.getValue(Property.HBASE_SASL_CLIENTCONFIG.getName(), Property.HBASE_SASL_CLIENTCONFIG.getDefaultValue()));
        config.overrideConfig("hbase.kerberos.regionserver.principal",
        		systemConfig.getValue(Property.HBASE_KERBEROS_REGIONSERVER_PRINCIPAL.getName(), Property.HBASE_KERBEROS_REGIONSERVER_PRINCIPAL.getDefaultValue()));
        config.overrideConfig("hbase.security.authentication", 
        		systemConfig.getValue(Property.HBASE_SECURITY_AUTHENTICATION.getName(), Property.HBASE_SECURITY_AUTHENTICATION.getDefaultValue()));
        
        _client = new HBaseClient(config);
        
        _ensureTableWithColumnFamilyExists(Bytes.toBytes(SCOPE_SCHEMA_TABLENAME), COLUMN_FAMILY);
        _ensureTableWithColumnFamilyExists(Bytes.toBytes(METRIC_SCHEMA_TABLENAME), COLUMN_FAMILY);
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
        
        for (Metric metric : metrics) {
            if (metric.getTags().isEmpty()) {
                _putWithoutTag(metric, SCOPE_SCHEMA_TABLENAME);
                _putWithoutTag(metric, METRIC_SCHEMA_TABLENAME);
            }
            
            for (Entry<String, String> tag : metric.getTags().entrySet()) {
                _putWithTag(metric, tag, SCOPE_SCHEMA_TABLENAME);
                _putWithTag(metric, tag, METRIC_SCHEMA_TABLENAME);
            }  
        } 
    }

    @Override
    public List<MetricSchemaRecord> get(MetricSchemaRecordQuery query, final int limit, final int page) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric Schema Record query cannot be null.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");
        SystemAssert.requireArgument(page > 0, "Page must be a positive integer.");

        final List<MetricSchemaRecord> records = new ArrayList<MetricSchemaRecord>(limit);
        final ScanMetadata metadata = _constructScanMetadata(query);
        String namespace = _convertToRegex(query.getNamespace());
        String scope = _convertToRegex(query.getScope());
        String metric = _convertToRegex(query.getMetric());
        String tagKey = _convertToRegex(query.getTagKey());
        String tagValue = _convertToRegex(query.getTagValue());
        String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.type) + "$";

        _logger.debug("Using table: " + metadata.type.getTableName());
        _logger.debug("Rowkey: " + rowKeyRegex);
        _logger.debug("Scan startRow: " + Bytes.toString(metadata.startRow));
        _logger.debug("Scan stopRow: " + Bytes.toString(metadata.stopRow));

        List<ScanFilter> filters = new ArrayList<ScanFilter>();

        filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex)));
        filters.add(new KeyOnlyFilter());
        filters.add(new FirstKeyOnlyFilter());

        FilterList fl = new FilterList(filters, FilterList.Operator.MUST_PASS_ALL);
        final Scanner scanner = _client.newScanner(metadata.type.getTableName());

        scanner.setStartKey(metadata.startRow);
        scanner.setStopKey(metadata.stopRow);
        scanner.setFilter(fl);
        scanner.setMaxNumRows(Math.min((limit * page), 10000));

        final Deferred<List<MetricSchemaRecord>> results = new Deferred<List<MetricSchemaRecord>>();

        /**
         * Scans HBASE rows.
         *
         * @author  Bhinav Sura (bhinav.sura@salesforce.com)
         */
        final class ScannerCB implements Callback<Object, ArrayList<ArrayList<KeyValue>>> {

            int recordsToSkip = limit * (page - 1);

            /**
             * Scans rows.
             *
             * @return  The list of metric schema records.
             */
            public Object scan() {
            	_logger.debug("Getting next set of rows.");
                return scanner.nextRows().addCallback(this);
            }

            @Override
            public Object call(ArrayList<ArrayList<KeyValue>> rows) throws Exception {
            	_logger.debug("Inside nextRows() callback..");
                try {
                    if (rows == null) {
                        results.callback(records);
                        scanner.close();
                        return null;
                    }
                    
                    _logger.debug("Retrieved " + rows.size() + " rows.");
                    if (recordsToSkip >= rows.size()) {
                        recordsToSkip -= rows.size();
                    } else {
                        for (int i = recordsToSkip; i < rows.size(); i++) {
                            ArrayList<KeyValue> row = rows.get(i);
                            byte[] rowkey = row.get(0).key();
                            MetricSchemaRecord record = _constructMetricSchemaRecord(Bytes.toString(rowkey), metadata.type);

                            records.add(record);
                            if (records.size() == limit) {
                                results.callback(records);
                                scanner.close();
                                return null;
                            }
                        }
                    }
                    return scan();
                } catch (Exception e) {
                    scanner.close();
                    return null;
                }
            }
        }
        
        new ScannerCB().scan();
        
        try {
			return results.join(TIMEOUT);
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for query: " + query, e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results for query: {}. Will return an empty list.", query);
			return Collections.emptyList();
		} catch (Exception e) {
			throw new SystemException("Exception occured in getting results for query: " + query, e);
		}

    }

    @Override
    public List<String> getUnique(MetricSchemaRecordQuery query, final int limit, final int page, final RecordType type) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric Schema Record query cannot be null.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");
        SystemAssert.requireArgument(page > 0, "Page must be a positive integer.");

        final Set<String> records = new TreeSet<String>();
        final Set<String> skip = new HashSet<String>();
        final ScanMetadata metadata = _constructScanMetadata(query);
        String namespace = _convertToRegex(query.getNamespace());
        String scope = _convertToRegex(query.getScope());
        String metric = _convertToRegex(query.getMetric());
        String tagKey = _convertToRegex(query.getTagKey());
        String tagValue = _convertToRegex(query.getTagValue());
        String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.type) + "$";

        _logger.debug("Using table: " + metadata.type.getTableName());
        _logger.debug("Rowkey: " + rowKeyRegex);
        _logger.debug("Scan startRow: " + Bytes.toString(metadata.startRow));
        _logger.debug("Scan stopRow: " + Bytes.toString(metadata.stopRow));

        List<ScanFilter> filters = new ArrayList<ScanFilter>();

        filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex)));
        filters.add(new KeyOnlyFilter());
        filters.add(new FirstKeyOnlyFilter());

        FilterList fl = new FilterList(filters, FilterList.Operator.MUST_PASS_ALL);
        final Scanner scanner = _client.newScanner(metadata.type.getTableName());

        scanner.setStartKey(metadata.startRow);
        scanner.setStopKey(metadata.stopRow);
        scanner.setFilter(fl);
        scanner.setMaxNumRows(10000);

        final Deferred<Set<String>> results = new Deferred<Set<String>>();

        /**
         * Scans HBASE rows.
         *
         * @author  Tom Valine (tvaline@salesforce.com)
         */
        final class ScannerCB implements Callback<Object, ArrayList<ArrayList<KeyValue>>> {

            int recordsToSkip = limit * (page - 1);

            /**
             * Scans rows.
             *
             * @return  The list of metric schema records.
             */
            public Object scan() {
                return scanner.nextRows().addCallback(this);
            }

            @Override
            public Object call(ArrayList<ArrayList<KeyValue>> rows) throws Exception {
                try {
                    if (rows == null) {
                        results.callback(records);
                        scanner.close();
                        return null;
                    }
                    for (ArrayList<KeyValue> row : rows) {
                        String rowKey = Bytes.toString(row.get(0).key());
                        MetricSchemaRecord record = _constructMetricSchemaRecord(rowKey, metadata.type);

                        if (skip.size() < recordsToSkip) {
                            skip.add(_getValueForType(record, type));
                            continue;
                        }
                        if (records.isEmpty() && skip.contains(record)) {
                            continue;
                        }
                        records.add(_getValueForType(record, type));
                        if (records.size() == limit) {
                            results.callback(records);
                            scanner.close();
                            return null;
                        }
                    }
                    return scan();
                } catch (Exception e) {
                    scanner.close();
                    return null;
                }
            }
        }
        
        new ScannerCB().scan();
        
        try {
			return new ArrayList<>(results.join(TIMEOUT));
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for query: " + query, e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results for query: {}. Will return an empty list.", query);
			return Collections.emptyList();
		} catch (Exception e) {
			throw new SystemException("Exception occured in getting results for query: " + query, e);
		}
        
    }

    @Override
    public void dispose() {
        super.dispose();
        if (_client != null) {
            _logger.info("Shutting down asynchbase client.");

            Deferred<Object> deferred = _client.shutdown();

            deferred.addCallback(new Callback<Void, Object>() {
                @Override
                public Void call(Object arg) throws Exception {
                    _logger.info("Shutdown of asynchbase client complete.");
                    return null;
                }
            });
            
            deferred.addErrback(new Callback<Void, Exception>() {
                @Override
                public Void call(Exception arg) throws Exception {
                    _logger.warn("Error occured while shutting down asynchbase client.");
                    return null;
                }
            });
            
            try {
                deferred.join();
            } catch (Exception e) {
                throw new SystemException("Exception while waiting for shutdown to complete.", e);
            }
        }
    }
    
    
    private void _ensureTableWithColumnFamilyExists(byte[] table, byte[] family) {
        final AtomicBoolean fail = new AtomicBoolean(true);
        
        Deferred<Object> deferred = _client.ensureTableFamilyExists(table, family);
        
        deferred.addCallback(new Callback<Void, Object>() {
			@Override
			public Void call(Object arg) throws Exception {
				fail.set(false);
				return null;
			}
		});
        
        deferred.addErrback(new Callback<Void, Exception>() {
			@Override
			public Void call(Exception arg) throws Exception {
				_logger.error("Table {} or family {} does not exist. Please create the appropriate table.", Bytes.toString(table), Bytes.toString(family));
				return null;
			}
		});
        
        try {
        	deferred.join(TIMEOUT);
        } catch (InterruptedException e) {
        	throw new SystemException("Interrupted while waiting to ensure schema tables exist.", e);
        } catch (Exception e) {
			_logger.error("Exception occured while waiting to ensure table {} with family {} exists", Bytes.toString(table), Bytes.toString(family));
		}
        
	}

    private void _putWithoutTag(Metric metric, String tableName) {
    	String rowKeyStr = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), 
    			null, null, TableType.fromTableName(tableName));
    	_put(tableName, rowKeyStr);
    }
    
    private void _putWithTag(Metric metric, Entry<String, String> tag, String tableName) {
    	String rowKeyStr = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), tag.getKey(), 
    			tag.getValue(), TableType.fromTableName(tableName));
    	_put(tableName, rowKeyStr);
    }

	private void _put(String tableName, String rowKeyStr) {
		_logger.trace(MessageFormat.format("Inserting rowkey {0} into table {1}", rowKeyStr, tableName));

        final PutRequest put = new PutRequest(Bytes.toBytes(tableName), Bytes.toBytes(rowKeyStr), COLUMN_FAMILY, COLUMN_QUALIFIER, CELL_VALUE);
        Deferred<Object> deferred = _client.put(put);
        
        if(_syncPut) {
        	deferred.addCallback(new Callback<Object, Object>() {
    			@Override
    			public Object call(Object arg) throws Exception {
    				_logger.trace(MessageFormat.format("Put to {0} successfully.", tableName));
    				return null;
    			}
            });
        }

        deferred.addErrback(new Callback<Object, Exception>() {
            @Override
            public Object call(Exception e) throws Exception {
                throw new SystemException("Error occured while trying to execute put().", e);
            }
        });
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

        SCOPE(SCOPE_SCHEMA_TABLENAME),
        METRIC(METRIC_SCHEMA_TABLENAME);

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
    	
        HBASE_ZOOKEEPER_CONNECT("service.property.schema.hbase.zookeeper.connect", "hbase.zookeeper.com:1234"),
        HBASE_ZOOKEEPER_SESSION_TIMEOUT("service.property.schema.hbase.zookeeper.session.timeout", "6000"),
        
        HBASE_SECURITY_AUTHENTICATION("service.property.schema.hbase.security.authentication", ""),
        HBASE_RPC_PROTECTION("service.property.schema.hbase.rpc.protection", ""),
        HBASE_SASL_CLIENTCONFIG("service.property.schema.hbase.sasl.clientconfig", "Client"),
        HBASE_SECURITY_AUTH_ENABLE("service.property.schema.hbase.security.auth.enable", "false"),
        HBASE_KERBEROS_REGIONSERVER_PRINCIPAL("service.property.schema.hbase.kerberos.regionserver.principal", ""),
        
        HBASE_RPCS_BATCH_SIZE("service.property.schema.hbase.rpcs.batch.size", "16192"),
        HBASE_RPCS_BUFFERED_FLUSH_INTERVAL("service.property.schema.hbase.rpcs.buffered_flush_interval", "5000"),
        HBASE_RPC_TIMEOUT("service.property.schema.hbase.rpc.timeout", "0"),
        
        HBASE_SYNC_PUT("service.property.schema.hbase.sync.put", "false");

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
