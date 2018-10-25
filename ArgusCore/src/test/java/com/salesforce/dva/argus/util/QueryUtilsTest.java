package com.salesforce.dva.argus.util;

import static org.junit.Assert.*;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;

public class QueryUtilsTest extends AbstractTest {

	@Test
	public void testGetQueryContextWithSingleExpression() {
		long relativeTo = System.currentTimeMillis();
		QueryContext context = QueryUtils.getQueryContext("-1h:argus.core:alerts.scheduled:zimsum:1m-sum", relativeTo);
		assertNull(context.getTransform());
		assertNull(context.getConstants());
		assertEquals(context.getChildContexts().size(),0);
		assertEquals(context.getChildExpressions().size(),1);
		TSDBQueryExpression expression = context.getChildExpressions().get(0);
		assertEquals(expression.getScope(), "argus.core");
		assertEquals(expression.getMetric(), "alerts.scheduled");
		assertEquals(expression.getAggregator().toString(), "ZIMSUM");
		assertEquals(expression.getDownsampler().toString(), "SUM");
		assertEquals(expression.getDownsamplingPeriod(), new Long(60000));
		assertEquals(expression.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
		assertEquals(expression.getEndTimestamp(),new Long(relativeTo));
	}
	
	@Test
	public void testGetQueryWithSingleTransform() {
		long relativeTo = System.currentTimeMillis();
		QueryContext context = QueryUtils.getQueryContext("DOWNSAMPLE(-1h:argus.core:alerts.scheduled:zimsum:1m-sum, #1h-sum#)", relativeTo);
		assertEquals(context.getTransform(), Function.DOWNSAMPLE);
		assertEquals(context.getConstants().size(), 1);
		assertEquals(context.getConstants().get(0), "1h-sum");
		assertEquals(context.getChildContexts().size(),0);
		assertEquals(context.getChildExpressions().size(),1);
		TSDBQueryExpression expression = context.getChildExpressions().get(0);
		assertEquals(expression.getScope(), "argus.core");
		assertEquals(expression.getMetric(), "alerts.scheduled");
		assertEquals(expression.getAggregator().toString(), "ZIMSUM");
		assertEquals(expression.getDownsampler().toString(), "SUM");
		assertEquals(expression.getDownsamplingPeriod(), new Long(60000));
		assertEquals(expression.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
		assertEquals(expression.getEndTimestamp(),new Long(relativeTo));
	}
	
	@Test
	public void testGetQueryWithNestedTransform() {
		long relativeTo = System.currentTimeMillis();
		QueryContext context = QueryUtils.getQueryContext("UNION(DOWNSAMPLE(-1h:argus.core:alerts.scheduled:zimsum:1m-sum, #1h-sum#), SUM(-1h:argus.core:alerts.scheduled:zimsum:1m-sum, -1h:argus.core:alerts.evaluated:zimsum:1m-sum))", relativeTo);
		assertEquals(context.getTransform(), Function.UNION);
		assertEquals(context.getConstants().size(), 0);
		assertEquals(context.getChildContexts().size(),2);
		assertEquals(context.getChildExpressions().size(),0);
		
		QueryContext context1 = context.getChildContexts().get(0);
		assertEquals(context1.getTransform(), Function.DOWNSAMPLE);
		assertEquals(context1.getConstants().size(), 1);
		assertEquals(context1.getConstants().get(0), "1h-sum");
		assertEquals(context1.getChildContexts().size(),0);
		assertEquals(context1.getChildExpressions().size(),1);
		TSDBQueryExpression expression = context1.getChildExpressions().get(0);
		assertEquals(expression.getScope(), "argus.core");
		assertEquals(expression.getMetric(), "alerts.scheduled");
		assertEquals(expression.getAggregator().toString(), "ZIMSUM");
		assertEquals(expression.getDownsampler().toString(), "SUM");
		assertEquals(expression.getDownsamplingPeriod(), new Long(60000));
		assertEquals(expression.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
		assertEquals(expression.getEndTimestamp(),new Long(relativeTo));
		
		QueryContext context2 = context.getChildContexts().get(1);
		assertEquals(context2.getTransform(), Function.SUM);
		assertEquals(context2.getConstants().size(), 0);
		assertEquals(context2.getChildContexts().size(),0);
		assertEquals(context2.getChildExpressions().size(),2);
		
		TSDBQueryExpression expression1 = context2.getChildExpressions().get(0);
		assertEquals(expression1.getScope(), "argus.core");
		assertEquals(expression1.getMetric(), "alerts.scheduled");
		assertEquals(expression1.getAggregator().toString(), "ZIMSUM");
		assertEquals(expression1.getDownsampler().toString(), "SUM");
		assertEquals(expression1.getDownsamplingPeriod(), new Long(60000));
		assertEquals(expression1.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
		assertEquals(expression1.getEndTimestamp(),new Long(relativeTo));
		
		TSDBQueryExpression expression2 = context2.getChildExpressions().get(1);
		assertEquals(expression2.getScope(), "argus.core");
		assertEquals(expression2.getMetric(), "alerts.evaluated");
		assertEquals(expression2.getAggregator().toString(), "ZIMSUM");
		assertEquals(expression2.getDownsampler().toString(), "SUM");
		assertEquals(expression2.getDownsamplingPeriod(), new Long(60000));
		assertEquals(expression2.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
		assertEquals(expression2.getEndTimestamp(),new Long(relativeTo));
	}
}
