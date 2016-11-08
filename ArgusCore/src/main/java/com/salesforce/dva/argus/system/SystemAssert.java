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
	 
package com.salesforce.dva.argus.system;

/**
 * Provides functionality to assert that certain conditions are met.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class SystemAssert {

    //~ Constructors *********************************************************************************************************************************

    /* Private constructor to prevent instantiation. */
    private SystemAssert() {
        assert (false) : "This class should never be instantiated.";
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Throws an IllegalArgumentException if the condition is not met.
     *
     * @param  condition  The condition to evaluate,
     * @param  message    The exception message.
     */
    public static void requireArgument(boolean condition, String message) {
        require(condition, message, IllegalArgumentException.class);
    }

    /**
     * Throws an IllegalStateException if the condition is not met.
     *
     * @param  condition  The condition to evaluate,
     * @param  message    The exception message.
     */
    public static void requireState(boolean condition, String message) {
        require(condition, message, IllegalStateException.class);
    }

    private static <T extends RuntimeException> void require(boolean condition, String message, Class<T> type) {
        if (!condition) {
            RuntimeException result;

            try {
                result = type.getConstructor(String.class).newInstance(message);
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
            throw result;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
