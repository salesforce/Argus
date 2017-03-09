package com.salesforce.dva.argus.service.schema;

import java.net.URLEncoder;
import java.util.LinkedHashMap;
import java.util.Map;

import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author stefanwiedemann {@literal stefan.wiedemann@kiwigrid.com}
 */
public class MetricQueryMaskerTest {

	private MetricQueryMasker metricQueryMasker;

	@Before
	public void setUp(){
		metricQueryMasker = new MetricQueryMasker();
	}

	@Test
	public void testMaskAndDemaskQuery() throws Exception{

		String filterExpression =  "regexp((^)test($|anything))";
		String encodedExpression = URLEncoder.encode(filterExpression, "UTF-8");

		Map<String, String> tagMap = new LinkedHashMap<>();
		tagMap.put("tag", filterExpression);

		MetricQuery metricQuery = new MetricQuery("TestScope", "TestMetric", tagMap, 0l, 1l);

		MetricQuery maskedQuery = metricQueryMasker.maskQuery(metricQuery);

		assertTrue("The query should be masked.", metricQueryMasker.isQueryMasked());
		assertFalse("The filter expression should be masked.", maskedQuery.toString().contains(filterExpression));


		MetricQuery unmaskedQuery = metricQueryMasker.demaskQuery(maskedQuery);

		assertTrue("The filter expression should be unmasked.", unmaskedQuery.toString().contains(encodedExpression));

	}

	@Test
	public void testMaskAndDemaskQuery_multipleTags() throws Exception{

		String filterExpression =   "literal_or(i)";
		String unfilterdExpression = "*";
		String encodedExpression = URLEncoder.encode(filterExpression, "UTF-8");

		Map<String, String> tagMap = new LinkedHashMap<>();
		tagMap.put("tag",filterExpression);
		tagMap.put("tag2", unfilterdExpression);

		MetricQuery metricQuery = new MetricQuery("TestScope", "TestMetric", tagMap, 0l, 1l);

		MetricQuery maskedQuery = metricQueryMasker.maskQuery(metricQuery);

		assertTrue("The query should be masked.", metricQueryMasker.isQueryMasked());
		assertFalse("The filter expression should be masked.", maskedQuery.toString().contains(filterExpression));


		MetricQuery unmaskedQuery = metricQueryMasker.demaskQuery(maskedQuery);

		assertTrue("The filter expression should be unmasked.", unmaskedQuery.toString().contains(encodedExpression));
		assertTrue("The filter expression should be unmasked.", unmaskedQuery.toString().contains(unfilterdExpression));

	}

}