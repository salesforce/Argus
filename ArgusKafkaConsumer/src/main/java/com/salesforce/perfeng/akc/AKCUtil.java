package com.salesforce.perfeng.akc;

import org.apache.commons.lang3.StringUtils;

import java.util.function.Function;

public class AKCUtil {
    /**
        Argus only supports letters, digits and ./-_ for namespace, scope,
        metric and tags. All other characters will be replaced by this.
    **/

    private static final String UNSUPPORTED_CHARACTER_REPLACEMENT = "__";

    public static String replaceUnsupportedChars(String input) {
        return input.replaceAll("[^a-zA-Z0-9\\./\\-_]+", UNSUPPORTED_CHARACTER_REPLACEMENT);
    }

    /**
     * small util method that's used in several locations to transform service, subservice strings, etc
     * @param input some CharSequence
     * @param defaultString default string to use when input is blank
     * @param textTransform custom transformation done to the input
     * @return final string
     */
    public static String resolveCharSequence(CharSequence input, String defaultString, Function<CharSequence, String> textTransform) {
        return StringUtils.isBlank(input) ? defaultString : textTransform.apply(input);
    }

}
