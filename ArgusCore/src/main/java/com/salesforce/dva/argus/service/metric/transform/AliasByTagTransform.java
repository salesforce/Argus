package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;

public class AliasByTagTransform implements Transform {
	
	static final String DELIMITER = ",";

	@Override
	public List<Metric> transform(List<Metric> metrics) {
		SystemAssert.requireArgument(metrics != null, "Metric list cannot be null.");
		
		for(Metric metric : metrics) {
			String displayName = "";
			
			for(Map.Entry<String, String> entry : metric.getTags().entrySet()) {
				displayName += entry.getValue() + DELIMITER;
			}
			
			if(!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				metric.setDisplayName(displayName);
			}
		}
		return metrics;
	}
	
	@Override
	public List<Metric> transformScanner(List<MetricScanner> scanners) {
		SystemAssert.requireArgument(scanners != null, "Metric scanners list cannot be null");
		
		List<Metric> result = new ArrayList<>();
		for (MetricScanner scanner : scanners) {
			String displayName = "";
			buildMetric(scanner);
			Metric m = new Metric(scanner.getMetric());
			
			for (Map.Entry<String, String> entry : scanner.getMetricTags().entrySet()) {
				displayName += entry.getValue() + DELIMITER;
			}
			
			if (!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				m.setDisplayName(displayName);
			}
			
			result.add(m);
		}
		return result;
	}
	
	private void buildMetric(MetricScanner scanner) {
		while (scanner.hasNextDP()) {
			scanner.getNextDP();
		}
	}
	
	@Override
	public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
		SystemAssert.requireArgument(scanners != null, "Metric scanners list cannot be null");
		
		List<Metric> result = new ArrayList<>();
		for (MetricScanner scanner : scanners) {
			String displayName = "";
			Metric m = buildMetricRange(scanner, start, end);
			
			for (Map.Entry<String, String> entry : scanner.getMetricTags().entrySet()) {
				displayName += entry.getValue() + DELIMITER;
			}
			
			if (!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				m.setDisplayName(displayName);
			}
			
			result.add(m);			
		}
		return result;
	}
	
	private Metric buildMetricRange(MetricScanner scanner, Long start, Long end) {
		Metric m = new Metric(scanner.getMetric());
		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
		if (dps.lastKey() < end) {
			while (scanner.peek() != null && scanner.peek().getKey() <= end) {
				Map.Entry<Long, Double> dp = scanner.getNextDP();
				dps.put(dp.getKey(), dp.getValue());
			}
		}
		Long startKey = dps.ceilingKey(start);
		Long endKey = dps.floorKey(end);
		if (startKey == null || endKey == null || startKey > endKey) {
			m.setDatapoints(new TreeMap<>());
			return m;
		}
		m.setDatapoints(dps.subMap(startKey, endKey + 1));
		return m;
	}
	
	@Override
	public List<Metric> transform(List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(metrics != null, "Metric list cannot be null.");
		SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Constant list cannot be null or empty.");
		
		for(Metric metric : metrics) {
			String displayName = "";
			
			Map<String, String> tags = metric.getTags();
			for(String tagKey : constants) {
				if(tags.containsKey(tagKey)) {
					displayName += tags.get(tagKey) + DELIMITER;
				}
			}
			
			if(!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				metric.setDisplayName(displayName);
			}
		}
		return metrics;
	}
	
	@Override
	public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
		SystemAssert.requireArgument(scanners != null, "Metric scanners list cannot be null.");
		SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Constant list cannot be null or empty.");
		
		
		List<Metric> result = new ArrayList<>();
		
		for (MetricScanner scanner : scanners) {
			String displayName = "";
			buildMetric(scanner);
			Metric m = new Metric(scanner.getMetric());
			
			Map<String, String> tags = scanner.getMetricTags();
			for (String tagKey : constants) {
				if (tags.containsKey(tagKey)) {
					displayName += tags.get(tagKey) + DELIMITER;
				}
			}
			
			if (!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				m.setDisplayName(displayName);
			}
			
			result.add(m);
		}
		return result;
	}
	
	@Override
	public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
		SystemAssert.requireArgument(scanners != null, "Metric scanners list cannot be null.");
		SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Constant list cannot be null or empty.");
		
		List<Metric> result = new ArrayList<>();
		for (MetricScanner scanner : scanners) {
			String displayName = "";
			Metric m = buildMetricRange(scanner, start, end);
			
			Map<String, String> tags = scanner.getMetricTags();
			for (String tagKey : constants) {
				if (tags.containsKey(tagKey)) {
					displayName += tags.get(tagKey) + DELIMITER;
				}
			}
			
			if (!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				m.setDisplayName(displayName);
			}
			
			result.add(m);			
		}
		return result;
	}

	@Override
	public List<Metric> transform(@SuppressWarnings("unchecked") List<Metric>... metrics) {
		throw new UnsupportedOperationException("ALIASBYTAG doesn't need a list of lists!");
	}
	
	@Override
	public List<Metric> transformScanner(@SuppressWarnings("unchecked") List<MetricScanner>... scanners) {
		throw new UnsupportedOperationException("ALIASBYTAG doesn't need a list of lists!");
	}
	
	@Override
	public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
		throw new UnsupportedOperationException("ALIASBYTAG doesn't need a list of lists!");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.ALIASBYTAG.name();
	}

}
