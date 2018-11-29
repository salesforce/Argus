package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.QueryUtils;
import com.salesforce.dva.argus.util.TransformUtil;
/**
 * It provides methods to implement Rate transform
 * @author Raj Sarkapally (rsarkapally@salesforce.com)
 *
 */
public class RateTransform implements Transform{
	private static long DEFAULT_INTERVAL=60*1000;
	private static boolean DEFAULT_SKIP_NEGATIVE_VALUES=true;
	private static boolean DEFAULT_INTERPOLATE_MISSING_DATAPOINTS=true;

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics) {
		Long[] startAndEndTimestamps = QueryUtils.getStartAndEndTimesWithMaxInterval(queryContext);
		return performRate(metrics, startAndEndTimestamps[0], startAndEndTimestamps[1], DEFAULT_INTERVAL, 
				DEFAULT_SKIP_NEGATIVE_VALUES, DEFAULT_INTERPOLATE_MISSING_DATAPOINTS);
	}

	@Override
	public List<Metric> transform(QueryContext queryContext,
			List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(constants != null && constants.size() == 3,
				"Rate Transform needs 3 constants (interval, skipNegativeValues, interpolateMissingValues)!");

		Long[] startAndEndTimestamps = QueryUtils.getStartAndEndTimesWithMaxInterval(queryContext);
		long intervalInMilli = TransformUtil.getWindowInSeconds(constants.get(0)) * 1000;
		return performRate(metrics, startAndEndTimestamps[0], startAndEndTimestamps[1], intervalInMilli, 
				Boolean.valueOf(constants.get(1)), Boolean.valueOf(constants.get(2)));
	}

	@Override
	public List<Metric> transform(QueryContext queryContext,
			List<Metric>... metrics) {
		throw new UnsupportedOperationException("Rate transform doesn't need list of list");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.RATE.name();
	}

	private List<Metric> performRate(List<Metric> metrics,long startTimestampInMilli, long endTimestampInMilli, long intervalInMilli,
			boolean skipNegativeValues, boolean interpolateMissingDP){
		List<Metric> result= new ArrayList<>();
		for(Metric metric:metrics) {
			if(metric.getDatapoints().size()>=2) {
				TreeMap<Long, Double> sortedDatapoints = new TreeMap<>(metric.getDatapoints());
				startTimestampInMilli = startTimestampInMilli > 0 ? startTimestampInMilli:sortedDatapoints.firstKey();
				endTimestampInMilli = endTimestampInMilli > 0 ?endTimestampInMilli:sortedDatapoints.lastKey();
				if(interpolateMissingDP) {
					addFirstNLastDatapointsIfMissing(sortedDatapoints, startTimestampInMilli, endTimestampInMilli, intervalInMilli);
					sortedDatapoints=performInterpolation(sortedDatapoints, intervalInMilli);
				}
				sortedDatapoints = calculateRateValues(sortedDatapoints, intervalInMilli);
				if(skipNegativeValues) {
					sortedDatapoints = removeNegativeValues(sortedDatapoints);
				}
				metric.setDatapoints(sortedDatapoints);
				result.add(metric);
			}else {
				result.add(metric);
			}
		}
		return result;
	}

	public TreeMap<Long, Double> performInterpolation(TreeMap<Long, Double> sortedDatapoints, long intervalInMilli) {
		if(sortedDatapoints.size()<2) {
			return sortedDatapoints;
		}
		TreeMap<Long, Double> result = new TreeMap<>();
		Long prevTimestamp = sortedDatapoints.firstKey();
		Entry<Long, Double> prevDP = sortedDatapoints.firstEntry();
		for(Entry<Long, Double> currDP:sortedDatapoints.entrySet()) {
			while(currDP.getKey() > (prevTimestamp+intervalInMilli)) {
				Long missingTimestamp = prevTimestamp+intervalInMilli;
				Double missingValue= getInterpolatedvalue(prevDP, currDP, missingTimestamp);
				result.put(missingTimestamp, missingValue);
				prevTimestamp = missingTimestamp;
			}
			result.put(currDP.getKey(), currDP.getValue());
			prevDP=currDP;
			prevTimestamp=currDP.getKey();
		}
		return result;
	}

	private TreeMap<Long, Double> removeNegativeValues(TreeMap<Long, Double> datapoints){
		TreeMap<Long, Double> result = new TreeMap<>();
		for(Entry<Long, Double> entry:datapoints.entrySet()) {
			if(entry.getValue()>=0) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		return result;
	}

	private TreeMap<Long, Double> calculateRateValues(TreeMap<Long, Double> sortedDatapoints, long intervalInMilli) {
		TreeMap<Long, Double> result = new TreeMap<>();
		Entry<Long, Double> prevEntry = null;
		for (Entry<Long, Double> currEntry : sortedDatapoints.entrySet()) {
			if (prevEntry !=null){
				double rateValue = intervalInMilli * (currEntry.getValue()-prevEntry.getValue())/(currEntry.getKey()-prevEntry.getKey());
				result.put(currEntry.getKey(), rateValue);
			}
			prevEntry = currEntry;
		}
		return result;
	}

	private void addFirstNLastDatapointsIfMissing(TreeMap<Long,Double> sortedDatapoints, long startTimestampInMilli, long endTimestampInMilli, long intervalInMilli) {
		if(sortedDatapoints.size()>=2) {
			if(sortedDatapoints.firstKey() >= (startTimestampInMilli + intervalInMilli)) {
				double firstDPValue= getInterpolatedvalue(sortedDatapoints.firstEntry(), sortedDatapoints.higherEntry(sortedDatapoints.firstKey()), startTimestampInMilli);
				sortedDatapoints.put(startTimestampInMilli, firstDPValue);
			}
			if(endTimestampInMilli >= (sortedDatapoints.lastKey()+intervalInMilli)) {
				double lastDPValue= getInterpolatedvalue(sortedDatapoints.lowerEntry(sortedDatapoints.lastKey()), sortedDatapoints.lastEntry(), endTimestampInMilli);
				sortedDatapoints.put(endTimestampInMilli, lastDPValue);
			}
		}
	}

	private double getInterpolatedvalue(Entry<Long, Double> prevDP, Entry<Long, Double> nextDP, long timestamp){
		double slope = (nextDP.getValue()-prevDP.getValue())/(nextDP.getKey()-prevDP.getKey());
		double result = prevDP.getValue() + slope*(timestamp-prevDP.getKey()); 
		return result;
	}
}
