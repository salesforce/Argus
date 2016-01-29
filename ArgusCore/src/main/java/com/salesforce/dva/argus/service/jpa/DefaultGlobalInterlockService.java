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
	 
package com.salesforce.dva.argus.service.jpa;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.GlobalInterlock;
import com.salesforce.dva.argus.entity.GlobalInterlock.GlobalInterlockException;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.GlobalInterlockService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the global interlock service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultGlobalInterlockService extends DefaultJPAService implements GlobalInterlockService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new DefaultGlobalInterlock object. 
     * @param _sysConfig Service Properties*/
    @Inject
    public DefaultGlobalInterlockService(SystemConfiguration _sysConfig) {
        super(null, _sysConfig);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String obtainLock(long expiration, LockType type, String note) {
        requireNotDisposed();
        requireArgument(expiration >= 0, "Expiration must be positive.");
        requireArgument(type != null, "Lock type cannot be null.");
        requireArgument(note != null && !note.isEmpty(), "Note cannot be null or empty.");
        try {
            _logger.debug("Attempting to obtain lock of type {}.", type);
            return GlobalInterlock.obtainLock(emf.get(), expiration, type.ordinal() + 1, note);
        } catch (GlobalInterlockException ex) {
            _logger.debug(ex.getMessage());
            return null;
        }
    }

    @Override
    public boolean releaseLock(LockType type, String key) {
        requireNotDisposed();
        requireArgument(type != null, "Lock type cannot be null.");
        requireArgument(key != null && !key.isEmpty(), "Key cannot be null or empty.");
        try {
            _logger.debug("Attempting to release lock of type {} having key {}.", type, key);
            GlobalInterlock.releaseLock(emf.get(), type.ordinal() + 1, key);
            return true;
        } catch (GlobalInterlockException ex) {
            _logger.warn(ex.getMessage());
            return false;
        }
    }

    @Override
    public String refreshLock(LockType type, String key, String note) {
        requireNotDisposed();
        requireArgument(type != null, "Lock type cannot be null.");
        requireArgument(key != null && !key.isEmpty(), "Key cannot be null or empty.");
        requireArgument(note != null && !note.isEmpty(), "Note cannot be null or empty.");
        try {
            _logger.debug("Attempting to refresh lock of type {} having key {}.", type, key);
            return GlobalInterlock.refreshLock(emf.get(), type.ordinal() + 1, key, note);
        } catch (GlobalInterlockException ex) {
            _logger.warn(ex.getMessage());
            return null;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
