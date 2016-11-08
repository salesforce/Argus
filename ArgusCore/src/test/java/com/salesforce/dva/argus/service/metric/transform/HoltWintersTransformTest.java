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
     
package com.salesforce.dva.argus.service.metric.transform;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HoltWintersTransformTest {

    @Test
    public void testHoltWintersForecast() {
        HoltWintersAnalysis holtWinters = new HoltWintersAnalysis();
        Map<Long, String> dp = new HashMap<Long, String>();

        dp.put(2L, "3.0");
        dp.put(3L, "4.0");
        dp.put(4L, "5.0");
        dp.put(5L, "8.0");
        dp.put(6L, "13.0");
        dp.put(7L, "6.0");
        dp.put(8L, "4.0");
        dp.put(9L, "3.0");
        dp.put(10L, "2.5");

        double alpha = 0.1, beta = 0.0035, gamma = 0.1;
        int seasonLength = 1;
        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(2L, "3");
        expected.put(3L, "3");
        expected.put(4L, "3.10035");
        expected.put(5L, "3.2823");
        expected.put(6L, "3.73133");
        expected.put(7L, "4.59829");
        expected.put(8L, "4.60102");
        expected.put(9L, "4.40302");
        expected.put(10L, "4.14214");

        Map<Long, String> forecastedDatapoints = holtWinters._performHoltWintersAnalysis(dp, alpha, beta, gamma, seasonLength, 2L)
            .getForecastedDatapoints();

        assertTrue(forecastedDatapoints.equals(expected));
    }

    @Test
    public void testHoltWintersDeviation() {
        HoltWintersAnalysis holtWinters = new HoltWintersAnalysis();
        Map<Long, String> dp = new HashMap<Long, String>();

        dp.put(2L, "3.0");
        dp.put(3L, "4.0");
        dp.put(4L, "5.0");
        dp.put(5L, "8.0");
        dp.put(6L, "13.0");
        dp.put(7L, "6.0");
        dp.put(8L, "4.0");
        dp.put(9L, "3.0");
        dp.put(10L, "2.5");

        double alpha = 0.1, beta = 0.0035, gamma = 0.1;
        int seasonLength = 1;
        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(2L, "0");
        expected.put(3L, "0.1");
        expected.put(4L, "0.27997");
        expected.put(5L, "0.72374");
        expected.put(6L, "1.57824");
        expected.put(7L, "1.56059");
        expected.put(8L, "1.46463");
        expected.put(9L, "1.45847");
        expected.put(10L, "1.47683");

        Map<Long, String> deviationDatapoints = holtWinters._performHoltWintersAnalysis(dp, alpha, beta, gamma, seasonLength, 2L)
            .getDeviationDatapoints();

        assertTrue(deviationDatapoints.equals(expected));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
