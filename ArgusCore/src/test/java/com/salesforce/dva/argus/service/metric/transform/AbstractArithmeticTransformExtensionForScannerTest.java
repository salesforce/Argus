package com.salesforce.dva.argus.service.metric.transform;

import java.util.List;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class AbstractArithmeticTransformExtensionForScannerTest extends AbstractArithmeticTransform {

	protected Double performOperation(List<Double> operands) {
		Double sum = 0.0;
		for (Double val : operands) {
			sum += val;
		}
		return sum;
	}
	
	@Override
	public List<Metric> transform(List<Metric> metrics, List<String> constants) {
		throw new UnsupportedOperationException("Don't use this sum implementation with a constant!");
	}
	
	@Override
	public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
		throw new UnsupportedOperationException("Don't use this sum implementation with a constant!");
	}
	
	@Override
	public List<Metric> transform(List<Metric>... listOfList) {
		throw new UnsupportedOperationException("This sum implementation doesn't take list of list!!!");
	}
	
	@Override
	public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
		throw new UnsupportedOperationException("This sum implementation doesn't take list of list!!!");
	}
	
	@Override
	public String getResultScopeName() {
		return "Testing implementation of AbstractArithmeticTransform";
	}
}
