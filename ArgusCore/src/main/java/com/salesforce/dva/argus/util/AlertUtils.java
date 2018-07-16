package com.salesforce.dva.argus.util;

import java.util.Set;
import java.util.regex.Pattern;

public class AlertUtils {

	public static boolean isScopePresentInWhiteList(String expression, Set<String> whiteListedScopeRegexes) {
		for(String scopeRegex : whiteListedScopeRegexes) {
			if(Pattern.compile(":"+scopeRegex+":").matcher(expression.toLowerCase()).find()) {
				return true;
			}
		}
		return false;
	}
}
