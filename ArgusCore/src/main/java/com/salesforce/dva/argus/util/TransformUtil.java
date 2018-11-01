package com.salesforce.dva.argus.util;

import com.salesforce.dva.argus.service.metric.MetricReader;

public class TransformUtil {

	public static long getWindowInSeconds(String window) {
		MetricReader.TimeUnit timeunit = null;

		try {
			timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));
			long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));
			return timeDigits * timeunit.getValue() / 1000;
		} catch (Exception t) {
			throw new IllegalArgumentException("Fail to parse window size!");
		}
	}
}