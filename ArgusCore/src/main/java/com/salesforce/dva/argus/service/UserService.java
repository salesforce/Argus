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

import com.salesforce.dva.argus.entity.PrincipalUser;
import java.math.BigInteger;

/**
 * Provides methods relevant to system users.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface UserService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Retrieves a principal user based on the user name.
     *
     * @param   userName  The user name of the user to retrieve. Cannot be null or empty.
     *
     * @return  The principal user or null if no user exists for the given user name.
     */
    PrincipalUser findUserByUsername(String userName);

    /**
     * Retrieves a principal user based on the primary key ID.
     *
     * @param   id  The primary key ID. Cannot be null and must be a positive non-zero number.
     *
     * @return  The principal user or null if no user exists for the give primary key ID.
     */
    PrincipalUser findUserByPrimaryKey(BigInteger id);

    /**
     * Retrieves the system wide administrative user, creating it if necessary.
     *
     * @return  The administrative user. Will never return null.
     */
    PrincipalUser findAdminUser();
    
    /**
     * Retrieves the system wide default non-privileged user, creating it if necessary. This is the user that will be used 
     * when the system is configured to use NoAuthService.
     *
     * @return  The default user. Will never return null.
     */
    PrincipalUser findDefaultUser();

    /**
     * Deletes a user.
     *
     * @param  user  The user to delete. Cannot be the administrative user.
     */
    void deleteUser(PrincipalUser user);

    /**
     * Updates a user, creating it if necessary.
     *
     * @param   user  The user to update. Cannot be null.
     *
     * @return  The updated user. Will never return null.
     */
    PrincipalUser updateUser(PrincipalUser user);

    /**
     * Returns the unique user count.
     *
     * @return  The unique user count.
     */
    long getUniqueUserCount();
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
