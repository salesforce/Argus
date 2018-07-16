package com.salesforce.dva.argus.util;

import java.util.List;
import java.util.regex.Pattern;

public class AlertUtils {

	public static boolean isScopePresentInWhiteList(String expression, List<Pattern> regexPatterns) {
		for(Pattern regexPattern : regexPatterns) {
			if(regexPattern.matcher(expression.toLowerCase()).find()) {
				return true;
			}
		}
		return false;
	}
}
