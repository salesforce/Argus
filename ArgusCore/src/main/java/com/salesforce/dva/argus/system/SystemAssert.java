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
import java.util.function.Predicate;

/**
 * Provides functionality to assert that certain conditions are met.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com), Ian Keck (ikeck@salesforce.com)
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
        if (!condition) {
            raiseException(message, IllegalArgumentException.class);
        }
    }

    /**
     * Throws an IllegalStateException if the condition is not met.
     *
     * @param  condition  The condition to evaluate,
     * @param  message    The exception message.
     */
    public static void requireState(boolean condition, String message) {
        if (!condition) {
            raiseException(message, IllegalStateException.class);
        }
    }


    /**
     * Throws an exception of the specified type and message.
     *
     * @param  message    Message to return
     * @param  type       Type of RuntimeException to raise.
     */
    private static <T extends RuntimeException> void raiseException(String message, Class<T> type) throws RuntimeException
    {
        RuntimeException result;
        try {
            result = type.getConstructor(String.class).newInstance(message);
        } catch (Exception ex)  {
            throw new SystemException(ex);
        }
        throw result;
    }


    // NOTE - these functions add a mechanism that allow you to capture error messages in arbitrary exceptions thrown by the test code.

    /**
     * Throws an IllegalArgumentException if the predicate fails.
     *
     * @param  arg        Object to test
     * @param  t          The predicate to evaluate,
     * @param  message    The exception message.
     */
    public static <P> void requireArgumentP(P arg, Predicate<P> t, String message, boolean captureMsg) {
        requirePredicate(arg, t, message, IllegalArgumentException.class, captureMsg);
    }

    public static <P> void requireArgumentP(P arg, Predicate<P> t, String message) {
        requirePredicate(arg, t, message, IllegalArgumentException.class, true);
    }

    /**
     * Throws an IllegalStateException if the predicate fails.
     *
     * @param  arg        Object to test
     * @param  t          The condition to evaluate,
     * @param  message    The exception message.
     */
    public static <P> void requireStateP(P arg, Predicate<P> t, String message, boolean captureMsg) {
        requirePredicate(arg, t, message, IllegalStateException.class, captureMsg);
    }

    public static <P> void requireStateP(P arg, Predicate<P> t, String message) {
        requirePredicate(arg, t, message, IllegalStateException.class, true);
    }


    private static <T extends RuntimeException, P> void requirePredicate(P arg, Predicate<P> t, String message, Class<T> type, boolean captureMessage) {

        boolean ok = true;
        String msg = message;
        try
        {
            ok = t.test(arg);
        }
        catch (RuntimeException ex)
        {
            ok = false;
            if (captureMessage)
            {
                Throwable e = ex.getCause();
                msg = (e != null)? e.getMessage() : ex.getMessage();
            }
        }
        catch (Exception e)
        {
            ok = false;
            if (captureMessage)
            {
                msg = e.getMessage();
            }
        }

        if (!ok)
        {
            raiseException(msg, type);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
