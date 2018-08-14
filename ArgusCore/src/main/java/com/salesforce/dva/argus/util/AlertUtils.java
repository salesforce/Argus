package com.salesforce.dva.argus.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility functions for the alert evaluation flow
 *
 * @author  Sundeep tiyyagura (stiyyagura@salesforce.com)
 */
public class AlertUtils {

	public static boolean isScopePresentInWhiteList(String expression, List<Pattern> scopeRegexPatterns) {
		for(Pattern regexPattern : scopeRegexPatterns) {
			if(regexPattern.matcher(expression.toLowerCase()).find()) {
				return true;
			}
		}
		return false;
	}
}
