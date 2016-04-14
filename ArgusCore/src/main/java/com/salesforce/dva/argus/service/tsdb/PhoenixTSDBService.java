package com.salesforce.dva.argus.service.tsdb;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;


/*
 * Quotes around columns and view names make them case sensitive. 
 */
public class PhoenixTSDBService extends DefaultService implements TSDBService {
	
	protected Logger _logger = LoggerFactory.getLogger(getClass());
	private final MonitorService _monitorService;
	private final String _phoenixJDBCUrl;
	
	// A separate connection for each thread.
	private final Connection _connection;
	
	private final PhoenixTSDBEngine phoenixEngine = new PhoenixTSDBEngine(); 
	
	@Inject
	public PhoenixTSDBService(SystemConfiguration config, MonitorService monitorService) {
		super(config);
        requireArgument(config != null, "System configuration cannot be null.");
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        
        _monitorService = monitorService;
		_phoenixJDBCUrl = config.getValue(Property.PHOENIX_JDBC_URL.getName(), Property.PHOENIX_JDBC_URL.getDefaultValue());
		
		try {
			_connection = DriverManager.getConnection(_phoenixJDBCUrl);
		} catch (SQLException e) {
			throw new SystemException("Failed to craete connection to phoenix using jdbc url: " + _phoenixJDBCUrl, e);
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		try {
			_connection.close();
		} catch (SQLException e) {
			throw new SystemException("Failed to close jdbc connection to phoenix. This should never happen.", e);
		}
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
	public void putMetrics(List<Metric> metrics) {
		SystemAssert.requireArgument(metrics != null && !metrics.isEmpty(), "Cannot put null or empty metrics list.");
		
		for(Metric metric : metrics) {
			phoenixEngine.createOrUpdateView(_connection, metric);
			phoenixEngine.upsertMetrics(_connection, metric);
		}
	}

	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		SystemAssert.requireArgument(queries != null, "Metric queries list cannot be null.");
		
		Map<MetricQuery, List<Metric>> result = new HashMap<>();
		for(MetricQuery query : queries) {
			result.put(query, phoenixEngine.selectMetrics(_connection, query));
		}
		return result;
	}

	@Override
	public void putAnnotations(List<Annotation> annotations) {
		// TODO Auto-generated method stub

	}

	@Override
	public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String constructTSDBMetricName(String scope, String namespace) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getScopeFromTSDBMetric(String tsdbMetricName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getNamespaceFromTSDBMetric(String tsdbMetricName) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	//~ Enums ****************************************************************************************************************************************

    /**
     * Enumerates the implementation specific configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The TSDB read endpoint. */
        PHOENIX_JDBC_URL("service.property.tsdb.phoenix.jdbc.url", "jdbc:phoenix:vampire8.internal.salesforce.com"),
        PHOENIX_CONNECTIONS("service.property.tsdb.phoenix.connections", "10");

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
