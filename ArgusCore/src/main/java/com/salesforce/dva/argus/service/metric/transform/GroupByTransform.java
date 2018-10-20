package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

public class GroupByTransform implements Transform {
	
	private TransformFactory _factory;

	public GroupByTransform(TransformFactory transformFactory) {
		_factory = transformFactory;
	}

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		throw new UnsupportedOperationException("GroupBy Transform is supposed to be used with 2 constants: A regex match criteria and an aggregator function name.");
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics");
		SystemAssert.requireArgument(constants != null && constants.size() >= 2, "Constants list cannot be null and its size must be 2 or more.");
		
		//Remove first constant which is the regex to group by.
		Pattern pattern = Pattern.compile(constants.remove(0));
		//Remove second constant which is the function to perform on the grouped metrics. 
		String functionName = constants.remove(0);
		
		Map<String, List<Metric>> groups = new HashMap<>();
		for(Metric metric : metrics) {
			String identifier = metric.getIdentifier();
			Matcher matcher = pattern.matcher(identifier);
			if(matcher.find()) {
				String group = "";
				int i = 1;
				while(i <= matcher.groupCount()) {
					group += matcher.group(i++);
				}
				if(!groups.containsKey(group)) {
					List<Metric> m = new ArrayList<>();
					groups.put(group, m);
				}
				groups.get(group).add(metric);
			} else {
				if(!groups.containsKey(null)) {
					List<Metric> m = new ArrayList<>();
					groups.put(null, m);
				}
				groups.get(null).add(metric);
			}
		}
		
		Transform transform = _factory.getTransform(functionName);
		List<Metric> result = new ArrayList<>();
		for(Entry<String, List<Metric>> entry : groups.entrySet()) {
			List<Metric> metricsInThisGroup = entry.getValue();
			List<Metric> reducedMetrics = constants.isEmpty() ? transform.transform(null, metricsInThisGroup) : transform.transform(null, metricsInThisGroup, constants);
			for(Metric reducedMetric : reducedMetrics) {
				reducedMetric.setScope(entry.getKey() != null ? entry.getKey() : "uncaptured-group");
			}
			result.addAll(reducedMetrics);
		}
		
		return result;
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, @SuppressWarnings("unchecked") List<Metric>... metrics) {
		throw new UnsupportedOperationException("Group By Transform doesn't need list of list!");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.GROUPBY.name();
	}

}
