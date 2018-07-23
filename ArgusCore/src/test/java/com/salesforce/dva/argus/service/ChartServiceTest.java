package com.salesforce.dva.argus.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Chart;
import com.salesforce.dva.argus.entity.Chart.ChartQuery;
import com.salesforce.dva.argus.entity.Chart.ChartQueryType;
import com.salesforce.dva.argus.entity.Chart.ChartType;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.Dashboard.LayoutType;
import com.salesforce.dva.argus.entity.PrincipalUser;

public class ChartServiceTest extends AbstractTest {
	
	private ChartService _chartService;
	private PrincipalUser _adminUser;
	
	@Before
	public void setup() {
		_chartService = system.getServiceFactory().getChartService();
		_adminUser = system.getServiceFactory().getUserService().findAdminUser();
	}
	
	@Test
	public void testCreateChart() {
		
		List<ChartQuery> queries = Arrays.asList(new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg"));
		Chart chart = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart.setTitle("This is an example chart");
		
		chart = _chartService.updateChart(chart);
		assertNotNull(chart.getId());
		
		Chart retrievedChart = _chartService.getChartByPrimaryKey(chart.getId());
		assertEquals(chart.getId(), retrievedChart.getId());
	}

	@Test
	public void testDeleteChart() {
		
		List<ChartQuery> queries = Arrays.asList(new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg"));
		Chart chart = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
        Map<String,String> preferences = new HashMap<>();
        preferences.put("brush","enabled");
        preferences.put("downsampling","Method1");
        chart.getPreferences().putAll(preferences);
		chart.setTitle("This is an example chart");
		
		chart = _chartService.updateChart(chart);
		
		_chartService.deleteChart(chart);
		Chart retrievedChart = _chartService.getChartByPrimaryKey(chart.getId());
		assertNull(retrievedChart);
	}
	
	@Test
	public void testDeleteChartById() {
		
		List<ChartQuery> queries = Arrays.asList(new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg"));
		Chart chart = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart.setTitle("This is an example chart");
        Map<String,String> preferences = new HashMap<>();
        preferences.put("brush","enabled");
        preferences.put("downsampling","Method1");
        chart.getPreferences().putAll(preferences);
		
		chart = _chartService.updateChart(chart);
		
		_chartService.deleteChart(chart.getId());
		Chart retrievedChart = _chartService.getChartByPrimaryKey(chart.getId());
		assertNull(retrievedChart);
	}
	
	@Test
	public void testGetChartByPrimaryKey() {
		
		List<ChartQuery> queries = Arrays.asList(new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg"));
		Chart chart = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart.setTitle("This is an example chart");
        Map<String,String> preferences = new HashMap<>();
        preferences.put("brush","enabled");
        preferences.put("downsampling","Method1");
        chart.getPreferences().putAll(preferences);

		chart = _chartService.updateChart(chart);
		
		Chart retrievedChart = _chartService.getChartByPrimaryKey(chart.getId());
		assertEquals(chart.getId(), retrievedChart.getId());
	}
	
	@Test
	public void testGetChartsByOwner() {
		
		List<ChartQuery> queries = Arrays.asList(new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg"));
		Chart chart1 = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart1.setTitle("chart1");
		
		Chart chart2 = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart2.setTitle("chart2");
		
		_chartService.updateChart(chart1);
		_chartService.updateChart(chart2);
		
		List<Chart> charts = _chartService.getChartsByOwner(_adminUser);
		assertEquals("chart1", charts.get(0).getTitle());
		assertEquals("chart2", charts.get(1).getTitle());
	}
	
	@Test
	public void testGetChartsForEntity() {
		
		Dashboard dashboard = new Dashboard(_adminUser, "dashboard", _adminUser);
		dashboard.setLayout(LayoutType.MEDIUM);
		dashboard = system.getServiceFactory().getDashboardService().updateDashboard(dashboard);
		
		List<ChartQuery> queries = Arrays.asList(new ChartQuery(ChartQueryType.METRIC, "-1h:argus.jvm:mem.heap.used:avg"));
		Chart chart1 = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart1.setTitle("chart1");
		chart1.setEntity(dashboard);
		
		Chart chart2 = new Chart(_adminUser, _adminUser, ChartType.LINE, queries);
		chart2.setTitle("chart2");
		chart2.setEntity(dashboard);
		
		chart1 = _chartService.updateChart(chart1);
		chart2 = _chartService.updateChart(chart2);
		
		List<Chart> charts = _chartService.getChartsForEntity(dashboard.getId());
		assertEquals("chart1", charts.get(0).getTitle());
		assertEquals("chart2", charts.get(1).getTitle());
	}
	
}
