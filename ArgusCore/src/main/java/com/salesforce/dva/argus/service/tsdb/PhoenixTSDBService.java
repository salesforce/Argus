package com.salesforce.dva.argus.service.tsdb;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.phoenix.schema.TableAlreadyExistsException;
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
			createViewAndCommitDps(metric);
		}
	}
	
	private void createViewAndCommitDps(Metric metric) {
		
		String viewName = "\"" + metric.getScope() + "\".\"" + metric.getMetric() + "\"";
		
		String createViewQuery = MessageFormat.format("CREATE VIEW {0} ({1}) AS SELECT * "
				+ "FROM ARGUS.METRICS WHERE METRIC_ID = MD5(''{0}'')", viewName, generateCols(metric.getTags()));
		_logger.info("Craete View query: " + createViewQuery);
		
		Statement stmt = null;
		try {
			stmt = _connection.createStatement();
			stmt.executeUpdate(createViewQuery);
		} catch(TableAlreadyExistsException taee) {
			if(stmt != null) {
				//View exists
				for(String tagKey : metric.getTags().keySet()) {
					String addCol = MessageFormat.format("ALTER VIEW {0} ADD IF NOT EXISTS {1} VARCHAR PRIMARY KEY", viewName, tagKey);
					_logger.info("Alter view add column query: " + addCol);
					try {
						stmt.executeUpdate(addCol);
					} catch (SQLException e) {
						throw new SystemException(e);
					}
				}
			}
		} catch(SQLException sqle) {
			 throw new SystemException(sqle);
		} finally {
			if(stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					throw new SystemException("Failed to close statement. This should never happen", e);
				}
			}
		}
		
		
		
		String tagkeys = "", tagvalues = "";
		for(Map.Entry<String, String> tagEntry : metric.getTags().entrySet()) {
			tagkeys += tagEntry.getKey() + ",";
			tagvalues += "'" + tagEntry.getValue() + "',";
		}
		
		tagkeys = tagkeys.substring(0, tagkeys.length() - 1);
		tagvalues = tagvalues.substring(0, tagvalues.length() - 1);
		
		String upsertValueQuery = MessageFormat.format("upsert into {0} (ts, val, {1}) values(?, ?, {2})", viewName, tagkeys, tagvalues);
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = _connection.prepareStatement(upsertValueQuery);
			for(Map.Entry<Long, String> datapointEntry : metric.getDatapoints().entrySet()) {
				
				Long timestamp = datapointEntry.getKey();
				String value = datapointEntry.getValue();
				
				preparedStmt.setDate(1, new Date(timestamp));
				preparedStmt.setDouble(2, Double.parseDouble(value));
				preparedStmt.execute();
			}
			
			// Commint maybe in batches of 1000 datapoints. 
			_connection.commit();
		} catch (SQLException e) {
			throw new SystemException(e);
		} finally {
			if(preparedStmt != null) {
				try {
					preparedStmt.close();
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
	}

	private static String generateCols(Map<String, String> tags) {
		StringBuilder colSb = new StringBuilder();
		for(String tagKey : tags.keySet()) {
			colSb.append(tagKey).append(" ").append("varchar"). append(", ");
		}
		
		colSb.append("CONSTRAINT PK PRIMARY KEY (");
		for(String tagKey : tags.keySet()) {
			colSb.append(tagKey).append(",");
		}
		colSb.deleteCharAt(colSb.length() - 1);
		colSb.append(")");
		
		return colSb.toString();
	}
	
	public static void main(String args[]) {
		
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("device", "app1-1-sfm.ops.sfdc.net");
		tags.put("datacenter", "SFM");
		
		System.out.println(generateCols(tags));
		
	}

	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		
		Map<MetricQuery, List<Metric>> result = new HashMap<>();
		
		for(MetricQuery query : queries) {
			
			Date startDate = new Date(query.getStartTimestamp());
			Date endDate = new Date(query.getEndTimestamp());
			
			Map<String, Metric> metrics = new HashMap<>();
			
			String selectQuery = getPhoenixQuery(query);
			//List<String> columns = new ArrayList<>(query.getTags().keySet());
			//columns.add("epoch_time");
			//columns.add("val");
			
			try {
				PreparedStatement preparedStmt = _connection.prepareStatement(selectQuery);
				preparedStmt.setDate(1, endDate);
				preparedStmt.setDate(2, startDate);
				
				ResultSet rs = preparedStmt.executeQuery();
				
				ResultSetMetaData metaData = rs.getMetaData();
				int colCount = metaData.getColumnCount();
				
				while(rs.next()) {
					
					Map<String, String> tags = new HashMap<>();
					
//					for(String column : columns) {
//						if("epoch_time".equals(column)) {
//							timestamp = rs.getDate(column).getTime();
//						} else if("val".equals(column)) {
//							value = Double.toString(rs.getDouble(column));
//						} else {
//							tags.put(column, rs.getString(column));
//						}
//					}
					
					long timestamp = rs.getDate(2).getTime();
					String value = Double.toString(rs.getDouble(1));
					
					for(int i=3; i<=colCount; i++) {
						tags.put(metaData.getColumnName(i), rs.getString(i));
					}
					
					Map<Long, String> datapoints = new HashMap<>();
					datapoints.put(timestamp, value);
					String identifier = tags.toString();
					if(metrics.containsKey(identifier)) {
						metrics.get(identifier).addDatapoints(datapoints);
					} else {
						Metric metric = new Metric(query.getScope(), query.getMetric());
						metric.setTags(tags);
						metric.setDatapoints(datapoints);
						metrics.put(identifier, metric);
					}
				}
			} catch(SQLException sqle) {
				_logger.warn("Failed to read data from Phoenix.", sqle);
			}
			
			result.put(query, new ArrayList<>(metrics.values()));
		}
		
		return result;
	}

	private String getPhoenixQuery(MetricQuery query) {
		
		String viewName = "\"" + query.getScope() + "\".\"" + query.getMetric() + "\"";
		
		//TODO: Convert MetricQuery aggregators to SQL aggregation functions.
		String agg = query.getAggregator() == null ? "avg" : query.getAggregator().name();
		
		String tagkeys = "", tagWhereClaue = "";
		for(Map.Entry<String, String> tagEntry : query.getTags().entrySet()) {
			tagkeys += ", " + tagEntry.getKey();
			//TODO: Add support for tagKey=* and tagKey=a|b
			tagWhereClaue += " AND " + tagEntry.getKey() + " IN (''" + tagEntry.getValue() + "'')";
		}
		
		
		//TODO: start date inclusive??
		String innerquery = MessageFormat.format("SELECT ts, {0}(val) A{1} FROM {2} "
				+ "WHERE ts < ? AND ts > ? {3} GROUP BY ts{1}", 
				agg, tagkeys, viewName, tagWhereClaue);
		
		//TODO: convert downsampler from hardcoded avg. 
		String outerQuery = MessageFormat.format("SELECT AVG(A) val, trunc(T.ts, ''HOUR'', 1) epoch_time{0} FROM (" + innerquery + ") AS T GROUP BY epoch_time{0}", tagkeys);
		
		return outerQuery;
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
