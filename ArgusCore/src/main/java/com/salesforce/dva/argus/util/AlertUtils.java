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
 * @author  Sudhanshu Bahety (sudhanshu.bahety@salesforce.com)
 *
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
	
	public static Long getMaximumIntervalLength(String queryExpression) {
	     Long[] times = getStartAndEndTimes(queryExpression, System.currentTimeMillis());
	     return times[1] - times[0];
	}

	public static Long[] getStartAndEndTimes(String originalExpression, Long relativeTo) {
		String expression = "@" + originalExpression.replaceAll("[\\s\\t\\r\\n\\f]*", "");
		String regexMatcherWithStartAndEnd = "(?i)\\-[0-9]+(d|m|h|s):\\-[0-9]+(d|m|h|s)";
		String regexMatcherWithFILL = "(?i)FILL\\(#\\-[0-9]+(d|h|m|s),#\\-[0-9]+(d|h|m|s)";
		String regexMatcherWithoutEnd = "(?i)\\@\\-[0-9]+(d|m|h|s)|\\(\\-[0-9]+(d|m|h|s)|,\\-[0-9]+(d|m|h|s)";
		Long longestLength = 0L;
		Long[] startAndEndtimes = new Long[2];
		try {
			Matcher m = Pattern.compile(regexMatcherWithStartAndEnd).matcher(expression);
			while (m.find()) {
				String[] times = m.group().split(":");
				Long currentLength = MetricReader.getTime(relativeTo, times[1]) - MetricReader.getTime(relativeTo, times[0]);
				if(currentLength > longestLength) {
				    longestLength = currentLength;
				    startAndEndtimes[0] = MetricReader.getTime(relativeTo, times[0]);
				    startAndEndtimes[1] = MetricReader.getTime(relativeTo, times[1]);
				}
				expression = expression.replaceAll(m.group(),"");
			}

			m = Pattern.compile(regexMatcherWithFILL).matcher(expression);
			while (m.find()) {
				String[] times = m.group().substring(6, m.group().length() - 1).split("#,#");
				Long currentLength = MetricReader.getTime(relativeTo, times[1]) - MetricReader.getTime(relativeTo, times[0]);
				if(currentLength > longestLength) {
				    longestLength = currentLength;
				    startAndEndtimes[0] = MetricReader.getTime(relativeTo, times[0]);
				    startAndEndtimes[1] = MetricReader.getTime(relativeTo, times[1]);
				}
				expression = expression.replaceAll(m.group(),"");
			}

			m = Pattern.compile(regexMatcherWithoutEnd).matcher(expression);
			while (m.find()) {
				String timeStr = m.group();
				Long currentLength = relativeTo - MetricReader.getTime(relativeTo, timeStr.substring(1));
				if(currentLength > longestLength) {
				    longestLength = currentLength;
				    startAndEndtimes[0] = MetricReader.getTime(relativeTo, timeStr.substring(1));
				    startAndEndtimes[1] = relativeTo;
				}
			}
		} catch (Exception ex) {
			_logger.error(MessageFormat.format("Exception occurred while calculating the maximum time interval for expression {0}, with error message {1}", originalExpression, ex.getMessage()));
		}

		return startAndEndtimes;
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
