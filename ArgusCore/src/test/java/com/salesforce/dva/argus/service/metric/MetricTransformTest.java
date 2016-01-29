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
     
package com.salesforce.dva.argus.service.metric;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.transform.AverageTransform;
import com.salesforce.dva.argus.service.metric.transform.DivisionTransform;
import com.salesforce.dva.argus.service.metric.transform.SubtractionTransform;
import com.salesforce.dva.argus.service.metric.transform.SumTransform;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.junit.Assert.assertEquals;

public class MetricTransformTest extends AbstractTest {

    private static Metric first;
    private static Metric second;
    private static Metric third;

    @BeforeClass
    public static void createMetricsForTransform() {
        long startTime = System.currentTimeMillis();

        first = createMetricForTransform(startTime);
        second = createMetricForTransform(startTime);
        third = createMetricForTransform(startTime);
    }

    private static Metric createMetricForTransform(long startTime) {
        String metricName = createRandomName();
        String scope = createRandomName();
        Metric result = new Metric(scope, metricName);
        int datapointCount = random.nextInt(500) + 200;
        Map<Long, String> datapoints = new HashMap<>();

        for (int j = 0; j < datapointCount; j++) {
            datapoints.put(startTime - (j * 60000L), String.valueOf(random.nextInt(100000)));
        }
        result.setDatapoints(datapoints);
        return result;
    }

    @Test
    public void testAdditionTransform() {
        Transform transform = new SumTransform();
        Map<Long, String> expectedDatapoints = new HashMap<Long, String>();
        Iterator<Entry<Long, String>> it = first.getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, String> entry = it.next();

            if (second.getDatapoints().get(entry.getKey()) != null) {
                expectedDatapoints.put(entry.getKey(),
                    String.valueOf(Double.valueOf(entry.getValue()) + Double.valueOf(second.getDatapoints().get(entry.getKey()))));
            }
        }

        List<Metric> metrics = transform.transform(Arrays.asList(new Metric[] { first, second }));
        Map<Long, String> actualDatapoints = metrics.get(0).getDatapoints();

        assertEquals(expectedDatapoints.size(), actualDatapoints.size());
        assertEquals(expectedDatapoints, actualDatapoints);
    }

    @Test
    public void testAdditionTransform3() {
        Transform transform = new SumTransform();
        Map<Long, String> expectedDatapoints = new HashMap<Long, String>();
        Iterator<Entry<Long, String>> it = first.getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, String> entry = it.next();

            if (second.getDatapoints().get(entry.getKey()) != null && third.getDatapoints().get(entry.getKey()) != null) {
                Double sum = Double.valueOf(entry.getValue()) + Double.valueOf(second.getDatapoints().get(entry.getKey())) +
                    Double.valueOf(third.getDatapoints().get(entry.getKey()));

                expectedDatapoints.put(entry.getKey(), String.valueOf(sum));
            }
        }

        List<Metric> metrics = transform.transform(Arrays.asList(new Metric[] { first, second, third }));
        Map<Long, String> actualDatapoints = metrics.get(0).getDatapoints();

        assertEquals(expectedDatapoints.size(), actualDatapoints.size());
        assertEquals(expectedDatapoints, actualDatapoints);
    }

    @Test
    public void testSubtractionTransform() {
        SubtractionTransform transform = new SubtractionTransform();
        List<Metric> list = new ArrayList<Metric>();

        list.add(first);
        list.add(second);

        Map<Long, String> expectedDatapoints = new HashMap<Long, String>();
        Iterator<Entry<Long, String>> it = first.getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, String> entry = it.next();

            if (second.getDatapoints().get(entry.getKey()) != null) {
                expectedDatapoints.put(entry.getKey(),
                    String.valueOf(Double.valueOf(entry.getValue()) - Double.valueOf(second.getDatapoints().get(entry.getKey()))));
            }
        }

        List<Metric> metrics = transform.transform(list);
        Map<Long, String> actualDatapoints = metrics.get(0).getDatapoints();

        assertEquals(expectedDatapoints.size(), actualDatapoints.size());
        assertEquals(expectedDatapoints, actualDatapoints);
    }

    @Test
    public void testSubtractionTransform3() {
        Transform transform = new SubtractionTransform();
        Map<Long, String> expectedDatapoints = new HashMap<Long, String>();
        Iterator<Entry<Long, String>> it = first.getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, String> entry = it.next();

            if (second.getDatapoints().get(entry.getKey()) != null && third.getDatapoints().get(entry.getKey()) != null) {
                Double difference = Double.valueOf(entry.getValue()) - Double.valueOf(second.getDatapoints().get(entry.getKey())) -
                    Double.valueOf(third.getDatapoints().get(entry.getKey()));

                expectedDatapoints.put(entry.getKey(), String.valueOf(difference));
            }
        }

        List<Metric> metrics = transform.transform(Arrays.asList(new Metric[] { first, second, third }));
        Map<Long, String> actualDatapoints = metrics.get(0).getDatapoints();

        assertEquals(expectedDatapoints.size(), actualDatapoints.size());
        assertEquals(expectedDatapoints, actualDatapoints);
    }

    @Test
    public void testDivisionTransform() {
        DivisionTransform transform = new DivisionTransform();
        Map<Long, String> expectedDatapoints = new HashMap<Long, String>();
        Iterator<Entry<Long, String>> it = first.getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, String> entry = it.next();

            if (second.getDatapoints().get(entry.getKey()) != null && Double.valueOf(entry.getValue()) != 0 &&
                    Double.valueOf(second.getDatapoints().get(entry.getKey())) != 0) {
                expectedDatapoints.put(entry.getKey(),
                    String.valueOf(Double.valueOf(entry.getValue()) / Double.valueOf(second.getDatapoints().get(entry.getKey()))));
            }
        }

        List<Metric> list = new ArrayList<Metric>();

        list.add(first);
        list.add(second);

        List<Metric> metrics = transform.transform(list);
        Map<Long, String> actualDatapoints = metrics.get(0).getDatapoints();

        assertEquals(expectedDatapoints.size(), actualDatapoints.size());
        assertEquals(expectedDatapoints, actualDatapoints);
    }

    @Test
    public void testAverageTransform() {
        Transform averageTransform = new AverageTransform();
        Map<Long, String> expectedDatapoints = new HashMap<Long, String>();
        Iterator<Entry<Long, String>> it = first.getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, String> entry = it.next();

            if (second.getDatapoints().get(entry.getKey()) != null && third.getDatapoints().get(entry.getKey()) != null) {
                Double sum = Double.valueOf(entry.getValue()) + Double.valueOf(second.getDatapoints().get(entry.getKey())) +
                    Double.valueOf(third.getDatapoints().get(entry.getKey()));

                expectedDatapoints.put(entry.getKey(), String.valueOf(sum / 3));
            }
        }

        List<Metric> metrics = averageTransform.transform(Arrays.asList(new Metric[] { first, second, third }));
        Map<Long, String> actualDatapoints = metrics.get(0).getDatapoints();

        assertEquals(expectedDatapoints.size(), actualDatapoints.size());
        assertEquals(expectedDatapoints, actualDatapoints);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
