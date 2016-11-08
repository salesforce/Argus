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

import com.salesforce.dva.argus.entity.Namespace;
import com.salesforce.dva.argus.entity.PrincipalUser;
import java.math.BigInteger;
import java.util.List;

/**
 * Provides methods to manipulate namespaces.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface NamespaceService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespace for the given ID.
     *
     * @param   id  The ID for the namespace.  Must be a positive integer.
     *
     * @return  The corresponding namespace or null if no corresponding namespace exists.
     */
    Namespace findNamespaceByPrimaryKey(BigInteger id);

    /**
     * Creates a new namespace.
     *
     * @param   namespace  The namespace to create.  Cannot be null.
     *
     * @return  The new namespace with the ID field populated.
     */
    Namespace createNamespace(Namespace namespace);

    /**
     * Updates a namespace.
     *
     * @param   namespace  The namespace to update.
     *
     * @return  The updated namespace.
     */
    Namespace updateNamespace(Namespace namespace);

    /**
     * Indicates if a principal user is authorized for a give namespace.
     *
     * @param   namespace  The namespace name.  Cannot be null.
     * @param   user       The principal to check.  Cannot be null.
     *
     * @return  True if the user is authorized.
     */
    boolean isPermitted(String namespace, PrincipalUser user);

    /**
     * Finds the list of namespaces for a given owner.
     *
     * @param   owner  The owner.  Cannot be null.
     *
     * @return  The list of namespaces.  Will never return null, but may be empty.
     */
    List<Namespace> findNamespacesByOwner(PrincipalUser owner);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
