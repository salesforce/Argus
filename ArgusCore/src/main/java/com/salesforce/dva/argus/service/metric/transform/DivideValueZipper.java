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

import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;

/**
 * Make a map of datapoints that aggregates points from originalDatapoints and baseDatapoints, if for some timestamp in originalDatapoints, there is
 * no point value in the baseDatapoints corresponding to that timestamp, the aggregation result is quotient of point value in originalDatapoints and
 * ONE if the point value in originalDatapoints is null or empty, set it a ZERO.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DivideValueZipper implements ValueZipper {

    //~ Methods **************************************************************************************************************************************

    @Override
    public String zip(String originalDpStr, String baseDpStr) {
        try {
            Double original = (originalDpStr == null || originalDpStr.equals("")) ? 0.0 : Double.parseDouble(originalDpStr);
            Double base = (baseDpStr == null || baseDpStr.equals("")) ? 1.0 : Double.parseDouble(baseDpStr);

            SystemAssert.requireArgument(base != 0.0, "Points in base metric shouldn't contain zero as their point values!");
            return String.valueOf(original / base);
        } catch (Exception e) {
            throw new SystemException("Fail to parse the double value of original Datapoint or base Datapoint!", e);
        }
    }

    @Override
    public String name() {
        return TransformFactory.Function.DIVIDE_V.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
