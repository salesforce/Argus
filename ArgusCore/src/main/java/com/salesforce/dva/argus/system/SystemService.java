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

import com.salesforce.dva.argus.inject.SLF4JTypeListener.InjectLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CountDownLatch;

/**
 * Base class for the main service that provides interlocking between <tt>start</tt> and <tt>stop</tt> methods. This class ensures that calls to the
 * <tt>stop</tt> method block until a call to the <tt>start</tt> method has completed successfully. No wait checking or monitoring is provided by this
 * class. This class is a one-shot implementation. The <tt>start</tt> and <tt>stop</tt> methods may only be invoked once per instance. Any subsequent
 * calls to those methods will have no effect.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public abstract class SystemService {

    //~ Instance fields ******************************************************************************************************************************

    private final CountDownLatch _interlock;
    private boolean _unlocked = false;
    private boolean _started = false;
    @InjectLogger
    protected Logger _log;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new SystemService object. */
    protected SystemService() {
        _interlock = new CountDownLatch(1);
        _log = LoggerFactory.getLogger(getClass());
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the name of the service.
     *
     * @return  The service name.
     */
    public String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Indicates the interlock _started successfully.
     *
     * @return  true If the interlock _started successfully.
     */
    public final boolean isStarted() {
        return _started;
    }

    /**
     * Indicates if a call to <tt>start</tt> has occurred.
     *
     * @return  true If a call to <tt>start</tt> has occurred.
     */
    public final boolean isUnlocked() {
        return _unlocked;
    }

    /**
     * Performs the actions of the {@link #doStart()} method. After this method completes either by normal execution or by throwing an exception, the
     * interlock condition is satisfied and calls to {@link #stop()} may proceed.
     *
     * @throws  SystemException  If any exception is thrown by the underlying implementation.
     */
    public final void start() {
        try {
            if (!_unlocked) {
                _log.info("Starting {}", getName());
                doStart();
            }
            _started = true;
        } catch (Exception ex) {
            throw new SystemException(ex);
        } finally {
            _unlocked = true;
            _interlock.countDown();
        }
    }

    /**
     * Performs the actions of the {@link #doStop()} method. After this method completes either by normal execution or by throwing an exception, the
     * interlock condition is satisfied and calls to {@link #start()} may proceed.
     */
    public final void stop() {
        SystemException finalException = null;

        try {
            _interlock.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                if (_unlocked) {
                    _log.info("Stopping {}.", getName());
                    doStop();
                }
            } catch (Exception ex) {
                finalException = new SystemException(ex);
            } finally {
                _started = false;
            }
        }
        if (finalException != null) {
            throw finalException;
        }
    }

    /** Actions to perform when starting. Liveness checks must be performed by the implementation. */
    protected void doStart() { }

    /** Actions to perform when stopping. Liveness checks must be performed by the implementation. */
    protected void doStop() { }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
