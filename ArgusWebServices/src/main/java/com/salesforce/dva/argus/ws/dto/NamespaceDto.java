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
	 
package com.salesforce.dva.argus.ws.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.salesforce.dva.argus.entity.Namespace;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * The namespace DTO.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NamespaceDto extends EntityDTO {

    //~ Instance fields ******************************************************************************************************************************

    private String qualifier;
    private Set<String> usernames = new HashSet<String>();

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a namespace entity to a DTO.
     *
     * @param   namespace  The entity to convert.
     *
     * @return  The namespace DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static NamespaceDto transformToDto(Namespace namespace) {
        if (namespace == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        NamespaceDto result = createDtoObject(NamespaceDto.class, namespace);

        for (PrincipalUser user : namespace.getUsers()) {
            result.addUsername(user.getUserName());
        }
        return result;
    }

    /**
     * Converts list of alert entity objects to list of alertDto objects.
     *
     * @param   namespaces  users alerts List of alert entities. Cannot be null.
     *
     * @return  List of alertDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<NamespaceDto> transformToDto(List<Namespace> namespaces) {
        if (namespaces == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<NamespaceDto> result = new ArrayList<>();

        for (Namespace namespace : namespaces) {
            result.add(transformToDto(namespace));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespace qualifier.
     *
     * @return  The namespace qualifier.
     */
    public String getQualifier() {
        return qualifier;
    }

    /**
     * Sets the namespace qualifier.
     *
     * @param  qualifier  The namespace qualifier.
     */
    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    /**
     * Returns the authorized users.
     *
     * @return  The authorized users.
     */
    public Set<String> getUsernames() {
        return usernames;
    }

    /**
     * Sets the authorized users.
     *
     * @param  usernames  The authorized users.
     */
    public void setUsernames(Set<String> usernames) {
        this.usernames = usernames;
    }

    /**
     * Adds an authorized user.
     *
     * @param  username  The authorized user to add.
     */
    public void addUsername(String username) {
        SystemAssert.requireArgument(username != null && !username.isEmpty(), "Username cannot be null or empty.");
        this.usernames.add(username);
    }

    @Override
    public Object createExample() {
        // TODO Auto-generated method stub
        return null;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
