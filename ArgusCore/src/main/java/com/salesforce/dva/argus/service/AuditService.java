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

import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.JPAEntity;
import java.math.BigInteger;
import java.util.List;

/**
 * Provides methods to create and retrieve audits.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public interface AuditService extends Service {

    //~ Methods **************************************************************************************************************************************

    /** Deletes expired audits. */
    void deleteExpiredAudits();

    /**
     * Creates a new audit record.
     *
     * @param   audit  The audit record to create. Cannot be null.
     *
     * @return  Created audit object.
     */
    Audit createAudit(Audit audit);

    /**
     * Creates a new audit record.
     *
     * @param   message  The message pattern.
     * @param   entity   The entity to attach the audit item to.
     * @param   params   The message parameters.
     *
     * @return  Created audit object.
     */
    Audit createAudit(String message, JPAEntity entity, Object... params);

    /**
     * Finds the audit object by Id.
     *
     * @param   id  Id of the audit record. Cannot be null and must be a positive non-zero number.
     *
     * @return  The audit record.
     */
    Audit findAuditByPrimaryKey(BigInteger id);

    /**
     * Finds audits for a given entity object.
     *
     * @param   entityId  The entity for which the audits are requested. Cannot be null.
     *
     * @return  List of audits for a given entity.
     */
    List<Audit> findByEntity(BigInteger entityId);

    /**
     * Finds audits for a given entity object.
     *
     * @param   entityId  The entity for which the audits are requested. Cannot be null.
     * @param   limit     The number of messages to retrieve.
     *
     * @return  List of audits for a given entity.
     */
    List<Audit> findByEntity(BigInteger entityId, BigInteger limit);

    /**
     * Finds audits for a given host name.
     *
     * @param   hostName  The name of the host name which created the audits. Cannot be null or empty.
     *
     * @return  List of audit records for a given host name.
     */
    List<Audit> findByHostName(String hostName);

    /**
     * Returns all audits.
     *
     * @return  List of audit objects.
     */
    List<Audit> findAll();

    /**
     * Returns list of audits for a given message.
     *
     * @param   message  The exception message. Cannot be null or empty.
     *
     * @return  List of audit records whose exception message matches with provided message.
     */
    List<Audit> findByMessage(String message);

    /**
     * Returns list of audits for a given JPA Entity, host name and message.
     *
     * @param   entityId  - Id of a JPA Entity.
     * @param   hostName  - The name of the host name which created the audits.
     * @param   message   - The exception message. Cannot be null or empty.
     *
     * @return  List of audit objects.
     */
    List<Audit> findByEntityHostnameMessage(BigInteger entityId, String hostName, String message);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
