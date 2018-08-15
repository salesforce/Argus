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

import com.salesforce.dva.argus.entity.NumberOperations;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Calculates the logarithm according to the specified base.<br>
 * <tt>LOG(&lt;expr&gt;, &lt;constant&gt;)</tt>
 *
 * @author  Ruofan Zhang (rzhang@saleforce.com)
 */
public class LogValueMapping implements ValueMapping {

    // ~ Methods
    // **************************************************************************************************************************************

    @Override
    public Map<Long, Number> mapping(Map<Long, Number> originalDatapoints) {
        List<String> constants = new ArrayList<String>();

        constants.add("10");
        return mapping(originalDatapoints, constants);
    }

    @Override
    public Map<Long, Number> mapping(Map<Long, Number> originalDatapoints, List<String> constants) {
        if (constants == null || constants.isEmpty()) {
            return mapping(originalDatapoints);
        }
        SystemAssert.requireArgument(constants.size() == 1, "Log Transform requires exactly one constant!");

        Number base = NumberOperations.parseConstant(constants.get(0));
        
        Map<Long, Number> logDatapoints = new HashMap<>();

        for (Entry<Long, Number> entry : originalDatapoints.entrySet()) {
            Number logValue = NumberOperations.divide(NumberOperations.log(entry.getValue()), NumberOperations.log(base));

            logDatapoints.put(entry.getKey(), logValue);
        }
        return logDatapoints;
    }

    @Override
    public String name() {
        return TransformFactory.Function.LOG.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
