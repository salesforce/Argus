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

/**
 * Evaluation type: ABOVE/BELOW/HIGHEST/LOWEST: average, min, max, recent SORT : maxima, minima, name, dev DOWNSAMPLER : avg, min, max, sum, dev
 * CULL_ABOVE/BELOW : percentile, value MOVING: average, median
 *
 * <p>So far, make AVERAGE value is avg(not average)</p>
 *
 * @author  rzhang
 */
public enum InternalReducerType {

    //~ Enum constants *******************************************************************************************************************************

    AVG("avg"),
    MIN("min"),
    MAX("max"),
    RECENT("recent"),
    MAXIMA("maxima"),
    MINIMA("minima"),
    NAME("name"),
    DEVIATION("dev"),
    SUM("sum"),
    MEDIAN("median"),
	COUNT("count");

    //~ Instance fields ******************************************************************************************************************************

    /** The reducer name. */
    public final String reducerName;

    //~ Constructors *********************************************************************************************************************************

    private InternalReducerType(String reducerName) {
        this.reducerName = reducerName;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Creates a reducer type corresponding to the given name.
     *
     * @param   name  The name of the reducer.
     *
     * @return  The corresponding reducer type.
     *
     * @throws  IllegalArgumentException  If an unknown reducer type is specified.
     */
    public static InternalReducerType fromString(String name) {
        if (name != null) {
            for (InternalReducerType type : InternalReducerType.values()) {
                if (name.equalsIgnoreCase(type.getName())) {
                    return type;
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the reducer name.
     *
     * @return  The reducer name.
     */
    public String getName() {
        return reducerName;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
