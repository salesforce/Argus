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
import com.google.inject.Provider;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import com.salesforce.dva.argus.service.alert.notifier.EmailNotifier;
import com.salesforce.dva.argus.service.alert.notifier.GOCNotifier;
import com.salesforce.dva.argus.service.alert.notifier.GusNotifier;
import com.salesforce.dva.argus.service.warden.WardenApiNotifier;
import com.salesforce.dva.argus.service.warden.WardenPostingNotifier;

/**
 * The system service factory module. All services should be obtained from this class via injection.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com), Raj Sarkapally (rsarkapally@salesforce.com)
 */
public final class NotifierFactory {

    //~ Instance fields ******************************************************************************************************************************
	
	@Inject
    private Provider<EmailNotifier> _emailNotifierProvider;
    @Inject
    private Provider<GOCNotifier> _gocNotifierProvider;
    @Inject
    private Provider<AuditNotifier> _dbNotifierProvider;
    @Inject
    private Provider<WardenApiNotifier> _wardenApiNotifierProvider;
    @Inject
    private Provider<WardenPostingNotifier> _wardenPostingNotifierProvider;
    @Inject
    private Provider<GusNotifier> _gusNotifierProvider;
    
    
    /**
     * Returns an instance of the Email Notifier.
     *
     * @return  An instance of the Email Notifier.
     */
    public synchronized EmailNotifier getEmailNotifier() {
        return _emailNotifierProvider.get();
    }
    
    /**
     * Returns an instance of the GOC Notifier.
     *
     * @return  An instance of the GOC Notifier.
     */
    public synchronized GOCNotifier getGOCNotifier() {
        return _gocNotifierProvider.get();
    }
    
    /**
     * Returns an instance of the DB Notifier.
     *
     * @return  An instance of the DB Notifier.
     */
    public synchronized AuditNotifier getDBNotifier() {
        return _dbNotifierProvider.get();
    }
    
    /**
     * Returns an instance of the Wardern API Notifier.
     *
     * @return  An instance of the Warden API Notifier.
     */
    public synchronized WardenApiNotifier getWardenApiNotifier() {
        return _wardenApiNotifierProvider.get();
    }
    
    /**
     * Returns an instance of the Wardern Posting Notifier.
     *
     * @return  An instance of the Warden Posting Notifier.
     */
    public synchronized WardenPostingNotifier getWardenPostingNotifier() {
        return _wardenPostingNotifierProvider.get();
    }

    /**
     * Returns an instance of the GUS Notifier.
     *
     * @return  An instance of the GUS Notifier.
     */
    public synchronized GusNotifier getGusNotifier() {
        return _gusNotifierProvider.get();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
