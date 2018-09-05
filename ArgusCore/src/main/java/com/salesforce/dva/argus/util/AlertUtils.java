package com.salesforce.dva.argus.util;

import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for the alert evaluation flow
 *
 * @author  Sundeep tiyyagura (stiyyagura@salesforce.com)
 */
public class AlertUtils {

	private static final Logger _logger = LoggerFactory.getLogger(AlertUtils.class);

	public static boolean isScopePresentInWhiteList(String expression, List<Pattern> scopeRegexPatterns) {
		for(Pattern regexPattern : scopeRegexPatterns) {
			if(regexPattern.matcher(expression.toLowerCase()).find()) {
				return true;
			}
		}
		return false;
	}

	public static Long getMaximumIntervalLength(String originalExpression) {
		String expression = "@" + originalExpression.replaceAll("[\\s\\t\\r\\n\\f]*", "");
		String regexMatcherWithStartAndEnd = "(?i)\\-[0-9]+(d|m|h|s):\\-[0-9]+(d|m|h|s)";
		String regexMatcherWithFILL = "(?i)FILL\\(#\\-[0-9]+(d|h|m|s),#\\-[0-9]+(d|h|m|s)";
		String regexMatcherWithoutEnd = "(?i)\\@\\-[0-9]+(d|m|h|s)|\\(\\-[0-9]+(d|m|h|s)|,\\-[0-9]+(d|m|h|s)";
		Long relativeTo = System.currentTimeMillis(), longestLength = 0L;
		try {
			Matcher m = Pattern.compile(regexMatcherWithStartAndEnd).matcher(expression);
			while (m.find()) {
				String[] times = m.group().split(":");
				Long currentLength = MetricReader.getTime(relativeTo, times[1]) - MetricReader.getTime(relativeTo, times[0]);
				longestLength = Math.max(currentLength, longestLength);
				expression = expression.replaceAll(m.group(),"");
			}

			m = Pattern.compile(regexMatcherWithFILL).matcher(expression);
			while (m.find()) {
				String[] times = m.group().substring(6, m.group().length() - 1).split("#,#");
				Long currentLength = MetricReader.getTime(relativeTo, times[1]) - MetricReader.getTime(relativeTo, times[0]);
				longestLength = Math.max(currentLength, longestLength);
				expression = expression.replaceAll(m.group(),"");
			}

			m = Pattern.compile(regexMatcherWithoutEnd).matcher(expression);
			while (m.find()) {
				String timeStr = m.group();
				Long currentLength = relativeTo - MetricReader.getTime(relativeTo, timeStr.substring(1));
				longestLength = Math.max(currentLength, longestLength);
			}
		} catch (Exception ex) {
			_logger.error(MessageFormat.format("Exception occurred while calculating the maximum time interval for expression {0}, with error message {1}", originalExpression, ex.getMessage()));
		}

		return longestLength;
	}

	public static String getExpressionWithAbsoluteStartAndEndTimeStamps(DefaultAlertService.NotificationContext context) {
		String absoluteExpression = "";
		try {
			String expression = "@" + context.getAlert().getExpression().replaceAll("[\\s\\t\\r\\n\\f]*", "");
			String regexMatcherWithStartAndEnd = "(?i)\\-[0-9]+(d|m|h|s):\\-[0-9]+(d|m|h|s)";
			String regexMatcherWithConstants = "(?i)#\\-[0-9]+(d|h|m|s)";
			String regexMatcherWithoutEnd = "(?i)\\@\\-[0-9]+(d|m|h|s)|\\(\\-[0-9]+(d|m|h|s)|,\\-[0-9]+(d|m|h|s)";
			Long relativeTo = context.getAlertEnqueueTimestamp();

			Matcher m = Pattern.compile(regexMatcherWithStartAndEnd).matcher(expression);
			while (m.find()) {
				for (String timeStr: m.group().split(":")) {
					Long absoluteTime = MetricReader.getTime(relativeTo, timeStr);
					expression = expression.replaceFirst(timeStr, ""  + absoluteTime);
				}
			}

			m = Pattern.compile(regexMatcherWithConstants).matcher(expression);
			while (m.find()) {
				String timeStr = m.group();
				Long absoluteTime = MetricReader.getTime(relativeTo, timeStr.substring(1));
				expression = expression.replaceFirst(timeStr, ("" + timeStr.charAt(0)) + absoluteTime);
			}

			m = Pattern.compile(regexMatcherWithoutEnd).matcher(expression);
			while (m.find()) {
				String timeStr = m.group();
				Long absoluteTime = MetricReader.getTime(relativeTo, timeStr.substring(1));
				expression = expression.replace(timeStr, ("" + timeStr.charAt(0)) + absoluteTime + (":" + relativeTo));
			}
			absoluteExpression = expression.substring(1);
		} catch (Exception ex) {
			_logger.error(MessageFormat.format("Exception occurred while converting relative time within the expression to absolute time for {0}, with error message {1}.", context.getAlert().getExpression(), ex.getMessage()));
		}

		return absoluteExpression;
	}

}
