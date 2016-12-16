package com.salesforce.dva.argus.service;

import org.hbase.async.Config;
import org.hbase.async.HBaseClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

@Singleton
public class AsyncHBaseClientFactory {
	
	private HBaseClient _client; 
	
	@Inject
	private AsyncHBaseClientFactory(SystemConfiguration systemConfig) {
		SystemAssert.requireArgument(systemConfig != null, "System configuration cannot be null.");
		
		if(_client == null) {
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
		}
	}
	
	public HBaseClient getClient() {
		return _client;
	}
	

	public enum Property {
    	
        HBASE_ZOOKEEPER_CONNECT("asynchbase.property.hbase.zookeeper.connect", "hbase.zookeeper.com:1234"),
        HBASE_ZOOKEEPER_SESSION_TIMEOUT("asynchbase.property.hbase.zookeeper.session.timeout", "6000"),
        
        HBASE_SECURITY_AUTHENTICATION("asynchbase.property.hbase.security.authentication", ""),
        HBASE_RPC_PROTECTION("asynchbase.property.hbase.rpc.protection", ""),
        HBASE_SASL_CLIENTCONFIG("asynchbase.property.hbase.sasl.clientconfig", "Client"),
        HBASE_SECURITY_AUTH_ENABLE("asynchbase.property.hbase.security.auth.enable", "false"),
        HBASE_KERBEROS_REGIONSERVER_PRINCIPAL("asynchbase.property.hbase.kerberos.regionserver.principal", ""),
        
        HBASE_RPCS_BATCH_SIZE("asynchbase.property.hbase.rpcs.batch.size", "16192"),
        HBASE_RPCS_BUFFERED_FLUSH_INTERVAL("asynchbase.property.hbase.rpcs.buffered_flush_interval", "5000"),
        HBASE_RPC_TIMEOUT("asynchbase.property.hbase.rpc.timeout", "0");

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
