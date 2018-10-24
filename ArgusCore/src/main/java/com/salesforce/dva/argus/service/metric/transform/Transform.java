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
import com.salesforce.dva.argus.util.QueryContext;

import java.util.List;

/**
 * Represents a multiple input, single output transform which can be applied to a time series metric.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
public interface Transform {

    //~ Methods **************************************************************************************************************************************

    /**
     * Applies the transform to one or metrics. Implementations of this interface method shall perform any validation on the list of input metrics as
     * required by the implementation.
     * @param context This contains details of the underlying tsdb query
     * @param   metrics  The list of metrics to which the transform will be applied. Cannot be null.
     *
     * @return  The result of the transform. Shall not be null.
     */
    List<Metric> transform(QueryContext context, List<Metric> metrics);

    /**
     * Applies the transform to one or metrics. Implementations of this interface method shall perform any validation on the list of input metrics as
     * required by the implementation.
     * @param queryContext This contains details of the underlying tsdb query
     * @param   metrics    The list of metrics to which the transform will be applied. Cannot be null.
     * @param   constants  The transform specific constants to use.
     *
     * @return  The result of the transform.  Shall not be null.
     */
    List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants);

    /**
     * Applies the transform to one or metrics. Implementations of this interface method shall perform any validation on the list of input metrics as
     * required by the implementation.
     * @param queryContext This contains details of the underlying tsdb query
     * @param   metrics  The list of metrics to which the transform will be applied. Cannot be null.
     *
     * @return  The result of the transform. Shall not be null.
     */
    List<Metric> transform(QueryContext queryContext, List<Metric>... metrics);

    /**
     * Returns the scope name for the result.
     *
     * @return  The scope name for the result.
     */
    String getResultScopeName();
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
