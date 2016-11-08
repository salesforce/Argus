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

import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Shifts the timestamp for each data point by the specified constant.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class ShiftValueMapping implements ValueMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints) {
        throw new UnsupportedOperationException("Shift transform requires an offset input!");
    }

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants.size() == 1, "Shift Transform can only have one constant which is offset.");

        Long offset = getOffsetInSeconds(constants.get(0)) * 1000;
        Map<Long, String> shiftDatapoints = new TreeMap<Long, String>();

        for (Entry<Long, String> entry : originalDatapoints.entrySet()) {
            Long newTimestamp = entry.getKey() + offset;

            SystemAssert.requireArgument((newTimestamp <= Long.MAX_VALUE && newTimestamp >= Long.MIN_VALUE),
                "You are not allowed to shift like this, be nice to me!");
            shiftDatapoints.put(newTimestamp, entry.getValue());
        }
        return shiftDatapoints;
    }

    @Override
    public String name() {
        return TransformFactory.Function.SHIFT.name();
    }

    private long getOffsetInSeconds(String offset) {
        MetricReader.TimeUnit timeunit = null;
        Long backwards = 1L;

        try {
            if (offset.startsWith("-")) {
                backwards = -1L;
                offset = offset.substring(1);
            }
            if (offset.startsWith("+")) {
                offset = offset.substring(1);
            }
            timeunit = MetricReader.TimeUnit.fromString(offset.substring(offset.length() - 1));

            long timeDigits = Long.parseLong(offset.substring(0, offset.length() - 1));

            return backwards * timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            throw new IllegalArgumentException("Fail to parse offset!");
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
