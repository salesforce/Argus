package com.salesforce.dva.argus.service.schema;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.SmartArrayBasedNodeFactory;
import com.googlecode.concurrenttrees.radix.node.concrete.voidvalue.VoidValue;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

public abstract class AbstractSchemaService extends DefaultService implements SchemaService {
	
	private static final long MAX_MEMORY = Runtime.getRuntime().maxMemory();
	private static final long POLL_INTERVAL_MS = 60 * 1000L;
	protected static final RadixTree<VoidValue> TRIE = new ConcurrentRadixTree<>(new SmartArrayBasedNodeFactory());
	
	private static boolean _writesToTrieEnabled = true;
    
    private final Logger _logger = LoggerFactory.getLogger(getClass());
	private final Thread _oldGenMonitorThread;
	private final boolean _cacheEnabled;
    protected final boolean _syncPut;

	protected AbstractSchemaService(SystemConfiguration config) {
		super(config);
		
    	_cacheEnabled = Boolean.parseBoolean(
    			config.getValue(Property.CACHE_SCHEMARECORDS.getName(), Property.CACHE_SCHEMARECORDS.getDefaultValue()));
    	_syncPut = Boolean.parseBoolean(
    			config.getValue(Property.SYNC_PUT.getName(), Property.SYNC_PUT.getDefaultValue()));
    	
    	_oldGenMonitorThread = new Thread(new OldGenMonitorThread(), "old-gen-monitor");
    	if(_cacheEnabled) {
        	_oldGenMonitorThread.start();
    	}
	}

	@Override
	public void put(Metric metric) {
		requireNotDisposed();
		SystemAssert.requireArgument(metric != null, "Metric cannot be null.");
		
		put(Arrays.asList(metric));
	}

	@Override	
	public void put(List<Metric> metrics) {
		requireNotDisposed();
        SystemAssert.requireArgument(metrics != null, "Metric list cannot be null.");
		
        //If cache is not enabled, call implementation specific put with the list of metrics. 
        if(!_cacheEnabled) {
        	implementationSpecificPut(metrics);
        	return;
        }
        
        //If cache is enabled, create a list of metricsToPut that do not exist on the TRIE and then call implementation 
        // specific put with only those subset of metricsToPut. 
        List<Metric> metricsToPut = new ArrayList<>(metrics.size());
		
		for(Metric metric : metrics) {
			if(metric.getTags().isEmpty()) {
				String key = constructTrieKey(metric, null);
				boolean found = TRIE.getValueForExactKey(key) != null;
		    	if(!found) {
		    		metricsToPut.add(metric);
		    		if(_writesToTrieEnabled) {
                        TRIE.putIfAbsent(key, VoidValue.SINGLETON);
		    		}
		    	}
			} else {
				boolean newTags = false;
				for(Entry<String, String> tagEntry : metric.getTags().entrySet()) {
					String key = constructTrieKey(metric, tagEntry);
					boolean found = TRIE.getValueForExactKey(key) != null;
			    	if(!found) {
			    		newTags = true;
			    		if(_writesToTrieEnabled) {
	                        TRIE.putIfAbsent(key, VoidValue.SINGLETON);
			    		}
			    	}
				}
				
				if(newTags) {
					metricsToPut.add(metric);
				}
			}
		}
		
		implementationSpecificPut(metricsToPut);
	}
	
	
	protected abstract void implementationSpecificPut(List<Metric> metrics);

	protected String constructTrieKey(Metric metric, Entry<String, String> tagEntry) {
		StringBuilder sb = new StringBuilder(metric.getScope());
		sb.append('\0').append(metric.getMetric());
		
		if(metric.getNamespace() != null) {
			sb.append('\0').append(metric.getNamespace());
		}
		
		if(tagEntry != null) {
			sb.append('\0').append(tagEntry.getKey()).append('\0').append(tagEntry.getValue());
		}
		
		return sb.toString();
	}
	
	protected String constructTrieKey(String scope, String metric, String tagk, String tagv, String namespace) {
		StringBuilder sb = new StringBuilder(scope);
		sb.append('\0').append(metric);
		
		if(namespace != null) {
			sb.append('\0').append(namespace);
		}
		
		if(tagk != null) {
			sb.append('\0').append(tagk);
		}
		
		if(tagv != null) {
			sb.append('\0').append(tagv);
		}
		
		return sb.toString();
	}


	@Override
	public void dispose() {
		requireNotDisposed();
        if (_oldGenMonitorThread != null && _oldGenMonitorThread.isAlive()) {
            _logger.info("Stopping old gen monitor thread.");
            _oldGenMonitorThread.interrupt();
            _logger.info("Old gen monitor thread interrupted.");
            try {
                _logger.info("Waiting for old gen monitor thread to terminate.");
                _oldGenMonitorThread.join();
            } catch (InterruptedException ex) {
                _logger.warn("Old gen monitor thread was interrupted while shutting down.");
            }
            _logger.info("System monitoring stopped.");
        } else {
            _logger.info("Requested shutdown of old gen monitor thread aborted, as it is not yet running.");
        }
	}

	@Override
	public abstract Properties getServiceProperties();
	
	@Override
	public abstract List<MetricSchemaRecord> get(MetricSchemaRecordQuery query);

	@Override
	public abstract List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type);

	@Override
	public abstract List<MetricSchemaRecord> keywordSearch(KeywordQuery query);
	
	
	/**
     * The set of implementation specific configuration properties.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {
        
    	/* If set to true, schema records will be cached on writes. This helps to check if a schema records already exists,
    	 * and if it does then do not rewrite. Provide more heap space when using this option. */
    	CACHE_SCHEMARECORDS("service.property.schema.cache.schemarecords", "false"),
    	SYNC_PUT("service.property.schema.sync.put", "false");

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
     * Old Generation monitoring thread.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
	private class OldGenMonitorThread implements Runnable {

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				_sleepForPollPeriod();
				if (!Thread.currentThread().isInterrupted()) {
					try {
						_checkOldGenUsage();
					} catch (Exception ex) {
						_logger.warn("Exception occurred while checking old generation usage.", ex);
					}
				}
			}
		}

		private void _checkOldGenUsage() {
			List<MemoryPoolMXBean> memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();
			for (MemoryPoolMXBean bean : memoryPoolBeans) {
				if (bean.getType() == MemoryType.HEAP) {
					String name = bean.getName().toLowerCase();
					if (name.contains("old") || name.contains("tenured")) {
						long oldGenUsed = bean.getUsage().getUsed();
						_logger.info("Old Gen Memory = {} bytes", oldGenUsed);
						_logger.info("Max JVM Memory = {} bytes", MAX_MEMORY);
						if (oldGenUsed > 0.90 * MAX_MEMORY) {
							_logger.info("JVM heap memory usage has exceeded 90% of the allocated heap memory. Disabling writes to TRIE.");
							_writesToTrieEnabled = false;
						} else if(oldGenUsed < 0.50 * MAX_MEMORY && !_writesToTrieEnabled) {
							_logger.info("JVM heap memory usage is below 50% of the allocated heap memory and writes to TRIE is disabled. "
									+ "Enabling writes to TRIE now.");
							_writesToTrieEnabled = true;
						}
					}
				}
			}
		}

		private void _sleepForPollPeriod() {
			try {
				_logger.info("Sleeping for {}s before checking old gen usage.", POLL_INTERVAL_MS / 1000);
				Thread.sleep(POLL_INTERVAL_MS);
			} catch (InterruptedException ex) {
				_logger.warn("AbstractSchemaService memory monitor thread was interrupted.");
				Thread.currentThread().interrupt();
			}
		}
	}

}
