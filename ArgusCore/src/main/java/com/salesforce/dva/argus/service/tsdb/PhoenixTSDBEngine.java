package com.salesforce.dva.argus.service.tsdb;

import java.sql.Connection;
import java.sql.Date;
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

import org.apache.phoenix.schema.TableAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.system.SystemException;

public class PhoenixTSDBEngine {
	
	protected Logger _logger = LoggerFactory.getLogger(getClass());
	
	public PhoenixTSDBEngine() {
		
	}
	
	void createOrUpdateView(Connection connection, Metric metric) {

		String viewName = getPhoenixViewName(metric.getScope(), metric.getMetric());
		
		String createViewSql = MessageFormat.format("CREATE VIEW {0} ({1}) AS SELECT * "
				+ "FROM ARGUS.METRICS WHERE METRIC_ID = MD5(''{0}'')", viewName, generateCols(metric.getTags()));
		_logger.info("Craete View query: " + createViewSql);
		
		Statement stmt = null;
		try {
			stmt = connection.createStatement();
			stmt.executeUpdate(createViewSql);
		} catch(TableAlreadyExistsException taee) {
			//View exists
			for(String tagKey : metric.getTags().keySet()) {
				String addColIfNotExistsSql = MessageFormat.format("ALTER VIEW {0} ADD IF NOT EXISTS {1} VARCHAR PRIMARY KEY", viewName, tagKey);
				_logger.info("Alter view add column query: " + addColIfNotExistsSql);
				try {
					stmt.executeUpdate(addColIfNotExistsSql);
				} catch (SQLException e) {
					throw new SystemException(e);
				}
			}
		} catch(SQLException sqle) {
			 throw new SystemException("Database access error occured or "
			 		+ "createStatement() was called on a closed connection.", sqle);
		} finally {
			if(stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
					throw new SystemException("Failed to close statement. This should never happen", e);
				}
			}
		}
	}
	
	void upsertMetrics(Connection connection, Metric metric) {
		
		String viewName = getPhoenixViewName(metric.getScope(), metric.getMetric());
	
		String tagkeys = "", tagvalues = "";
		for(Map.Entry<String, String> tagEntry : metric.getTags().entrySet()) {
			tagkeys += "\"" + tagEntry.getKey() + "\",";
			tagvalues += "'" + tagEntry.getValue() + "',";
		}
		
		if(metric.getDisplayName() != null && !metric.getDisplayName().isEmpty()) {
			tagkeys += "DISPLAY_NAME,";
			tagvalues += "'" + metric.getDisplayName() + "',";
		}
		
		if(metric.getUnits() != null && !metric.getUnits().isEmpty()) {
			tagkeys += "UNITS" + ",";
			tagvalues += "'" + metric.getUnits() + "',";
		}
		
		tagkeys = tagkeys.substring(0, tagkeys.length() - 1);
		tagvalues = tagvalues.substring(0, tagvalues.length() - 1);
		
		String upsertMetricSql = MessageFormat.format("upsert into {0} (ts, val, {1}) values(?, ?, {2})", viewName, tagkeys, tagvalues);
		PreparedStatement preparedStmt = null;
		try {
			preparedStmt = connection.prepareStatement(upsertMetricSql);
			for(Map.Entry<Long, String> datapointEntry : metric.getDatapoints().entrySet()) {
				
				Long timestamp = datapointEntry.getKey();
				String value = datapointEntry.getValue();
				
				preparedStmt.setDate(1, new Date(timestamp));
				preparedStmt.setDouble(2, Double.parseDouble(value));
				preparedStmt.execute();
			}
			
			// Commit maybe in batches of 1000 datapoints.
			connection.commit();
		} catch (SQLException e) {
			throw new SystemException("Failed to insert values into Phoenix.", e);
		} finally {
			if(preparedStmt != null) {
				try {
					preparedStmt.close();
				} catch (SQLException e) {
					throw new SystemException("Failed to close Prepared Statement. This should never happen.", e);
				}
			}
		}
		
	}
	
	List<Metric> selectMetrics(Connection connection, MetricQuery metricQuery) {
		Date startDate = new Date(metricQuery.getStartTimestamp()); 
		Date endDate = new Date(metricQuery.getEndTimestamp());
		
		Map<String, Metric> metrics = new HashMap<>();
		
		String selectQuery = getPhoenixQuery(metricQuery);
		try {
			PreparedStatement preparedStmt = connection.prepareStatement(selectQuery);
			preparedStmt.setDate(1, endDate);
			preparedStmt.setDate(2, startDate);
			
			ResultSet rs = preparedStmt.executeQuery();
			
			ResultSetMetaData metaData = rs.getMetaData();
			int colCount = metaData.getColumnCount();
			
			while(rs.next()) {
				
				Map<String, String> tags = new HashMap<>();
				
				String value = Double.toString(rs.getDouble(1));
				long timestamp = rs.getDate(2).getTime();
				String displayName = rs.getString(3);
				String units = rs.getString(4);
				
				for(int i=5; i<=colCount; i++) {
					tags.put(metaData.getColumnName(i), rs.getString(i));
				}
				
				Map<Long, String> datapoints = new HashMap<>();
				datapoints.put(timestamp, value);
				String identifier = tags.toString();
				if(metrics.containsKey(identifier)) {
					metrics.get(identifier).addDatapoints(datapoints);
				} else {
					Metric metric = new Metric(metricQuery.getScope(), metricQuery.getMetric());
					metric.setTags(tags);
					metric.setDatapoints(datapoints);
					metric.setDisplayName(displayName);
					metric.setUnits(units);
					
					metrics.put(identifier, metric);
				}
			}
		} catch(SQLException sqle) {
			_logger.warn("Failed to read data from Phoenix.", sqle);
		}
		
		return new ArrayList<>(metrics.values());
	}

	private String generateCols(Map<String, String> tags) {
		StringBuilder colSb = new StringBuilder();
		for(String tagKey : tags.keySet()) {
			colSb.append("\"").append(tagKey).append("\" ").append("varchar"). append(", ");
		}
		
		colSb.append("CONSTRAINT PK PRIMARY KEY (");
		for(String tagKey : tags.keySet()) {
			colSb.append("\"").append(tagKey).append("\",");
		}
		colSb.deleteCharAt(colSb.length() - 1);
		colSb.append(")");
		
		return colSb.toString();
	}
	
	private String getPhoenixQuery(MetricQuery query) {
		
		String viewName = getPhoenixViewName(query.getScope(), query.getMetric());
		String agg = convertArgusAggregatorToPhoenixAggregator(query.getAggregator());
		
		String tagkeys = "", tagWhereClaue = "";
		for(Map.Entry<String, String> tagEntry : query.getTags().entrySet()) {
			tagkeys += ", \"" + tagEntry.getKey() + "\"";
			//TODO: Add support for tagKey=* and tagKey=a|b
			tagWhereClaue += " AND \"" + tagEntry.getKey() + "\" IN ('" + tagEntry.getValue() + "')";
		}
		
		String selectSql = MessageFormat.format("SELECT {0}(val) val, ts epoch_time, display_name, units {1} FROM {2}"
				+ " WHERE ts < ? AND ts >= ? {3}"
				+ " GROUP BY epoch_time, display_name, units {1}", agg, tagkeys, viewName, tagWhereClaue);
		
		if(query.getDownsampler() != null) {
			if(!query.getDownsampler().equals(query.getAggregator())) {
				String downsamplingAgg = convertArgusAggregatorToPhoenixAggregator(query.getDownsampler());
				selectSql = MessageFormat.format("SELECT {0}(val) val, trunc(T.epoch_time, ''MILLISECOND'', {1}) epoch_time,"
						+ " display_name, units {2} FROM ({3}) AS T GROUP BY epoch_time, display_name, units {2}",
						downsamplingAgg, query.getDownsamplingPeriod(), 
						tagkeys, selectSql);
			} else {
				selectSql = MessageFormat.format("SELECT {0}(val) val, trunc(ts, ''MILLISECOND'', {1}) epoch_time, display_name,"
						+ " units {2} FROM {3} WHERE ts < ? AND ts >= ? {4} GROUP BY epoch_time, display_name, units {1}",
						agg, query.getDownsamplingPeriod(), tagkeys, viewName, tagWhereClaue);
			}
		}
		
		return selectSql;
	}

	private String getPhoenixViewName(String scope, String metric) {
		return "\"" + scope + "\".\"" + metric + "\"";
	}
	
	private String convertArgusAggregatorToPhoenixAggregator(Aggregator aggregator) {
		if(aggregator == null) {
			return "AVG";
		}
		
		switch(aggregator) {
		case AVG:
			return "AVG";
		case SUM:
			return "SUM";
		case MIN:
			return "MIN";
		case MAX: 
			return "MAX";
		case DEV:
			return "STDDEV_POP";
		default:
			return "AVG";
		}
	}
	
}
