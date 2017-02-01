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

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.equalTo;

public class MaxValueReducerTest {

    @Test
    public void reduce_shouldReturnNullWhenZeroValues() {
        MaxValueReducer r = new MaxValueReducer();

        assertThat(r.reduce(new ArrayList<Double>()), is(nullValue()));
    }

    @Test
    public void reduce_shouldReturnMaxWhenSingleValue() {
        MaxValueReducer r = new MaxValueReducer();

        assertThat(r.reduce(Arrays.asList(1.0)), equalTo(1.0));
    }

    @Test
    public void reduce_shouldReturnMaxWithMultipleDoubles() {
        MaxValueReducer r = new MaxValueReducer();

        assertThat(r.reduce(Arrays.asList(5.0, 1.0, 4.0)), equalTo(5.0));
    }

    @Test
    public void reduce_shouldReturnMaxWithNullValue() {
        MaxValueReducer r = new MaxValueReducer();

        assertThat(r.reduce(Arrays.asList(null, 5.0, 1.0, 4.0)), equalTo(5.0));
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
