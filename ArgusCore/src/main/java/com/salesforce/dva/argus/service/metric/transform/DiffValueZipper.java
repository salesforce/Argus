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

import com.salesforce.dva.argus.system.SystemException;

/**
 * Make a map of datapoints that aggregates points from originalDatapoints and baseDatapoints, if for some timestamp in originalDatapoints, there is
 * no point value in the baseDatapoints corresponding to that timestamp, the aggregation result is subtraction of point value in originalDatapoints
 * and ZERO.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DiffValueZipper implements ValueZipper {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Double zip(Double originalDp, Double baseDp) {
        try {
            Double original = (originalDp == null) ? 0.0 : originalDp;
            Double base = (baseDp == null) ? 0.0 : baseDp;

            return (original - base);
        } catch (Exception e) {
            throw new SystemException("Fail to parse the double value of original Datapoint or base Datapoint!", e);
        }
    }

    @Override
    public String name() {
        return TransformFactory.Function.DIFF_V.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
