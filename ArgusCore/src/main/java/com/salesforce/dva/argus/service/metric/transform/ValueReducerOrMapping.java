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

import java.util.List;
import java.util.Map;

/**
 * This interface is used to perform a mapping or reducing operation based on input constant.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public interface ValueReducerOrMapping {

    //~ Methods **************************************************************************************************************************************

    /**
     * Apply function to every datapoint of a datapoint map and return a new datapoint.
     *
     * @param   originalDatapoints  datapoint values The values to map.
     *
     * @return  The value after mapping.
     */
    Map<Long, String> mapping(Map<Long, String> originalDatapoints);

    /**
     * Apply function to every datapoint of a datapoint map and return a new datapoint.
     *
     * @param   originalDatapoints  map values The values to map, constants constant value used by mapping function.
     * @param   constants           input constants
     *
     * @return  The value after mapping.
     */
    Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants);

    /**
     * Reduce a set of values to a single value.
     *
     * @param   values  The values to reduce.
     *
     * @return  The reduced value.
     */
    String reduce(List<String> values);

    /**
     * Reduce a set of values to a single value.
     *
     * @param   values     The values to reduce. *
     * @param   constants  Constants input
     *
     * @return  The reduced value.
     */
    String reduce(List<String> values, List<String> constants);

    /**
     * Returns the name of the value mapping/reducer.
     *
     * @return  The name of the value mapping/reducer.
     */
    String name();
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
