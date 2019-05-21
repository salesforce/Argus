package com.salesforce.dva.argus.service.metric.transform;

import java.util.List;
import java.util.Map;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

public class AliasByTagTransform implements Transform {
	
	public static final String DELIMITER = ",";
	public static final String DEFAULT_SCOPE_NAME=TransformFactory.Function.ALIASBYTAG.name();

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		SystemAssert.requireArgument(metrics != null, "Metric list cannot be null.");
		
		for(Metric metric : metrics) {
			String displayName = "";
			
			for(Map.Entry<String, String> entry : metric.getTags().entrySet()) {
				displayName += entry.getValue() + DELIMITER;
			}
			
			if(!displayName.isEmpty()) {
				displayName = displayName.substring(0, displayName.length() - 1);
				metric.setDisplayName(displayName);
				metric.setScope(DEFAULT_SCOPE_NAME);
				metric.setMetric(displayName);
				metric.setTags(null);
			}
		}
		return metrics;
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
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
				metric.setScope(DEFAULT_SCOPE_NAME);
				metric.setMetric(displayName);
				metric.setTags(null);
			}
		}
		return metrics;
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, @SuppressWarnings("unchecked") List<Metric>... metrics) {
		throw new UnsupportedOperationException("ALIASBYTAG doesn't support multiple lists of metrics!");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.ALIASBYTAG.name();
	}

}
