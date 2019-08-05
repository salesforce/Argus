package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.TSDBQueryExpression;

public class SliceTransformTest {
	private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private static final long SECOND=1000l;
    
    @Test
    public void testWithAbsoluteTime() {
    	SliceTransform transform = new SliceTransform();
    	
    	Metric actualMetric = new Metric(TEST_SCOPE, TEST_METRIC);
    	Map<Long, Double> actualDps = new HashMap<Long, Double>();
    	actualDps.put(1l, 1d);
    	actualDps.put(2l, 2d);
    	actualDps.put(3l, 3d);
    	actualDps.put(4l, 4d);
    	actualDps.put(5l, 5d);
    	
    	actualMetric.setDatapoints(actualDps);
    	
    	List<String> constants = new ArrayList<String>();
    	constants.add("2");
    	constants.add("4");
    	
    	QueryContext context = new QueryContext();
    	
    	QueryContext childContext = new QueryContext();
    	TSDBQueryExpression exp = new TSDBQueryExpression();
    	exp.setStartTimestamp(1l);
    	exp.setEndTimestamp(6l);
    	childContext.setExpression(exp);
    	
    	context.setChildContexts(Arrays.asList(childContext)); 
    	
    	List<Metric> actual = transform.transform(context, Arrays.asList(actualMetric),constants);
    	
    	Metric expectedMetric = new Metric(TEST_SCOPE, TEST_METRIC);
    	Map<Long, Double> expectedDps = new HashMap<Long, Double>();
    	expectedDps.put(2l, 2d);
    	expectedDps.put(3l, 3d);
    	expectedDps.put(4l, 4d);
    	
    	expectedMetric.setDatapoints(expectedDps);
    	
    	List<Metric> expected = Arrays.asList(expectedMetric);
    	
    	assertEquals(expected.get(0), actual.get(0));
    	assertEquals(expected.get(0).getDatapoints(), actual.get(0).getDatapoints());
    }
    
    @Test
    public void testWithRelativeTime() {
    	SliceTransform transform = new SliceTransform();
    	
    	Metric actualMetric = new Metric(TEST_SCOPE, TEST_METRIC);
    	Map<Long, Double> actualDps = new HashMap<Long, Double>();
    	actualDps.put(1*SECOND, 1d);
    	actualDps.put(2*SECOND, 2d);
    	actualDps.put(3*SECOND, 3d);
    	actualDps.put(4*SECOND, 4d);
    	actualDps.put(5*SECOND, 5d);
    	actualDps.put(6*SECOND, 6d);
    	actualDps.put(7*SECOND, 7d);
    	
    	actualMetric.setDatapoints(actualDps);
    	
    	List<String> constants = new ArrayList<String>();
    	constants.add("start + 2s");
    	constants.add("end-2s");
    	
    	QueryContext context = new QueryContext();
    	QueryContext childContext = new QueryContext();
    	TSDBQueryExpression exp = new TSDBQueryExpression();
    	exp.setStartTimestamp(1*SECOND);
    	exp.setEndTimestamp(7*SECOND);
    	childContext.setExpression(exp);
    	
    	context.setChildContexts(Arrays.asList(childContext)); 
    	List<Metric> actual = transform.transform(context, Arrays.asList(actualMetric),constants);
    	
    	Metric expectedMetric = new Metric(TEST_SCOPE, TEST_METRIC);
    	Map<Long, Double> expectedDps = new HashMap<Long, Double>();
    	expectedDps.put(3*SECOND, 3d);
    	expectedDps.put(4*SECOND, 4d);
    	expectedDps.put(5*SECOND, 5d);
    	
    	expectedMetric.setDatapoints(expectedDps);
    	
    	List<Metric> expected = Arrays.asList(expectedMetric);
    	
    	assertEquals(expected.get(0), actual.get(0));
    	assertEquals(expected.get(0).getDatapoints(), actual.get(0).getDatapoints());
    }
}
