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
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.service.AsyncHBaseClientFactory;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import com.stumbleupon.async.TimeoutException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.hbase.util.Bytes;
import org.hbase.async.CompareFilter.CompareOp;
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
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.Map;


/**
 * Implementation of the schema service using Asynchbase.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class AsyncHbaseSchemaService extends AbstractSchemaService {

    //~ Static fields/initializers *******************************************************************************************************************

	private static String METRIC_SCHEMA_TABLENAME;
	private static String SCOPE_SCHEMA_TABLENAME ;
    private static final byte[] COLUMN_FAMILY = "f".getBytes(Charset.forName("UTF-8"));
    private static final byte[] COLUMN_QUALIFIER = "c".getBytes(Charset.forName("UTF-8"));
    private static final byte[] CELL_VALUE = new byte[1];
    private static final char ROWKEY_SEPARATOR = '\u0000';
    private static final char PLACEHOLDER_FOR_NULL_STRINGS = '\u0023';

    private static final long TIMEOUT_MS = 30 * 1000;
    private static final long SCAN_TIMEOUT_MS = 2 * 60 * 1000;

    //~ Instance fields ******************************************************************************************************************************

    private Logger _logger = LoggerFactory.getLogger(AsyncHbaseSchemaService.class);
    private final HBaseClient _client;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    private AsyncHbaseSchemaService(SystemConfiguration systemConfig, AsyncHBaseClientFactory factory, MonitorService monitorService) {
    	super(systemConfig, monitorService);

    	METRIC_SCHEMA_TABLENAME = systemConfig.getValue(Property.HBASE_METRICSCHEMA_TABLE.getName(),
    													Property.HBASE_METRICSCHEMA_TABLE.getDefaultValue());
    	SCOPE_SCHEMA_TABLENAME = systemConfig.getValue(Property.HBASE_SCOPESCHEMA_TABLE.getName(),
    												   Property.HBASE_SCOPESCHEMA_TABLE.getDefaultValue());

    	_client = factory.getClient();
    }

    //~ Methods **************************************************************************************************************************************

    private static String _constructRowKey(String namespace, String scope, String metric, String tagKey, String tagValue, String tableName) {
        namespace = namespace == null ? Character.toString(PLACEHOLDER_FOR_NULL_STRINGS) : namespace;
        tagKey = tagKey == null ? Character.toString(PLACEHOLDER_FOR_NULL_STRINGS) : tagKey;
        tagValue = tagValue == null ? Character.toString(PLACEHOLDER_FOR_NULL_STRINGS) : tagValue;

        String key;

        if (SCOPE_SCHEMA_TABLENAME.equals(tableName)) {
            key = MessageFormat.format("{0}{5}{1}{5}{2}{5}{3}{5}{4}", scope, metric, tagKey, tagValue, namespace, ROWKEY_SEPARATOR);
        } else if (METRIC_SCHEMA_TABLENAME.equals(tableName)) {
            key = MessageFormat.format("{0}{5}{1}{5}{2}{5}{3}{5}{4}", metric, scope, tagKey, tagValue, namespace, ROWKEY_SEPARATOR);
        } else {
            throw new SystemException(new IllegalArgumentException("Unknown table: " + tableName));
        }
        return key;
    }

    private static String _constructRowKey(MetricSchemaRecord schema, String tableName){
    	return _constructRowKey(schema.getNamespace(), schema.getScope(), schema.getMetric(), schema.getTagKey(), schema.getTagValue(), tableName);
    }

    private static MetricSchemaRecord _constructMetricSchemaRecord(String rowKey, String tableName) {
        SystemAssert.requireArgument(rowKey != null && !rowKey.isEmpty(), "This should never happen. Rowkey should never be null or empty.");

        String[] parts = rowKey.split(String.valueOf(ROWKEY_SEPARATOR));
        MetricSchemaRecord record;

        if (SCOPE_SCHEMA_TABLENAME.equals(tableName)) {
            record = new MetricSchemaRecord(parts[0], parts[1]);
        } else if (METRIC_SCHEMA_TABLENAME.equals(tableName)) {
            record = new MetricSchemaRecord(parts[1], parts[0]);
        } else {
            throw new SystemException(new IllegalArgumentException("Unknown table: " + tableName));
        }

        String placeholder = Character.toString(PLACEHOLDER_FOR_NULL_STRINGS);
        if (!placeholder.equals(parts[2])) {
            record.setTagKey(parts[2]);
        }
        if (!placeholder.equals(parts[3])) {
            record.setTagValue(parts[3]);
        }
        if (!placeholder.equals(parts[4])) {
            record.setNamespace(parts[4]);
        }
        return record;
    }

    private  String _plusOneNConstructRowKey(MetricSchemaRecord record, String tableName, RecordType type){
    	if(type==null){
    		return  _plusOne(_constructRowKey(record, tableName));
    	}else{
    		switch (type) {
            case NAMESPACE:
                 record.setNamespace(_plusOne(record.getNamespace()));
                 break;
            case SCOPE:
                record.setScope(_plusOne(record.getScope()));
                break;
            case METRIC:
                record.setMetric(_plusOne(record.getMetric()));
                break;
            case TAGK:
                record.setTagKey(_plusOne(record.getTagKey()));
                break;
            case TAGV:
                record.setTagValue(_plusOne(record.getTagValue()));
    		}
    	}
    	return _constructRowKey(record, tableName);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    protected void implementationSpecificPut(List<Metric> metrics,
                                             Set<String> scopeNames,
                                             Set<Pair<String, String>> scopesAndMetricNames,
                                             Map<String, MetatagsRecord> metatagsToPut) {
        requireNotDisposed();
        SystemAssert.requireArgument(metrics != null, "Metric list cannot be null.");

        for (Metric metric : metrics) {
            if (metric.getTags().isEmpty()) {
                _putWithoutTag(metric);
            }

            for (Entry<String, String> tag : metric.getTags().entrySet()) {
                _putWithTag(metric, tag);
            }
        }
    }

    @Override
    public List<MetricSchemaRecord> get(final MetricSchemaRecordQuery query) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric Schema Record query cannot be null.");

        final List<MetricSchemaRecord> records = new ArrayList<MetricSchemaRecord>(query.getLimit());
        final ScanMetadata metadata = _constructScanMetadata(query);
        String namespace = SchemaService.convertToRegex(query.getNamespace());
        String scope = SchemaService.convertToRegex(query.getScope());
        String metric = SchemaService.convertToRegex(query.getMetric());
        String tagKey = SchemaService.convertToRegex(query.getTagKey());
        String tagValue = SchemaService.convertToRegex(query.getTagValue());
        MetricSchemaRecord scanFrom = query.getScanFrom();

        String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.tableName) + "$";

        String scanStartRow = scanFrom == null ? Bytes.toString(metadata.startRow)
        									   : _plusOneNConstructRowKey(scanFrom, metadata.tableName, null);

        _logger.info("Using table: " + metadata.tableName);
        _logger.info("Rowkey: " + rowKeyRegex);

        _logger.debug("Scan startRow: " + scanStartRow);
        _logger.debug("Scan stopRow: " + metadata.stopRow.toString());

        List<ScanFilter> filters = new ArrayList<ScanFilter>();

        filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex)));
        filters.add(new KeyOnlyFilter());
        filters.add(new FirstKeyOnlyFilter());

        FilterList fl = new FilterList(filters, FilterList.Operator.MUST_PASS_ALL);
        final Scanner scanner = _client.newScanner(metadata.tableName);

        scanner.setStartKey(scanStartRow.getBytes());
        scanner.setStopKey(metadata.stopRow);
        scanner.setFilter(fl);
        scanner.setMaxNumRows(Math.min(query.getLimit(), 10000));

        final Deferred<List<MetricSchemaRecord>> results = new Deferred<List<MetricSchemaRecord>>();

        /**
         * Scans HBASE rows.
         *
         * @author  Bhinav Sura (bhinav.sura@salesforce.com)
         */
        final class ScannerCB implements Callback<Object, ArrayList<ArrayList<KeyValue>>> {


            /**
             * Scans rows.
             *
             * @return  The list of metric schema records.
             */
            public Object scan() {
            	_logger.trace("Getting next set of rows.");
                return scanner.nextRows().addCallback(this);
            }

            @Override
            public Object call(ArrayList<ArrayList<KeyValue>> rows) throws Exception {
            	_logger.trace("Inside nextRows() callback..");
                try {
                    if (rows == null) {
                        results.callback(records);
                        scanner.close();
                        return null;
                    }

                    _logger.debug("Retrieved " + rows.size() + " rows.");

                    for(ArrayList<KeyValue> row:rows){
                    	byte[] rowKey=row.get(0).key();
                    	MetricSchemaRecord record = _constructMetricSchemaRecord(Bytes.toString(rowKey), metadata.tableName);
                    	records.add(record);
                        if (records.size() == query.getLimit()) {
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
			return results.join(SCAN_TIMEOUT_MS);
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for query: " + query, e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results for query: {}. Will return an empty list.", query);
			return Collections.emptyList();
		} catch (Exception e) {
			throw new SystemException("Exception occurred in getting results for query: " + query, e);
		}

    }

    /**
     * Fast scan works when trying to discover either scopes or metrics with all other fields being *.
     * In this case, when the first result is obtained, we skip all other rows starting with that prefix and directly move
     * on to the next possible value which is obtained value incremented by 1 Ascii character. If that value exists, then
     * it is returned, otherwise HBase returns the next possible value in lexicographical order.
     *
     * For e.g. suppose if we have the following rows in HBase:
     *
     * scope\0metric1\0$\0$\0$
     * scope\0metric2\0$\0$\0$
     * .
     * .
     * .
     * scope\0metric1000\0$\0$\0$
     * scopu\0metric1\0$\0$\0$
     * scopu\0metric2\0$\0$\0$
     * .
     * .
     * .
     * scopu\0metric1000\0$\0$\0$
     *
     * And our start row is "sco", then this method would first find "scope" and then jump the next 1000 rows
     * to start from the next possible value of scopf. Since nothing like scopf exists, HBase would directly
     * jump to scopu and return that.
     *
     */
    private List<MetricSchemaRecord> _getUniqueFastScan(MetricSchemaRecordQuery query, final RecordType type) {
    	requireNotDisposed();
    	SystemAssert.requireArgument(RecordType.METRIC.equals(type) || RecordType.SCOPE.equals(type),
    			"This method is only for use with metric or scope.");

    	_logger.info("Using FastScan. Will skip rows while scanning.");

    	final Set<MetricSchemaRecord> records = new HashSet<>();

    	final ScanMetadata metadata = _constructScanMetadata(query);
        String namespace = SchemaService.convertToRegex(query.getNamespace());
        String scope = SchemaService.convertToRegex(query.getScope());
        String metric = SchemaService.convertToRegex(query.getMetric());
        String tagKey = SchemaService.convertToRegex(query.getTagKey());
        String tagValue = SchemaService.convertToRegex(query.getTagValue());
        MetricSchemaRecord scanFrom = query.getScanFrom();

        String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.tableName) + "$";

    	List<ScanFilter> filters = new ArrayList<ScanFilter>();
    	filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex)));
        filters.add(new KeyOnlyFilter());
        filters.add(new FirstKeyOnlyFilter());
        FilterList filterList = new FilterList(filters, FilterList.Operator.MUST_PASS_ALL);


        String start = scanFrom == null ? Bytes.toString(metadata.startRow)
        								: _plusOneNConstructRowKey(scanFrom, metadata.tableName, type);

        String end = Bytes.toString(metadata.stopRow);
        ArrayList<ArrayList<KeyValue>> rows = _getSingleRow(start, end, filterList, metadata.tableName);
        while(rows != null && !rows.isEmpty()) {
        	String rowKey = Bytes.toString(rows.get(0).get(0).key());
        	String splits[] = rowKey.split(String.valueOf(ROWKEY_SEPARATOR));
        	String record = (RecordType.METRIC.equals(type) && metadata.tableName.equals(METRIC_SCHEMA_TABLENAME)) ||
        			(RecordType.SCOPE.equals(type) && metadata.tableName.equals(SCOPE_SCHEMA_TABLENAME)) ? splits[0] : splits[1];

		MetricSchemaRecord schemaRecord = RecordType.METRIC.equals(type) ?
				new MetricSchemaRecord(null, record) : new MetricSchemaRecord(record, null);
        	records.add(schemaRecord);
        	if(records.size() == query.getLimit()) {
    			break;
    		}

        	String newScanStart;
        	if(!SchemaService.containsFilter(query.getScope()) || !SchemaService.containsFilter(query.getMetric())) {
        		newScanStart = _plusOne(record);
        	} else {
        		newScanStart = _plusOne(splits[0] + ROWKEY_SEPARATOR + splits[1]);
        	}
        	rows = _getSingleRow(newScanStart, end, filterList, metadata.tableName);
        }

    	return new ArrayList<>(records);
    }


	private ArrayList<ArrayList<KeyValue>> _getSingleRow(final String start, final String end,
			final FilterList filterList, final String tableName) {
		final Scanner scanner = _client.newScanner(tableName);
    	scanner.setStartKey(start);
    	scanner.setStopKey(end);
    	scanner.setMaxNumRows(1);
    	scanner.setFilter(filterList);

    	_logger.debug("Using table: " + tableName);
        _logger.debug("Scan startRow: " + start);
        _logger.debug("Scan stopRow: " + end);

        Deferred<ArrayList<ArrayList<KeyValue>>> deferred = scanner.nextRows();

        deferred.addCallback(new Callback<ArrayList<ArrayList<KeyValue>>, ArrayList<ArrayList<KeyValue>>>() {

			@Override
			public ArrayList<ArrayList<KeyValue>> call(ArrayList<ArrayList<KeyValue>> rows) throws Exception {
				scanner.close();
				return rows;
			}
		});

        try {
			ArrayList<ArrayList<KeyValue>> result = deferred.join(SCAN_TIMEOUT_MS);
			return result;
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for query", e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results.");
		} catch (Exception e) {
			throw new SystemException("Exception occurred in getting results for query", e);
		}
		return null;
	}

	//TSDB allowed characteers are: [A-Za-z0-9./-_]. The lowest ASCII value (45) out of these is for hyphen (-).
	private String _plusOne(String prefix) {
		char newChar = 45;
		return prefix + newChar;
	}

	/**
	 * Check if we can perform a faster scan. We can only perform a faster scan when we are trying to discover scopes or metrics
	 * without having information on any other fields.
	 */
	private boolean _canSkipWhileScanning(MetricSchemaRecordQuery query, RecordType type) {


		if( (RecordType.METRIC.equals(type) || RecordType.SCOPE.equals(type))
				&& !SchemaService.containsFilter(query.getTagKey())
				&& !SchemaService.containsFilter(query.getTagValue())
				&& !SchemaService.containsFilter(query.getNamespace())) {
			if(RecordType.METRIC.equals(type) && !SchemaService.containsFilter(query.getMetric())) {
				return false;
			}

			if(RecordType.SCOPE.equals(type) && !SchemaService.containsFilter(query.getScope())) {
				return false;
			}

			return true;
		}

		return false;
	}



    @Override
    public List<MetricSchemaRecord> getUnique(final MetricSchemaRecordQuery query, final RecordType type) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric Schema Record query cannot be null.");
        SystemAssert.requireArgument(type != null, "Record type cannot be null.");
        SystemAssert.requireArgument(!query.getScope().startsWith("*") || !query.getMetric().startsWith("*"), "Must specify at least some filtering criteria on either scope or metric name.");

        if(_canSkipWhileScanning(query, type)) {
        	return _getUniqueFastScan(query, type);
        }

        final Set<String> records = new TreeSet<String>();
        final ScanMetadata metadata = _constructScanMetadata(query);
        String namespace = SchemaService.convertToRegex(query.getNamespace());
        String scope = SchemaService.convertToRegex(query.getScope());
        String metric = SchemaService.convertToRegex(query.getMetric());
        String tagKey = SchemaService.convertToRegex(query.getTagKey());
        String tagValue = SchemaService.convertToRegex(query.getTagValue());
        MetricSchemaRecord scanFrom = query.getScanFrom();

        String rowKeyRegex = "^" + _constructRowKey(namespace, scope, metric, tagKey, tagValue, metadata.tableName) + "$";

        String scanStartRow = scanFrom == null ? Bytes.toString(metadata.startRow)
        									   : _plusOneNConstructRowKey(scanFrom, metadata.tableName, type);

        _logger.info("Using table: " + metadata.tableName);
        _logger.info("Rowkey: " + rowKeyRegex);
        _logger.debug("Scan startRow: " + scanStartRow);
        _logger.debug("Scan stopRow: " + Bytes.toString(metadata.stopRow));

        List<ScanFilter> filters = new ArrayList<ScanFilter>();

        filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex)));
        filters.add(new KeyOnlyFilter());
        filters.add(new FirstKeyOnlyFilter());

        FilterList filterList = new FilterList(filters, FilterList.Operator.MUST_PASS_ALL);
        final Scanner scanner = _client.newScanner(metadata.tableName);

        scanner.setStartKey(scanStartRow);
        scanner.setStopKey(metadata.stopRow);
        scanner.setFilter(filterList);
        scanner.setMaxNumRows(10000);

        final Deferred<List<MetricSchemaRecord>> results = new Deferred<List<MetricSchemaRecord>>();

        List<MetricSchemaRecord> listMetricSchemarecords = new ArrayList<>();


        final class ScannerCB implements Callback<Object, ArrayList<ArrayList<KeyValue>>> {

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
                        results.callback(listMetricSchemarecords);
                        scanner.close();
                        return null;
                    }
                    for (ArrayList<KeyValue> row : rows) {
                        String rowKey = Bytes.toString(row.get(0).key());
                        MetricSchemaRecord record = _constructMetricSchemaRecord(rowKey, metadata.tableName);

                        if(records.add(record.getStringValueForType(type))){
                        	listMetricSchemarecords.add(record);
                        }
                        if (records.size() == query.getLimit()) {
                            results.callback(listMetricSchemarecords);
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
			return new ArrayList<>(results.join(SCAN_TIMEOUT_MS));
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for query: " + query, e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results for query: {}. Will return an empty list.", query);
			return Collections.emptyList();
		} catch (Exception e) {
			throw new SystemException("Exception occurred in getting results for query: " + query, e);
		}

    }

    @Override
    public List<String> browseUnique(MetricSchemaRecordQuery query, RecordType type, int indexLevel) {
        throw new UnsupportedOperationException("browse Unique is not supported by AsyncHbaseSchemaService. "
                + "Please use ElasticSearchSchemaService. ");
    }

    @Override
	public List<MetricSchemaRecord> keywordSearch(KeywordQuery query) {
		throw new UnsupportedOperationException("Keyword search is not supported by AsyncHbaseSchemaService. "
				+ "Please use ElasticSearchSchemaService. ");
	}

	@Override
	public Properties getServiceProperties() {
		Properties serviceProps = new Properties();

		for (Property property : Property.values()) {
			serviceProps.put(property.getName(), property.getDefaultValue());
		}
		return serviceProps;
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
                    _logger.warn("Error occurred while shutting down asynchbase client.");
                    return null;
                }
            });

            try {
                deferred.join(TIMEOUT_MS);
            } catch (Exception e) {
                throw new SystemException("Exception while waiting for shutdown to complete.", e);
            }
        }
    }

    private void _putWithoutTag(Metric metric) {
    	String rowKeyScopeTable = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(),
    			null, null, SCOPE_SCHEMA_TABLENAME);
    	String rowKeyMetricTable = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(),
    			null, null, METRIC_SCHEMA_TABLENAME);

    	_put(SCOPE_SCHEMA_TABLENAME, rowKeyScopeTable);
		_put(METRIC_SCHEMA_TABLENAME, rowKeyMetricTable);
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITTEN, 2, null);
    }

    private void _putWithTag(Metric metric, Entry<String, String> tag) {
    	String rowKeyScopeTable = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), tag.getKey(),
    			tag.getValue(), SCOPE_SCHEMA_TABLENAME);
    	String rowKeyMetricTable = _constructRowKey(metric.getNamespace(), metric.getScope(), metric.getMetric(), tag.getKey(),
    			tag.getValue(), METRIC_SCHEMA_TABLENAME);

    	_put(SCOPE_SCHEMA_TABLENAME, rowKeyScopeTable);
		_put(METRIC_SCHEMA_TABLENAME, rowKeyMetricTable);
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITTEN, 2, null);
    }

	private void _put(String tableName, String rowKey) {
		_logger.debug(MessageFormat.format("Inserting rowkey {0} into table {1}", rowKey, tableName));

        final PutRequest put = new PutRequest(Bytes.toBytes(tableName), Bytes.toBytes(rowKey), COLUMN_FAMILY, COLUMN_QUALIFIER, CELL_VALUE);
        Deferred<Object> deferred = _client.put(put);

    	deferred.addCallback(new Callback<Object, Object>() {
			@Override
			public Object call(Object arg) throws Exception {
				_logger.trace(MessageFormat.format("Put to {0} successfully.", tableName));
				return null;
			}
        });

        deferred.addErrback(new Callback<Object, Exception>() {
            @Override
            public Object call(Exception e) throws Exception {
                throw new SystemException("Error occurred while trying to execute put().", e);
            }
        });


        if(_syncPut) {
        	try {
				deferred.join(TIMEOUT_MS);
			} catch (InterruptedException e) {
				_logger.warn("Interrupted while waiting for put to finish.", e);
			} catch (Exception e) {
				_logger.error("Exception while trying to put schema records.", e);
				throw new SystemException(e);
			}
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
            SCOPE_SCHEMA_TABLENAME).toCharArray();
        char[] metricTableRowKey = _constructRowKey(query.getNamespace(), query.getScope(), query.getMetric(), query.getTagKey(), query.getTagValue(),
            METRIC_SCHEMA_TABLENAME).toCharArray();

        // Find first occurrence of any wildcard character in both rowKeys.
        // Everything until this character will represent our entry point into the table.
        // We will therefore use the corresponding table where the index of wildcard in the rowKey is higher.
        int i = 0, j = 0;

        for (; (i < scopeTableRowKey.length && j < metricTableRowKey.length); i++, j++) {
            if (SchemaService.isWildcardCharacter(scopeTableRowKey[i]) || SchemaService.isWildcardCharacter(metricTableRowKey[j])) {
                break;
            }
        }
        while (i < scopeTableRowKey.length && !SchemaService.isWildcardCharacter(scopeTableRowKey[i])) {
            i++;
        }
        while (j < metricTableRowKey.length && !SchemaService.isWildcardCharacter(metricTableRowKey[j])) {
            j++;
        }

        // If the first wildcard character is OR, then we have to backtrack until the last ROW_SEPARATOR occurrence.
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
            metadata.tableName = METRIC_SCHEMA_TABLENAME;
            indexOfWildcard = j;
            rowKey = new String(metricTableRowKey);
        } else {
            metadata.tableName = SCOPE_SCHEMA_TABLENAME;
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

    /**
     * The set of implementation specific configuration properties.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {

        HBASE_METRICSCHEMA_TABLE("service.property.schema.hbase.metricschema.table", "metric-schema"),
    	HBASE_SCOPESCHEMA_TABLE("service.property.schema.hbase.scopeschema.table", "scope-schema");

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
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    static class ScanMetadata {

        /** The start row. */
        public byte[] startRow = new byte[0];

        /** The end row. */
        public byte[] stopRow = new byte[0];

        /** The table type. */
        public String tableName = SCOPE_SCHEMA_TABLENAME;
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
