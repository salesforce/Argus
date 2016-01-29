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

import com.salesforce.dva.argus.entity.ServiceManagementRecord;

/**
 * Provides methods to enable and disable services across multiple JVMs.
 *
 * @author  Raj sarkapally (rsarkapally@salesforce.com)
 */
public interface ServiceManagementService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Updates ServiceManagementRecord entity.
     *
     * @param   serviceManagementRecord  The service management record to create or update. Cannot be null.
     *
     * @return  updated/created service management record.
     */
    ServiceManagementRecord updateServiceManagementRecord(ServiceManagementRecord serviceManagementRecord);

    /**
     * Returns boolean variable indicating whether the service is enabled or disabled.
     *
     * @param   service  The service whose status to be returned.
     *
     * @return  true if the service is enabled otherwise false.
     */
    boolean isServiceEnabled(ServiceManagementRecord.Service service);

    /**
     * Returns the service management record for the specified service.
     *
     * @param   service  The service. Cannot be null.
     *
     * @return  The resulting management record or null if no record exists.
     */
    ServiceManagementRecord findServiceManagementRecord(ServiceManagementRecord.Service service);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
