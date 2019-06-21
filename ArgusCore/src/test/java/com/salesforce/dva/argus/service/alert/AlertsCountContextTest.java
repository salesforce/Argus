package com.salesforce.dva.argus.service.alert;

import com.salesforce.dva.argus.entity.PrincipalUser;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AlertsCountContextTest {

	private static String searchText;
	private static PrincipalUser owner;

	@BeforeClass
	static public void setUpClass() {
		owner = new PrincipalUser(null, "owner", "owner@mycompany.abc");
		searchText = "testSearchText";
	}

	@Test
	public void isCountUserAlerts() {
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts()
				.setPrincipalUser(owner).setSearchText(searchText).build();
		assertEquals(true, context.isCountUserAlerts());
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().build();
		assertEquals(false, context.isCountUserAlerts());
	}

	@Test
	public void isCountSharedAlerts() {
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts()
				.setPrincipalUser(owner).setSearchText(searchText).build();
		assertEquals(true, context.isCountSharedAlerts());
		context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().countUserAlerts().build();
		assertEquals(false, context.isCountSharedAlerts());
	}

	@Test
	public void isCountPrivateAlerts() {
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts()
				.setPrincipalUser(owner).setSearchText(searchText).build();
		assertEquals(true, context.isCountPrivateAlerts());
		context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().build();
		assertEquals(false, context.isCountPrivateAlerts());
	}

	@Test
	public void getPrincipalUser() {
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts()
				.setPrincipalUser(owner).setSearchText(searchText).build();
		assertEquals(owner, context.getPrincipalUser());
		assertNotEquals(null, context.getPrincipalUser());
		context = new AlertsCountContext.AlertsCountContextBuilder().setPrincipalUser(null).build();
		assertEquals(null, context.getPrincipalUser());
	}

	@Test
	public void getSearchText() {
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts()
				.setPrincipalUser(owner).setSearchText(searchText).build();
		assertEquals(searchText, context.getSearchText());
	}
}