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

import java.math.BigInteger;

import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.Preferences;

/**
 *
 * Provides methods to create, update and read Preferences entities.
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */
public interface PreferencesService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Updates a preferences object.
     *
     * @param preferences   preferences to update. Cannot be null.
     * @return              Updated preferences object.Will never return null.
     */
	Preferences updatePreferences(Preferences preferences);

    /**
     * Gets preferences object based on userId and entityId
     *
     * @param userId        userId of the person who created this preferences.Cannot be null or empty.
     * @param entityId      entityId of the preferences we are interested in.Cannot be null or empty.
     * @return              The preferences if one exists or null if no such preferences exists.
     */
	Preferences getPreferencesByUserAndEntity(BigInteger userId, BigInteger entityId);

    /**
     * Gets preferences object based on entityId
     *
     * @param entityId      entityId of the preferences we are interested in.Cannot be null or empty.
     * @return              The preferences if one exists or null if no such preferences exists.
     */
	Preferences getPreferencesByEntity(BigInteger entityId);

    /**
     * Retrieves the associated entity based on the primary key ID.
     *
     * @param   entityId	The primary key ID. Cannot be null and must be a positive non-zero number.
     *
     * @return  The entity or null if no entity exists for the given primary key ID.
     */
	JPAEntity getAssociatedEntity(BigInteger entityId);


}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */