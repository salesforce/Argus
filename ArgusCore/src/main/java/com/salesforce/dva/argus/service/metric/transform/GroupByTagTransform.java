package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

public class GroupByTagTransform implements Transform {

	private TransformFactory _factory;

	public GroupByTagTransform(TransformFactory transformFactory) {
		_factory = transformFactory;
	}

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		throw new UnsupportedOperationException("GroupByTag Transform is supposed to be used with 2 constants: A list of tags to group by and an aggregator function name.");
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics");
		SystemAssert.requireArgument(constants != null && constants.size() >= 2, "Constants list cannot be null and its size must be 2 or more.");

		// Find a constant with a function name: expected syntax is: tagk[,tagk]*,transform[,transformConstant]*
		// scanning back to find the transform function
		TransformFactory.Function functionName = null;
		int functionElementId = -1;

		for (int i = constants.size() - 1; i >= 0; i--) {
        	String constant = constants.get(i);
        	try {
        		functionName = TransformFactory.Function.valueOf(constant);
        		functionElementId = i;
        		break;
			}
			catch (IllegalArgumentException ex) {
        		// move on
			}
		}

		if (functionName == null) {
			throw new UnsupportedOperationException("GroupByTag needs a function name to be provided");
		}

		List<String> tags = constants.subList(0, functionElementId);
		if (tags.isEmpty()) {
			throw new UnsupportedOperationException("GroupByTag needs at least one tag to be provided");
		}

		List<String> transformConstants = functionElementId == constants.size() - 1 ?
			new ArrayList<>() : constants.subList(functionElementId + 1, constants.size());

		Map<String, List<Metric>> groups = new HashMap<>();
		for(Metric metric : metrics) {
			String key = tags.stream()
				.map(metric::getTag)
                .filter(Objects::nonNull)
				.collect(Collectors.joining(","));

            groups.putIfAbsent(key, new ArrayList<>());
            groups.get(key).add(metric);
		}

		Transform transform = _factory.getTransform(functionName.getName());
		List<Metric> result = new ArrayList<>();
		for(Entry<String, List<Metric>> entry : groups.entrySet()) {
			List<Metric> metricsInThisGroup = entry.getValue();
			List<Metric> reducedMetrics = transformConstants.isEmpty() ?
				transform.transform(null, metricsInThisGroup) : transform.transform(null, metricsInThisGroup, transformConstants);
			for(Metric reducedMetric : reducedMetrics) {
				reducedMetric.setScope(entry.getKey() != null && ! entry.getKey().trim().isEmpty() ?
					entry.getKey() : "uncaptured-group");
			}
			result.addAll(reducedMetrics);
		}
		
		return result;
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, @SuppressWarnings("unchecked") List<Metric>... metrics) {
		throw new UnsupportedOperationException("Group By Tags Transform doesn't need list of list!");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.GROUPBYTAG.name();
	}

}
