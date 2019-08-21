package com.salesforce.dva.argus.service.metric.transform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.TransformUtil;
/**
 * It provides methods to implement Slice transform
 * @author Raj Sarkapally
 *
 */
public class SliceTransform implements Transform{
	private static String START_TIME = "start";
	private static String END_TIME = "end";
	private static long SECOND_IN_MILLI=1000l;

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		throw new UnsupportedOperationException("Slice Transform needs interval start time and end time.");
	}

	@Override
	public List<Metric> transform(QueryContext queryContext,
			List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(constants != null, "Slice Transform needs interval start time and end time.");
		SystemAssert.requireArgument(constants.size() == 2, "Slice Transform must provide exactly 2 constants which are interval start time and interval end time.");

		String startEndTimePattern= "("+ START_TIME + "|"+ END_TIME +")(\\s*[+-]\\s*\\d+[smhd])?";
		String sliceStartTime = constants.get(0).trim();
		String sliceEndTime = constants.get(1).trim();
		SystemAssert.requireArgument((isLong(sliceStartTime) || sliceStartTime.matches(startEndTimePattern)), "The start time of Slice transform is invalid.");
		SystemAssert.requireArgument((isLong(sliceEndTime) || sliceEndTime.matches(startEndTimePattern)), "The end time of Slice transform is invalid.");
		
		long sliceStartTimeInMilli = calculateTime(sliceStartTime, queryContext.getChildContexts().get(0).getExpression().getStartTimestamp(), 
				queryContext.getChildContexts().get(0).getExpression().getEndTimestamp());
		
		long sliceEndTimeInMilli = calculateTime(sliceEndTime, queryContext.getChildContexts().get(0).getExpression().getStartTimestamp(), 
				queryContext.getChildContexts().get(0).getExpression().getEndTimestamp());
		
		metrics.forEach(metric -> {
			Map<Long, Double> slicedDatapoints = new HashMap<>();
			metric.getDatapoints().forEach((timestamp,value) ->{
				if(timestamp >= sliceStartTimeInMilli && timestamp <=sliceEndTimeInMilli) {
					slicedDatapoints.put(timestamp, value);
				}
			});
			metric.setDatapoints(slicedDatapoints);
		});
		return metrics;
	}

	@Override
	public List<Metric> transform(QueryContext queryContext,
			List<Metric>... metrics) {
		throw new UnsupportedOperationException("Slice Transform doesn't need list of list.");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.SLICE.name();
	}

	private long calculateTime(String time,long queryStartTime, long queryEndTime) {
		if(isLong(time)) {
			return Long.valueOf(time);
		}else {
			long startREndtime;
			String remTimeString;
			if(time.contains(START_TIME)) {
				startREndtime=queryStartTime;
				remTimeString=time.substring(START_TIME.length()).trim();
				if(remTimeString.isEmpty()) {
					return queryStartTime;
				}
			}else {
				startREndtime=queryEndTime;
				remTimeString=time.substring(END_TIME.length()).trim();
				if(remTimeString.isEmpty()) {
					return queryEndTime;
				}
			}
			return calculate(startREndtime, remTimeString.charAt(0), SECOND_IN_MILLI * TransformUtil.getWindowInSeconds(remTimeString.substring(1).trim()));
		}
	}
	
	private long calculate(long operand1, char operator, long operand2) {
		switch(operator) {
			case '+':
				return operand1 + operand2;
			case '-':
				return operand1 - operand2;
			case '*':
				return operand1 * operand2;
			case '/':
				return operand1/operand2;
			default: 
				return operand1-operand2;
		}
	}
	
	private boolean isLong(String s) {
		try {
			Long.valueOf(s);
			return true;
		}catch(NumberFormatException e) {
			return false;
		}catch(Throwable t) {
			return false;
		}
	}
}
