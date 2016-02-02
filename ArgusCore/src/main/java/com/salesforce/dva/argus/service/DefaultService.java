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
	 
package com.salesforce.dva.argus.service;

import com.google.inject.Inject;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static com.salesforce.dva.argus.system.SystemAssert.requireState;

import java.util.Properties;

import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * Abstract base class for disposable service implementations.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public abstract class DefaultService implements Service {

    //~ Instance fields ******************************************************************************************************************************

    private boolean _disposed = false;

    //~ Methods **************************************************************************************************************************************

    /** Disable the default constructor. */
    private DefaultService() {}
    
    /**
         * Automatically merges implementation specific service configuration properties with the overall system configuration.
         * 
         * @param systemConfiguration The system configuration instance for the current instance of the core Argus services as loaded by the 
         *                            <tt>SystemInitializer</tt>.
         */
    @Inject
    protected DefaultService (SystemConfiguration systemConfiguration){
        requireArgument(systemConfiguration != null, "The system configuration cannot be null.");
    }
    
    @Override
    public void dispose() {
        _disposed = true;
    }

    /** Throws an exception if the service is disposed. */
    protected final void requireNotDisposed() {
        requireState(!_disposed, "Cannot invoke a method on a disposed service.");
    }

    @Override
    public final boolean isDisposed() {
        return _disposed;
    }
    
    @Override
    public Properties getServiceProperties(){
        return new Properties();
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
