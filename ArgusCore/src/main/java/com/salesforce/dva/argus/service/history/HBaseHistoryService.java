package com.salesforce.dva.argus.service.history;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hadoop.hbase.util.Bytes;
import org.hbase.async.Config;
import org.hbase.async.HBaseClient;
import org.hbase.async.KeyValue;
import org.hbase.async.PutRequest;
import org.hbase.async.RegexStringComparator;
import org.hbase.async.RowFilter;
import org.hbase.async.ScanFilter;
import org.hbase.async.Scanner;
import org.hbase.async.CompareFilter.CompareOp;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import com.stumbleupon.async.TimeoutException;

public class HBaseHistoryService extends DefaultService implements HistoryService {
	
	//~ Static fields/initializers *******************************************************************************************************************
	
	private static final byte[] TABLENAME = "history".getBytes(Charset.forName("UTF-8"));
    private static final byte[] COLUMN_FAMILY = "f".getBytes(Charset.forName("UTF-8"));
    private static final byte[] COLUMN_QUALIFIER = "c".getBytes(Charset.forName("UTF-8"));
    private static final char ROWKEY_SEPARATOR = ':';
	
    
    //~ Instance fields ******************************************************************************************************************************
    
	@SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final HBaseClient _client;
    private final ObjectMapper _mapper;

    
    //~ Constructors *********************************************************************************************************************************
    
    @Inject
	protected HBaseHistoryService(SystemConfiguration systemConfig) {
		super(systemConfig);
		
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
        _mapper = new ObjectMapper();
	}
    
    
    //~ Public/API Methods **************************************************************************************************************************************

	@Override
	public void deleteExpiredHistory() {
		requireNotDisposed();
		_logger.info("Deleting expired history for HBaseHistoryService is not explicitly needed.");
		return;
	}

	@Override
	public History createHistory(final JPAEntity entity, final String message, final JobStatus jobStatus, 
									final long executionTime) {
		requireNotDisposed();
		SystemAssert.requireArgument(entity != null, "entity cannot be null.");
		SystemAssert.requireArgument(jobStatus != null, "jobStatus cannot be null.");
		
		long creationTime = System.currentTimeMillis();
		String rowKey = new StringBuilder(entity.getId().toString()).
						append(ROWKEY_SEPARATOR).
						append(_9sComplement(creationTime)).
						append(ROWKEY_SEPARATOR).
						append(jobStatus).
						toString();
		_logger.info("Creatiing history with row key: {}", rowKey);
		
		History history = new History(message, SystemConfiguration.getHostname(), entity.getId(), jobStatus, 
				executionTime, creationTime);
		
		try {
			byte[] value = _mapper.writeValueAsBytes(Arrays.asList(history));
			final PutRequest put = new PutRequest(TABLENAME, Bytes.toBytes(rowKey), COLUMN_FAMILY, 
					COLUMN_QUALIFIER, value);
			
			Deferred<Object> deferred = _client.put(put);
			
			
			deferred.addCallback(new Callback<Object, Object>() {
				@Override
				public Object call(Object arg) throws Exception {
					_logger.trace(MessageFormat.format("Put to {0} successful.", TABLENAME));
					return null;
				}
			});
			
			deferred.addErrback(new Callback<Object, Exception>() {
			    @Override
			    public Object call(Exception e) throws Exception {
			        throw new SystemException("Error occured while trying to execute put().", e);
			    }
			});
		
		} catch (JsonProcessingException e1) {
			_logger.warn("Failed to parse history object to bytes.");
			return null;
		}
		
		return history;
	}

	@Override
	public List<History> findByJob(BigInteger entityId) {
		return findByJob(entityId, Integer.MAX_VALUE);
	}

	@Override
	public List<History> findByJob(BigInteger entityId, int limit) {
		requireNotDisposed();
        SystemAssert.requireArgument(entityId != null, "entityId cannot be null.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");

        long startTime = System.currentTimeMillis();
        List<History> records = _scanRecords(entityId, limit, null);
        _logger.debug("Time taken to read {} history records: {}", limit, (System.currentTimeMillis() - startTime));
        return records;
	}

	@Override
	public List<History> findByJobAndStatus(BigInteger entityId, int limit, JobStatus jobStatus) {
		requireNotDisposed();
        SystemAssert.requireArgument(entityId != null, "entityId cannot be null.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");
        SystemAssert.requireArgument(jobStatus != null, "jobStatus cannot be null.");

        long startTime = System.currentTimeMillis();
        String rowKeyRegex = new StringBuilder("^").
        					append(entityId.toString()).
        					append(".*").
        					append(jobStatus).
        					append("$").
        					toString();
        ScanFilter filter = new RowFilter(CompareOp.EQUAL, new RegexStringComparator(rowKeyRegex));
        List<History> records = _scanRecords(entityId, limit, filter);
        _logger.debug("Time taken to read {} history records: {}", limit, (System.currentTimeMillis() - startTime));
        return records;
	}
	
	@Override
	public void dispose() {
		super.dispose();
		if (_client != null) {
            _logger.info("HBaseHistoryService: Shutting down asynchbase client.");

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
                deferred.join(30000);
            } catch (Exception e) {
                throw new SystemException("Exception while waiting for shutdown to complete.", e);
            }
        }
	}
	
	
	//~ Private Methods **************************************************************************************************************************************
	
	private List<History> _scanRecords(BigInteger entityId, int limit, ScanFilter filter) {
		
		final Scanner scanner = _client.newScanner(TABLENAME);

		String startRow = entityId.toString();
        String stopRow = _plusOne(startRow);
        scanner.setStartKey(startRow);
        scanner.setStopKey(stopRow);
        scanner.setMaxNumRows(Math.min(limit, 5000));
        scanner.setFilter(filter);
		
		final List<History> records = new ArrayList<>(limit);
        final Deferred<List<History>> results = new Deferred<List<History>>();
		
        final class ScannerCB implements Callback<Object, ArrayList<ArrayList<KeyValue>>> {

            /**
             * Scans rows.
             *
             * @return  The list of history records.
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
                    
                    for (ArrayList<KeyValue> row : rows) {
                        for(KeyValue kv : row) {
                        	byte[] value = kv.value();
                        	List<History> histories = _mapper.readValue(value, new TypeReference<List<History>>() {});
                        	records.addAll(histories);
                        }
                        
                        if (records.size() >= limit) {
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
			List<History> histories = results.join(100000);
			if(histories.size() <= limit) {
				return histories;
			}
			
			return histories.subList(0, limit);
			
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for jobId: " + entityId, e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results for jobId: {}. Will return an empty list.", entityId);
			return Collections.emptyList();
		} catch (Exception e) {
			throw new SystemException("Exception occured in getting results for jobId: " + entityId, e);
		}
	}
	
	private static long _9sComplement(long creationTime) {
		String time = String.valueOf(creationTime);
		char[] timeArr = time.toCharArray();
		StringBuilder sb = new StringBuilder();
		for(char c : timeArr) {
			sb.append(9 - Character.getNumericValue(c));
		}
		
		return Long.parseLong(sb.toString());
	}
	
	private static String _plusOne(String prefix) {
		char newChar = (char) (prefix.charAt(prefix.length() - 1) + 1);
    	String end = prefix.substring(0, prefix.length() - 1) + newChar;
		return end;
	}
	
	
	//~ Enums ****************************************************************************************************************************************
	
	/**
     * The set of implementation specific configuration properties.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {
    	
        HBASE_ZOOKEEPER_CONNECT("service.property.schema.hbase.zookeeper.connect", "perfeng-tools1-4-sfm.ops.sfdc.net:2181"),
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

}
