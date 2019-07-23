/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dva.argus.util;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A Number formatter that formats Metric Numbers.
 */
public class MetricNumberFormat extends NumberFormat {
    private static final long serialVersionUID = 1;

    private static NavigableMap<Double, String> suffixes = new TreeMap<>();
    private static  DecimalFormat decimalFormat = new DecimalFormat("0.00");
    static {
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
        suffixes.put(0.000000000000000000000001d, "y");
        suffixes.put(0.000000000000000000001d, "z");
        suffixes.put(0.000000000000000001d, "a");
        suffixes.put(0.000000000000001d, "f");
        suffixes.put(0.000000000001d, "p");
        suffixes.put(0.000000001d, "n");
        suffixes.put(0.000001d, "µ");
        suffixes.put(0.001d, "m");
        suffixes.put(1000d, "k");
        suffixes.put(1000000d, "M");
        suffixes.put(1000000000d, "G");
        suffixes.put(1000000000000d, "T");
        suffixes.put(1000000000000000d, "P");
        suffixes.put(1000000000000000000d, "E");
        suffixes.put(1000000000000000000000d, "Z");
        suffixes.put(1000000000000000000000000d, "Y");
    }

    /**
     * Format the double number to metrics terminology
     * @param value double value
     * @return  String representing the metric formatted in Metrics terminology
     */
    public String formatNumber(double value) {
        if (value == Double.MIN_VALUE) return formatNumber(Double.MIN_VALUE + 1);
        if (value < 0) return "-" + formatNumber(-value);
        if (value < 1000 && value>=0) return Double.toString(value);
        Map.Entry<Double, String> e = suffixes.floorEntry(value);
        Double divideBy = e.getKey();
        String suffix = e.getValue();

        double number = value / divideBy;
        if (number%1==0) {
            return Math.round(number) + suffix;
        }
        else {
            return decimalFormat.format((number)) + suffix;
        }

    }

    /**
     * Formats a number into the specified string buffer.
     *
     * @param number  the number to format.
     * @param toAppendTo  the string buffer.
     * @param pos  the field position (ignored here).
     *
     * @return The string buffer.
     */
    @Override
    public StringBuffer format(double number, StringBuffer toAppendTo,
                               FieldPosition pos) {
            return toAppendTo.append(formatNumber(number));
    }

    /**
     * Formats a number into the specified string buffer.
     *
     * @param number  the number to format.
     * @param toAppendTo  the string buffer.
     * @param pos  the field position (ignored here).
     *
     * @return The string buffer.
     */
    @Override
    public StringBuffer format(long number, StringBuffer toAppendTo,
                               FieldPosition pos) {
        return toAppendTo.append(formatNumber(number));
    }

    /**
     * This method returns <code>null</code> for all inputs.  This class cannot
     * be used for parsing.
     *
     * @param source  the source string.
     * @param parsePosition  the parse position.
     *
     * @return <code>null</code>.
     */
    @Override
    public Number parse(String source, ParsePosition parsePosition) {
        return null;
    }

}
