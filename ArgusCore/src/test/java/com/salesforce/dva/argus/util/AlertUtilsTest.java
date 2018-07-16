package com.salesforce.dva.argus.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;

public class AlertUtilsTest {

	@Test
	public void isScopePresentInWhiteListTest() {
		List<Pattern> scopesSet = new ArrayList<Pattern>(Arrays.asList(new Pattern[] {Pattern.compile("argus.core"), Pattern.compile("kafka.broker.*.ajna_local")}));
		
		assertTrue(AlertUtils.isScopePresentInWhiteList("-1d:argus.core:alerts.scheduled:zimsum:15m-sum",scopesSet));
		assertTrue(AlertUtils.isScopePresentInWhiteList("COUNT(-75m:-15m:kafka.broker.CHI.NONE.ajna_local:kafka.server.BrokerTopicMetrics.BytesInPerSec.BytesCount{device=*}:avg:1m-avg)", scopesSet));
		assertFalse(AlertUtils.isScopePresentInWhiteList("COUNT(-75m:-15m:kafka1.broker.CHI.NONE.ajna_local:kafka.server.BrokerTopicMetrics.BytesInPerSec.BytesCount{device=*}:avg:1m-avg)", scopesSet));
	}
}
