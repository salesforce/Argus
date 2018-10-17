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

public class AverageBelowTransformTest {

    @Test
    public void testAverageBelowTransform() {
        Transform averageBelowTransform = new AverageBelowTransform();
        Map<Long, Double> datapoints1 = new HashMap<Long, Double>();

        datapoints1.put(1L, 1.0);
        datapoints1.put(2L, 2.0);
        datapoints1.put(3L, 3.0);
        datapoints1.put(4L, 4.0);

        Metric below = new Metric("below-scope", "below-metric");

        below.setDatapoints(datapoints1);

        Map<Long, Double> datapoints2 = new HashMap<Long, Double>();

        datapoints2.put(1L, 10.0);
        datapoints2.put(2L, 2.0);
        datapoints2.put(3L, 3.0);
        datapoints2.put(4L, 15.0);

        Metric above = new Metric("above-scope", "above-metric");

        above.setDatapoints(datapoints2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(above);
        metrics.add(below);

        List<String> constants = new ArrayList<String>(1);

        constants.add("5");

        List<Metric> result = averageBelowTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 1);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
