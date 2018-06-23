package com.salesforce.dva.argus.service.audit;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.hadoop.hbase.util.Bytes;
import org.hbase.async.HBaseClient;
import org.hbase.async.KeyValue;
import org.hbase.async.PutRequest;
import org.hbase.async.ScanFilter;
import org.hbase.async.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.service.AsyncHBaseClientFactory;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.HBaseUtils;
import com.stumbleupon.async.Callback;
import com.stumbleupon.async.Deferred;
import com.stumbleupon.async.TimeoutException;

@Singleton
public class HBaseAuditService extends DefaultService implements AuditService {

	//~ Static fields/initializers *******************************************************************************************************************
	
	private static byte[] tablename;
    private static final byte[] COLUMN_FAMILY = "f".getBytes(Charset.forName("UTF-8"));
    private static final byte[] COLUMN_QUALIFIER = "c".getBytes(Charset.forName("UTF-8"));
    private static final char ROWKEY_SEPARATOR = ':';
    private static final int MAX_NUM_ROWS = 500;
    
    private static final long PUT_TIMEOUT_MS = 30 * 1000;
    private static final long SCAN_TIMEOUT_MS = 60 * 1000;
    private static final long SHUTDOWN_TIMEOUT_MS = 30 * 1000;
	
    
    //~ Instance fields ******************************************************************************************************************************
    
    private Logger _logger = LoggerFactory.getLogger(getClass());
    private final HBaseClient _client;
    private final ObjectMapper _mapper;
    private final boolean _syncPut;

    
    //~ Constructors *********************************************************************************************************************************
    
    @Inject
	protected HBaseAuditService(SystemConfiguration systemConfig, AsyncHBaseClientFactory factory) {
		super(systemConfig);
        _client = factory.getClient();
        _mapper = new ObjectMapper();
        _syncPut = Boolean.parseBoolean(systemConfig.getValue(Property.HBASE_SYNC_PUT.getName(), Property.HBASE_SYNC_PUT.getDefaultValue()));
        tablename = systemConfig.getValue(Property.HBASE_TABLE.getName(), Property.HBASE_TABLE.getDefaultValue())
        			.getBytes(Charset.forName("UTF-8"));
	}

	@Override
	public Audit createAudit(String message, JPAEntity entity, Object... params) {
        Audit audit = new Audit(MessageFormat.format(message, params), SystemConfiguration.getHostname(), entity);
        return createAudit(audit);
	}
	
	@Override
	public Audit createAudit(Audit audit) {
		requireNotDisposed();
		SystemAssert.requireArgument(audit != null, "audit cannot be null.");
		
		long creationTime = System.currentTimeMillis();
		String rowKey = new StringBuilder(audit.getEntityId().toString()).
						append(ROWKEY_SEPARATOR).
						append(HBaseUtils._9sComplement(creationTime)).
						append(ROWKEY_SEPARATOR).
						toString();
		audit.setCreatedDate(creationTime);
		
		_logger.debug("Creating audit with row key: {}", rowKey);

		try {
			byte[] value = _mapper.writeValueAsBytes(Arrays.asList(audit));
			final PutRequest put = new PutRequest(tablename, Bytes.toBytes(rowKey), COLUMN_FAMILY, 
					COLUMN_QUALIFIER, value);
			
			Deferred<Object> deferred = _client.put(put);
			
			
			deferred.addCallback(new Callback<Object, Object>() {
				@Override
				public Object call(Object arg) throws Exception {
					_logger.debug(MessageFormat.format("Put to {0} successful.", tablename));
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
				deferred.join(PUT_TIMEOUT_MS);
			}
		
		} catch (JsonProcessingException e) {
			_logger.warn("Failed to parse audit object to bytes.", e);
			return null;
		} catch (InterruptedException e) {
			_logger.warn("Interrupted while waiting for put to finish.", e);
			return null;
		} catch (Exception e) {
			_logger.error("Exception while trying to create audit.", e);
			throw new SystemException(e);
		}
		
		return audit;
	}

	@Override
	public void deleteExpiredAudits() {
		requireNotDisposed();
		_logger.info("Deleting expired audits for HBaseAuditService is not explicitly needed.");
		return;
	}

	@Override
	public List<Audit> findByEntity(BigInteger entityId) {
		return findByEntity(entityId, new BigInteger(Integer.MAX_VALUE+""));
	}

	@Override
	public List<Audit> findByEntity(BigInteger entityId, BigInteger limit) {
		requireNotDisposed();
        SystemAssert.requireArgument(entityId != null, "entityId cannot be null.");
        SystemAssert.requireArgument(limit.intValue()>0, "Limit must be a positive integer.");

        long startTime = System.currentTimeMillis();
        List<Audit> records = _scanRecords(entityId, limit.intValue(), null);
        _logger.debug("Time taken to read {} audit records: {}", limit, (System.currentTimeMillis() - startTime));
        return records;
	}

	@Override
	public List<Audit> findByHostName(String hostName) {
        throw new UnsupportedOperationException();
	}

	@Override
	public Audit findAuditByPrimaryKey(BigInteger id) {
        throw new UnsupportedOperationException();
	}

	@Override
	public List<Audit> findAll() {
        throw new UnsupportedOperationException();
	}

	@Override
	public List<Audit> findByMessage(String message) {
        throw new UnsupportedOperationException();
	}

	@Override
	public List<Audit> findByEntityHostnameMessage(BigInteger entityId, String hostName, String message) {
        throw new UnsupportedOperationException();
	}
	
	@Override
	public void dispose() {
		super.dispose();
		if (_client != null) {
            _logger.info("HBaseAuditService: Shutting down asynchbase client.");

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
                deferred.join(SHUTDOWN_TIMEOUT_MS);
            } catch (Exception e) {
                throw new SystemException("Exception while waiting for shutdown to complete.", e);
            }
        }
	}
	
	//~ Private Methods **************************************************************************************************************************************
	
	private List<Audit> _scanRecords(BigInteger entityId, int limit, ScanFilter filter) {
		
		final Scanner scanner = _client.newScanner(tablename);

		String startRow = entityId.toString();
        String stopRow = HBaseUtils._plusOne(startRow);
        scanner.setStartKey(startRow);
        scanner.setStopKey(stopRow);
        scanner.setMaxNumRows(Math.min(limit, MAX_NUM_ROWS));
        scanner.setFilter(filter);
		
		final List<Audit> records = new ArrayList<>(limit);
        final Deferred<List<Audit>> results = new Deferred<List<Audit>>();
		
        final class ScannerCB implements Callback<Object, ArrayList<ArrayList<KeyValue>>> {

            /**
             * Scans rows.
             *
             * @return  The list of audit records.
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
                        	   List<Audit> audits = _mapper.readValue(value, new TypeReference<List<Audit>>() {});
                        	   records.addAll(audits);
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
			List<Audit> audits = results.join(SCAN_TIMEOUT_MS);
			if(audits.size() <= limit) {
				return audits;
			}
			
			return audits.subList(0, limit);
			
		} catch (InterruptedException e) {
			throw new SystemException("Interrupted while waiting to obtain results for jobId: " + entityId, e);
		} catch (TimeoutException e) {
			_logger.warn("Timed out while waiting to obtain results for jobId: {}. Will return an empty list.", entityId);
			return Collections.emptyList();
		} catch (Exception e) {
			throw new SystemException("Exception occurred in getting results for jobId: " + entityId, e);
		}
	}

	//~ Enums ****************************************************************************************************************************************
	
	/**
     * The set of implementation specific configuration properties.
     *
     * @author  Sundeep Tiyyagura (stiyyagura@salesforce.com)
     */
    public enum Property {
    	
        HBASE_SYNC_PUT("service.property.audit.hbase.sync.put", "false"),
    	    HBASE_TABLE("service.property.audit.hbase.table", "audit");

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
