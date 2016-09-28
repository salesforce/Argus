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
package com.salesforce.dva.argus.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.salesforce.dva.argus.sdk.ArgusHttpClient.ArgusResponse;
import com.salesforce.dva.argus.sdk.ArgusService.EndpointService;
import com.salesforce.dva.argus.sdk.entity.Namespace;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * Provides methods to manipulate metrics.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class NamespaceService extends EndpointService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESOURCE = "/namespace";

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new NamespaceService object.
     *
     * @param  client  The HTTP client for use by the service.
     */
    NamespaceService(ArgusHttpClient client) {
        super(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespaces owned by the user.
     *
     * @return  The namespaces owned by the user.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Namespace> getNamespaces() throws IOException {
        String requestUrl = RESOURCE;
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Namespace>>() { });
    }

    /**
     * Creates a new namespace.
     *
     * @param   namespace  The namespace to create.
     *
     * @return  The persisted namespace.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Namespace createNamespace(Namespace namespace) throws IOException {
        String requestUrl = RESOURCE;
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, namespace);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Namespace.class);
    }

    /**
     * Updates a new namespace.
     *
     * @param   id         The ID of the namespace to update.
     * @param   namespace  The namespace to update.
     *
     * @return  The updated namespace.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Namespace updateNamespace(BigInteger id, Namespace namespace) throws IOException {
        String requestUrl = RESOURCE + "/" + id.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.PUT, requestUrl, namespace);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Namespace.class);
    }

    /**
     * Updates a the members of a namespace.
     *
     * @param   id     The ID of the namespace to update.
     * @param   users  The updated members of the namespace.
     *
     * @return  The updated namespace.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Namespace updateNamespaceMembers(BigInteger id, Set<String> users) throws IOException {
        String requestUrl = RESOURCE + "/" + id.toString() + "/users";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.PUT, requestUrl, users);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Namespace.class);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
