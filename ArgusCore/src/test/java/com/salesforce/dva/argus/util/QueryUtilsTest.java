package com.salesforce.dva.argus.util;

import static org.junit.Assert.*;

import java.util.List;

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
        assertNotNull(context.getExpression());
        TSDBQueryExpression expression = context.getExpression();
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
        assertEquals(context.getChildContexts().size(),1);
        assertNull(context.getExpression());
        assertNull(context.getChildContexts().get(0).getTransform());
        assertNotNull(context.getChildContexts().get(0).getExpression());
        TSDBQueryExpression expression = context.getChildContexts().get(0).getExpression();
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
        assertNull(context.getExpression());

        QueryContext context1 = context.getChildContexts().get(0);
        assertEquals(context1.getTransform(), Function.DOWNSAMPLE);
        assertEquals(context1.getConstants().size(), 1);
        assertEquals(context1.getConstants().get(0), "1h-sum");
        assertEquals(context1.getChildContexts().size(),1);
        assertNull(context1.getExpression());
        assertNotNull(context1.getChildContexts().get(0).getExpression());
        assertNull(context.getExpression());
        TSDBQueryExpression expression = context1.getChildContexts().get(0).getExpression();
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
        assertEquals(context2.getChildContexts().size(),2);
        assertNull(context2.getExpression());

        TSDBQueryExpression expression1 = context2.getChildContexts().get(0).getExpression();
        assertEquals(expression1.getScope(), "argus.core");
        assertEquals(expression1.getMetric(), "alerts.scheduled");
        assertEquals(expression1.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression1.getDownsampler().toString(), "SUM");
        assertEquals(expression1.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression1.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression1.getEndTimestamp(),new Long(relativeTo));

        TSDBQueryExpression expression2 = context2.getChildContexts().get(1).getExpression();
        assertEquals(expression2.getScope(), "argus.core");
        assertEquals(expression2.getMetric(), "alerts.evaluated");
        assertEquals(expression2.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression2.getDownsampler().toString(), "SUM");
        assertEquals(expression2.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression2.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression2.getEndTimestamp(),new Long(relativeTo));
    }

    @Test
    public void testGetQueryWithNestedTransformWithDivide1() {
        long relativeTo = System.currentTimeMillis();
        QueryContext context = QueryUtils.getQueryContext("DIVIDE(-1h:argus.core:alerts.scheduled:zimsum:1m-sum, SUM(-1h:argus.core:alerts.scheduled:zimsum:1m-sum, -1h:argus.core:alerts.evaluated:zimsum:1m-sum))", relativeTo);
        assertEquals(context.getTransform(), Function.DIVIDE);
        assertEquals(context.getConstants().size(), 0);
        assertEquals(context.getChildContexts().size(),2);
        assertNull(context.getExpression());

        QueryContext context1 = context.getChildContexts().get(0);
        assertEquals(context1.getTransform(), null);
        assertNull(context1.getConstants());
        assertEquals(context1.getChildContexts().size(),0);
        assertNotNull(context1.getExpression());
        TSDBQueryExpression expression = context1.getExpression();
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
        assertEquals(context2.getChildContexts().size(),2);
        assertNull(context2.getExpression());

        TSDBQueryExpression expression1 = context2.getChildContexts().get(0).getExpression();
        assertEquals(expression1.getScope(), "argus.core");
        assertEquals(expression1.getMetric(), "alerts.scheduled");
        assertEquals(expression1.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression1.getDownsampler().toString(), "SUM");
        assertEquals(expression1.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression1.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression1.getEndTimestamp(),new Long(relativeTo));

        TSDBQueryExpression expression2 = context2.getChildContexts().get(1).getExpression();
        assertEquals(expression2.getScope(), "argus.core");
        assertEquals(expression2.getMetric(), "alerts.evaluated");
        assertEquals(expression2.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression2.getDownsampler().toString(), "SUM");
        assertEquals(expression2.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression2.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression2.getEndTimestamp(),new Long(relativeTo));
    }
    
    @Test
    public void testGetQueryWithNestedTransformWithDivide2() {
        long relativeTo = System.currentTimeMillis();
        QueryContext context = QueryUtils.getQueryContext("DIVIDE(SUM(-1h:argus.core:alerts.scheduled:zimsum:1m-sum, -1h:argus.core:alerts.evaluated:zimsum:1m-sum), -1h:argus.core:alerts.scheduled:zimsum:1m-sum)", relativeTo);
        assertEquals(context.getTransform(), Function.DIVIDE);
        assertEquals(context.getConstants().size(), 0);
        assertEquals(context.getChildContexts().size(),2);
        assertNull(context.getExpression());

        QueryContext context1 = context.getChildContexts().get(1);
        assertEquals(context1.getTransform(), null);
        assertNull(context1.getConstants());
        assertEquals(context1.getChildContexts().size(),0);
        assertNotNull(context1.getExpression());
        TSDBQueryExpression expression = context1.getExpression();
        assertEquals(expression.getScope(), "argus.core");
        assertEquals(expression.getMetric(), "alerts.scheduled");
        assertEquals(expression.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression.getDownsampler().toString(), "SUM");
        assertEquals(expression.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression.getEndTimestamp(),new Long(relativeTo));

        QueryContext context2 = context.getChildContexts().get(0);
        assertEquals(context2.getTransform(), Function.SUM);
        assertEquals(context2.getConstants().size(), 0);
        assertEquals(context2.getChildContexts().size(),2);
        assertNull(context2.getExpression());

        TSDBQueryExpression expression1 = context2.getChildContexts().get(0).getExpression();
        assertEquals(expression1.getScope(), "argus.core");
        assertEquals(expression1.getMetric(), "alerts.scheduled");
        assertEquals(expression1.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression1.getDownsampler().toString(), "SUM");
        assertEquals(expression1.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression1.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression1.getEndTimestamp(),new Long(relativeTo));

        TSDBQueryExpression expression2 = context2.getChildContexts().get(1).getExpression();
        assertEquals(expression2.getScope(), "argus.core");
        assertEquals(expression2.getMetric(), "alerts.evaluated");
        assertEquals(expression2.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression2.getDownsampler().toString(), "SUM");
        assertEquals(expression2.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression2.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression2.getEndTimestamp(),new Long(relativeTo));
    }


    @Test
    public void testEmptyFillTransform() {
        long relativeTo = System.currentTimeMillis();
        QueryContext context = QueryUtils.getQueryContext("FILL(#-1d#,#-0d#,#4h#,#0m#,#100#)", relativeTo);
        assertEquals(context.getTransform(), Function.FILL);
        assertEquals(context.getConstants().size(), 5);
        assertEquals(context.getConstants().get(0), "-1d");
        assertEquals(context.getConstants().get(1), "-0d");
        assertEquals(context.getConstants().get(2), "4h");
        assertEquals(context.getConstants().get(3), "0m");
        assertEquals(context.getConstants().get(4), "100");
        assertEquals(context.getChildContexts().size(),0);
        assertNull(context.getExpression());
    }
    
    @Test
    public void testThreeLevelsNestedTransform() {
        long relativeTo = System.currentTimeMillis();
        QueryContext context = QueryUtils.getQueryContext("SUM(DOWNSAMPLE(UNION(-1h:argus.core:alerts.scheduled:zimsum:1m-sum,-1h:argus.core:alerts.evaluated:zimsum:1m-sum),#1m-avg#),#union#)", relativeTo);
        assertEquals(context.getTransform(), Function.SUM);
        assertEquals(context.getConstants().size(), 1);
        assertEquals(context.getConstants().get(0), "union");
        assertEquals(context.getChildContexts().size(),1);
        assertNull(context.getExpression());

        QueryContext context1 = context.getChildContexts().get(0);
        assertEquals(context1.getTransform(), Function.DOWNSAMPLE);
        assertEquals(context1.getConstants().size(), 1);
        assertEquals(context1.getConstants().get(0), "1m-avg");
        assertEquals(context1.getChildContexts().size(),1);
        assertNull(context1.getExpression());
        
        QueryContext context2 = context1.getChildContexts().get(0);
        assertEquals(context2.getTransform(), Function.UNION);
        assertEquals(context2.getConstants().size(), 0);
        assertEquals(context2.getChildContexts().size(),2);
        assertNull(context2.getExpression());
        
        TSDBQueryExpression expression = context2.getChildContexts().get(0).getExpression();
        assertEquals(expression.getScope(), "argus.core");
        assertEquals(expression.getMetric(), "alerts.scheduled");
        assertEquals(expression.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression.getDownsampler().toString(), "SUM");
        assertEquals(expression.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression.getEndTimestamp(),new Long(relativeTo));


        TSDBQueryExpression expression1 = context2.getChildContexts().get(1).getExpression();
        assertEquals(expression1.getScope(), "argus.core");
        assertEquals(expression1.getMetric(), "alerts.evaluated");
        assertEquals(expression1.getAggregator().toString(), "ZIMSUM");
        assertEquals(expression1.getDownsampler().toString(), "SUM");
        assertEquals(expression1.getDownsamplingPeriod(), new Long(60000));
        assertEquals(expression1.getStartTimestamp(),new Long(((relativeTo - 3600*1000)/1000)*1000));
        assertEquals(expression1.getEndTimestamp(),new Long(relativeTo));
    }
    
    @Test
    public void getScopesFromExpression() {
        List<String> scopes = QueryUtils.getScopesFromExpression("SUM(DOWNSAMPLE(UNION(-1h:argus.core:alerts.scheduled:zimsum:1m-sum,-1h:argus.core:alerts.evaluated:zimsum:1m-sum),#1m-avg#),#union#)");
        assertEquals(scopes.size(),1);
        assertEquals(scopes.get(0),"argus.core");
        
        scopes = QueryUtils.getScopesFromExpression("FILL(#-1d#,#-0d#,#4h#,#0m#,#100#)");
        assertEquals(scopes.size(),0);
        
        scopes = QueryUtils.getScopesFromExpression("DIVIDE(SUM(-1h:argus.core1:alerts.scheduled:zimsum:1m-sum, -1h:argus.core2:alerts.evaluated:zimsum:1m-sum), -1h:argus.core3:alerts.scheduled:zimsum:1m-sum)");
        assertEquals(scopes.size(),3);
        assertTrue(scopes.contains(new String("argus.core1"))); 
        assertTrue(scopes.contains(new String("argus.core2")));     
        assertTrue(scopes.contains(new String("argus.core3")));  
    }

}
