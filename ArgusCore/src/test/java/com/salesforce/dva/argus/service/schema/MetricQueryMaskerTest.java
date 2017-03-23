package com.salesforce.dva.argus.service.schema;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.hamcrest.core.StringContains;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;

/**
 * Test for the {@link MetricQueryMasker}
 *
 * @author stefanwiedemann {@literal stefan.wiedemann@kiwigrid.com}
 */
public class MetricQueryMaskerTest {

	private MetricQueryMasker metricQueryMasker;

	@Before
	public void setUp() {
		metricQueryMasker = new MetricQueryMasker();
	}

	@Test
	public void testMaskAndDemaskQuery() throws Exception {
		String filterExpression = "regexp((^)test($|anything))";
		String encodedExpression = URLEncoder.encode(filterExpression, StandardCharsets.UTF_8.name());

		Map<String, String> tagMap = new LinkedHashMap<>();
		tagMap.put("tag", filterExpression);

		MetricQuery metricQuery = new MetricQuery("TestScope", "TestMetric", tagMap, 0L, 1L);
		MetricQuery maskedQuery = metricQueryMasker.maskQuery(metricQuery);

		assertTrue("The query has to be masked.", metricQueryMasker.isQueryMasked());
		assertThat("Filter expression has to be masked", maskedQuery.toString(), not(containsString(filterExpression)));

		MetricQuery demaskedQuery = new MetricQuery(maskedQuery);
		metricQueryMasker.demaskQuery(demaskedQuery);

		assertThat("Filter expression has to be de-masked", demaskedQuery.toString(), containsString(encodedExpression));
	}

	@Test
	public void testMaskAndDemaskQuery_multipleTags() throws Exception {
		String filterExpression = "literal_or(i)";
		String unfilterdExpression = "*";
		String encodedExpression = URLEncoder.encode(filterExpression, StandardCharsets.UTF_8.name());

		Map<String, String> tagMap = new LinkedHashMap<>();
		tagMap.put("tag", filterExpression);
		tagMap.put("tag2", unfilterdExpression);

		MetricQuery metricQuery = new MetricQuery("TestScope", "TestMetric", tagMap, 0L, 1L);
		MetricQuery maskedQuery = metricQueryMasker.maskQuery(metricQuery);

		assertTrue("The query has to be masked.", metricQueryMasker.isQueryMasked());
		assertThat("Filter expression has to be masked", maskedQuery.toString(), not(containsString(filterExpression)));

		MetricQuery demaskedQuery = new MetricQuery(maskedQuery);
		metricQueryMasker.demaskQuery(demaskedQuery);

		assertThat("The filter expression has to be unmasked.", demaskedQuery.toString(), containsString(encodedExpression));
		assertThat("The filter expression has to be unmasked.", demaskedQuery.toString(), containsString(unfilterdExpression));
	}
}