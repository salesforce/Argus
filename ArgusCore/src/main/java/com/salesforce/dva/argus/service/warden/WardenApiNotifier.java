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
	 
package com.salesforce.dva.argus.service.warden;

import com.google.inject.Inject;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.WardenService;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * Implementation of the warden alert notifier, for suspending a user from the API subsystem.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class WardenApiNotifier extends WardenNotifier {

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new WardenApiNotifier object.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     * @param  wardenService      The warden service. Cannot be null.
     * @param  tsdbService        The tsdb service instance to use. Cannot be null.
     * @param  mailService        The mail service instance to use. Cannot be null.
     * @param  config             The system configuration. Cannot be null.
     */
    @Inject
    private WardenApiNotifier(MetricService metricService, AnnotationService annotationService, WardenService wardenService, TSDBService tsdbService,
        MailService mailService, SystemConfiguration config) {
        super(metricService, annotationService, wardenService, tsdbService, mailService, config);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return WardenApiNotifier.class.getName();
    }

    @Override
    protected void sendAdditionalNotification(NotificationContext context) {
        _wardenService.suspendUser(super.getWardenUser(context.getAlert().getName()), SubSystem.API);
        super.addAnnotationSuspendedUser(context, SubSystem.API);
        super.sendWardenEmailToUser(context, SubSystem.API);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
