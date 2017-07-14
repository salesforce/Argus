package com.salesforce.dva.argus.service.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

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
	
	private final RadixTree<VoidValue> _trie;
    private final boolean _cacheEnabled;

	protected AbstractSchemaService(SystemConfiguration systemConfiguration) {
		super(systemConfiguration);
		
		_trie = new ConcurrentRadixTree<>(new SmartArrayBasedNodeFactory());
    	_cacheEnabled = Boolean.parseBoolean(systemConfiguration.getValue(Property.CACHE_SCHEMARECORDS.getName(), 
    			Property.CACHE_SCHEMARECORDS.getDefaultValue()));
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
        
        //If cache is enabled, create a list of metrics that do not exist on the trie and then call implementation 
        // specific put with only this subset of metrics. 
        List<Metric> metricsToPut = new ArrayList<>(metrics.size());
		
		for(Metric metric : metrics) {
			if(metric.getTags().isEmpty()) {
				String key = _constructTrieKey(metric, null);
				boolean found = _trie.getValueForExactKey(key) != null;
		    	if(!found) {
		    		_trie.putIfAbsent(key, VoidValue.SINGLETON);
		    		metricsToPut.add(metric);
		    	}
			} else {
				boolean newTags = false;
				for(Entry<String, String> tagEntry : metric.getTags().entrySet()) {
					String key = _constructTrieKey(metric, tagEntry);
					boolean found = _trie.getValueForExactKey(key) != null;
			    	if(!found) {
			    		newTags = true;
			    		_trie.putIfAbsent(key, VoidValue.SINGLETON);
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

	private String _constructTrieKey(Metric metric, Entry<String, String> tagEntry) {
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


	@Override
	public void dispose() {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}

	@Override
	public Properties getServiceProperties() {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}
	
	@Override
	public List<MetricSchemaRecord> get(MetricSchemaRecordQuery query) {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}

	@Override
	public List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type) {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}

	@Override
	public List<MetricSchemaRecord> keywordSearch(KeywordQuery query) {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}
	
	
	/**
     * The set of implementation specific configuration properties.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {
        
    	/* If set to true, schema records will be cached on writes. This helps to check if a schema records already exists,
    	 * and if it does then do not rewrite. Provide more heap space when using this option. */
    	CACHE_SCHEMARECORDS("service.property.schema.cache.schemarecords", "false");

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
