package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

@Category(IntegrationTest.class)
public class MetricScannerTransformSpeedTest extends AbstractTest {

	private static final Long MILLIS_IN_HOUR = (long) (1000 * 60 * 60);
	private static final Long MILLIS_IN_DAY = MILLIS_IN_HOUR * 24;
	private static final Long MILLIS_IN_WEEK = MILLIS_IN_DAY * 7;
	
	private static Double ratioSum = 0.0;
	private static int numTests = 0;
	
	private static Map<String, String> tags1 = getT1();
	private static Map<String, String> getT1() {
		Map<String, String> res = new HashMap<>();
		res.put("device", "isthbase01-dnds2-2-crd.eng.sfdc.net");
		return res;
	}
	private static Map<String, String> tags2 = getT2();
	private static Map<String, String> getT2() {
		Map<String, String> res = new HashMap<>();
		res.put("host", "shared1-argusalert1-1-prd.eng.sfdc.net");
		return res;
	}
	private static Map<String, String> tags3 = getT3();
	private static Map<String, String> getT3() {
		Map<String, String> res = new HashMap<>();
		res.put("device", "gs0-app1-1-chi.ops.sfdc.net");
		return res;
	}
	
	private final MetricQuery mq1 = new MetricQuery("system.CRD.CRDHBASE.isthbase01", "cpu.0.cpu.idle", tags1, System.currentTimeMillis() - MILLIS_IN_WEEK, System.currentTimeMillis());
	private final MetricQuery mq2 = new MetricQuery("argus.jvm", "mem.heap.used", tags2, System.currentTimeMillis() - MILLIS_IN_WEEK, System.currentTimeMillis());
	//private final MetricQuery mq3 = new MetricQuery("agent.CHI>SP1.gs0", "watchdog.component_health", tags3, System.currentTimeMillis() - MILLIS_IN_WEEK, System.currentTimeMillis());
	private final MetricQuery mq3 = new MetricQuery("system.CHI.SP1.gs0", "processes.logstash.ps_count.processes", tags3, System.currentTimeMillis() - MILLIS_IN_WEEK, System.currentTimeMillis());
	
	private List<MetricQuery> makeQueries() {
		List<MetricQuery> queries = new ArrayList<>();
		queries.add(mq1);
		queries.add(mq2);
		//queries.add(mq3);
		queries.add(mq3);
		return queries;
	}
	
	@Test
	public void testScannerOnIdentityTransformOneDayMetricFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		
		List<MetricQuery> queries = makeQueries();
		
		// one throw away iteration
		service.getMetrics(queries);
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		Transform transform = new IdentityTransform();
		
		for (MetricQuery q : resMetric.keySet()) {
			transform.transform(resMetric.get(q));
		}
		
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("\nMetric implementation took " + metricTime + " nanoseconds.\n");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		Transform transform2 = new IdentityTransform();
		
		for (MetricQuery q : resMetric.keySet()) {
			transform2.transformScanner(resScanner.get(q));
		}
		
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds.");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done second\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnIdentityTransformOneDayScannerFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		// one throw away implementation for caching
		service.getMetrics(queries);
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		Transform transform = new IdentityTransform();
		
		for (MetricQuery q : resScanner.keySet()) {
			transform.transformScanner(resScanner.get(q));
		}
		
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds.");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		Transform transform2 = new IdentityTransform();
		
		for (MetricQuery q : resMetric.keySet()) {
			transform2.transform(resMetric.get(q));
		}
		
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds.");

		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done first\n");
		System.out.println("(Lower is better)");
	}
	
	@Test
	public void testScannerOnKMeansTransformOneDayMetricFirst() {
		
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		//one throw away for caching
		service.getMetrics(queries);
		
		Transform transform = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("3");
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		
		for (MetricQuery q : resMetric.keySet()) {
			for (Metric m : resMetric.get(q)) {
				transform.transform(Arrays.asList(m), constants);
			}
		}
		
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		
		for (MetricQuery q : resScanner.keySet()) {
			for (MetricScanner s : resScanner.get(q)) {
				transform.transformScanner(Arrays.asList(s), constants);
			}
		}
		
		Long scannerTime = System.nanoTime() - startTime;	
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done second\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnKMeansTransformationOneDayScannerFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		//one throw away for caching
		service.getMetrics(queries);
		
		Transform transform = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("3");
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		
		for (MetricQuery q : resScanner.keySet()) {
			for (MetricScanner s : resScanner.get(q)) {
				transform.transformScanner(Arrays.asList(s), constants);
			}
		}
		
		Long scannerTime = System.nanoTime() - startTime;	
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		
		for (MetricQuery q : resMetric.keySet()) {
			for (Metric m : resMetric.get(q)) {
				transform.transform(Arrays.asList(m), constants);
			}
		}
		
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done first\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnInternalReducerTypeOneDayMetricFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		service.getMetrics(queries);
		
		Transform transform = new MetricFilterWithInteralReducerTransform(new BelowValueFilter());
		List<String> constants = new ArrayList<>();
		constants.add("82");
		constants.add("min");
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		
		for (MetricQuery q : resMetric.keySet()) {
			transform.transform(resMetric.get(q), constants);
		}
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		
		for (MetricQuery q : resScanner.keySet()) {
			transform.transformScanner(resScanner.get(q), constants);
		}
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done second\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnInternalReducerTypeOneDayScannerFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		service.getMetrics(queries);
		
		Transform transform = new MetricFilterWithInteralReducerTransform(new BelowValueFilter());
		List<String> constants = new ArrayList<>();
		constants.add("82");
		constants.add("min");
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		
		for (MetricQuery q : resScanner.keySet()) {
			transform.transformScanner(resScanner.get(q), constants);
		}
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		
		for (MetricQuery q : resMetric.keySet()) {
			transform.transform(resMetric.get(q), constants);
		}
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done first\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnHoltWintersDeviationOneDayMetricFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		service.getMetrics(queries);
		
		Transform transform = new HoltWintersDeviation(service);
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble());
		constants.add("" + random.nextDouble());
		constants.add("" + random.nextDouble());
		constants.add("" + random.nextInt());
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		for (List<Metric> l : resMetric.values()) {
			transform.transform(l, constants);
		}
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		for (List<MetricScanner> l : resScanner.values()) {
			transform.transformScanner(l, constants);
		}
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done second\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnHoltWintersDeviationOneDayScannerFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		service.getMetrics(queries);
		
		Transform transform = new HoltWintersDeviation(service);
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble());
		constants.add("" + random.nextDouble());
		constants.add("" + random.nextDouble());
		constants.add("" + random.nextInt());
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		for (List<MetricScanner> l : resScanner.values()) {
			transform.transformScanner(l, constants);
		}
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		for (List<Metric> l : resMetric.values()) {
			transform.transform(l, constants);
		}
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done first\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnJoinTransformThreeMetricsMetricFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		service.getMetrics(queries);
		
		Transform transform = new JoinTransform();
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		List<List<Metric>> nestedList = new ArrayList<>();
		for (List<Metric> l : resMetric.values()) {
			nestedList.add(l);
		}
		transform.transform(nestedList.get(0), nestedList.get(1), nestedList.get(2));
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		List<List<MetricScanner>> nestedListS = new ArrayList<>();
		for (List<MetricScanner> l : resScanner.values()) {
			nestedListS.add(l);
		}
		transform.transformScanner(nestedListS.get(0), nestedListS.get(1), nestedListS.get(2));
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done second\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testScannerOnJoinTransformThreeMetricsScannerFirst() {
		TSDBService service = system.getServiceFactory().getTSDBService();
		List<MetricQuery> queries = makeQueries();
		
		service.getMetrics(queries);
		
		Transform transform = new JoinTransform();
		
		Long startTime = System.nanoTime();
		Map<MetricQuery, List<MetricScanner>> resScanner = service.getMetricScanners(queries);
		List<List<MetricScanner>> nestedList = new ArrayList<>();
		for (List<MetricScanner> l : resScanner.values()) {
			nestedList.add(l);
		}
		transform.transformScanner(nestedList.get(0), nestedList.get(1), nestedList.get(2));
		Long scannerTime = System.nanoTime() - startTime;
		System.out.println("Scanner implementation took " + scannerTime + " nanoseconds");
		
		startTime = System.nanoTime();
		Map<MetricQuery, List<Metric>> resMetric = service.getMetrics(queries);
		List<List<Metric>> nestedListM = new ArrayList<>();
		for (List<Metric> l : resMetric.values()) {
			nestedListM.add(l);
		}
		transform.transform(nestedListM.get(0), nestedListM.get(1), nestedListM.get(2));
		Long metricTime = System.nanoTime() - startTime;
		System.out.println("Metric implementation took " + metricTime + " nanoseconds");
		
		System.out.println("\nThe scanner took " + (((double) scannerTime) / metricTime) + " of the metric time when done first\n");
		System.out.println("(Lower is better)");
		
		ratioSum += ((double) scannerTime) / metricTime;
		numTests++;
	}
	
	@Test
	public void testOverallPerformanceHere() {
		System.out.println("\n\nThe overall ratio of scanner time to metric time is " + ratioSum / numTests);
	}
}
