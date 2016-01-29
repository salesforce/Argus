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

import com.salesforce.dva.argus.entity.Metric;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MovingAverageTransformTest {

    @Test
    public void testMovingAvgTransformWithFixedNoOfPastPoints() {
        Transform movingAvgTransform = new MovingAverageTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(2000L, "2");
        datapoints.put(3000L, "3");
        datapoints.put(5000L, "10");
        datapoints.put(6000L, "2");
        datapoints.put(7000L, "3");
        datapoints.put(10000L, "15");

        Map<Long, String> actual = new HashMap<Long, String>();

        actual.put(1000L, null);
        actual.put(2000L, "1.5");
        actual.put(3000L, "2.5");
        actual.put(5000L, "6.5");
        actual.put(6000L, "6.0");
        actual.put(7000L, "2.5");
        actual.put(10000L, "9.0");

        Metric metric = new Metric("test-scope", "test-metric");

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("2");

        List<Metric> result = movingAvgTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), actual.size());
        assertEquals(result.get(0).getDatapoints(), actual);
    }

    @Test
    public void testMovingAvgTransformWithFixedNoOfPastPoints1() {
        Transform movingAvgTransform = new MovingAverageTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, null);
        datapoints.put(2000L, null);
        datapoints.put(3000L, "3");
        datapoints.put(5000L, "10");
        datapoints.put(6000L, "2");
        datapoints.put(7000L, "3");
        datapoints.put(10000L, "15");

        Map<Long, String> actual = new HashMap<Long, String>();

        actual.put(1000L, null);
        actual.put(2000L, null);
        actual.put(3000L, null);
        actual.put(5000L, "3.25");
        actual.put(6000L, "3.75");
        actual.put(7000L, "4.5");
        actual.put(10000L, "7.5");

        Metric metric = new Metric("test-scope", "test-metric");

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("4");

        List<Metric> result = movingAvgTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), actual.size());
        assertEquals(result.get(0).getDatapoints(), actual);
    }

    @Test
    public void testMovingAvgTransformWithFixedNoOfPastPoints2() {
        Transform movingAvgTransform = new MovingAverageTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, null);
        datapoints.put(2000L, null);
        datapoints.put(3000L, null);
        datapoints.put(5000L, null);
        datapoints.put(6000L, "2");
        datapoints.put(7000L, "3");
        datapoints.put(10000L, "15");

        Map<Long, String> actual = new HashMap<Long, String>();

        actual.put(1000L, null);
        actual.put(2000L, "0.0");
        actual.put(3000L, "0.0");
        actual.put(5000L, "0.0");
        actual.put(6000L, "1.0");
        actual.put(7000L, "2.5");
        actual.put(10000L, "9.0");

        Metric metric = new Metric("test-scope", "test-metric");

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("2");

        List<Metric> result = movingAvgTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), actual.size());
        assertEquals(result.get(0).getDatapoints(), actual);
    }

    @Test
    public void testMovingAvgTransformWithTimeInterval() {
        Transform movingAvgTransform = new MovingAverageTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(2000L, "2");
        datapoints.put(3000L, "3");
        datapoints.put(5000L, "10");
        datapoints.put(6000L, "2");
        datapoints.put(7000L, "3");
        datapoints.put(10000L, "15");

        Map<Long, String> actual = new HashMap<Long, String>();

        actual.put(1000L, null);
        actual.put(2000L, "1.5");
        actual.put(3000L, "2.5");
        actual.put(5000L, "10.0");
        actual.put(6000L, "6.0");
        actual.put(7000L, "2.5");
        actual.put(10000L, "15.0");

        Metric metric = new Metric("test-scope", "test-metric");

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("2s");

        List<Metric> result = movingAvgTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), actual.size());
        assertEquals(result.get(0).getDatapoints(), actual);
    }

    @Test
    public void testMovingAvgTransformWithTimeInterval1() {
        Transform movingAvgTransform = new MovingAverageTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, null);
        datapoints.put(2000L, null);
        datapoints.put(3000L, null);
        datapoints.put(5000L, "10");
        datapoints.put(6000L, "2");
        datapoints.put(7000L, "3");
        datapoints.put(10000L, "15");

        Map<Long, String> actual = new HashMap<Long, String>();

        actual.put(1000L, null);
        actual.put(2000L, "0.0");
        actual.put(3000L, "0.0");
        actual.put(5000L, "10.0");
        actual.put(6000L, "6.0");
        actual.put(7000L, "2.5");
        actual.put(10000L, "15.0");

        Metric metric = new Metric("test-scope", "test-metric");

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("2s");

        List<Metric> result = movingAvgTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), actual.size());
        assertEquals(result.get(0).getDatapoints(), actual);
    }

    @Test
    public void testMovingAvgTransformWithTimeInterval2() {
        Transform movingAvgTransform = new MovingAverageTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, null);
        datapoints.put(2000L, "2");
        datapoints.put(3000L, "3");
        datapoints.put(5000L, "10");
        datapoints.put(6000L, "2");
        datapoints.put(7000L, "3");
        datapoints.put(10000L, "15");

        Map<Long, String> actual = new HashMap<Long, String>();

        actual.put(1000L, null);
        actual.put(2000L, "1.0");
        actual.put(3000L, "2.5");
        actual.put(5000L, "10.0");
        actual.put(6000L, "6.0");
        actual.put(7000L, "2.5");
        actual.put(10000L, "15.0");

        Metric metric = new Metric("test-scope", "test-metric");

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("2s");

        List<Metric> result = movingAvgTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), actual.size());
        assertEquals(result.get(0).getDatapoints(), actual);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void transform_ShouldThrowUnsupportedOperationExceptionWhenNoWindowSizeSpecified() {
        List<Metric> metrics = new ArrayList<Metric>();

        new MovingAverageTransform().transform(metrics);
    }

    @Test
    public void testMovingAvgTransformScopeName() {
        Transform movingAvgTransform = new MovingAverageTransform();

        assertEquals("MOVINGAVERAGE", movingAvgTransform.getResultScopeName());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
