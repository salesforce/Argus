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
import com.salesforce.dva.argus.sdk.entity.Batch;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Provides methods to manipulate batches.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class BatchService extends EndpointService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESOURCE = "/batches";
    private static final String METRIC_RESOURCE = "/metrics";

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new BatchService object.
     *
     * @param  client  The HTTP client for use by the service.
     */
    BatchService(ArgusHttpClient client) {
        super(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the audit history for the given entity.
     *
     * @param   expressions  The list of metric queries to batch.
     * @param   ttl          The time to live in seconds for the batch.
     *
     * @return  The ID of the batch.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public BigInteger createBatch(List<String> expressions, int ttl) throws IOException {
        StringBuilder requestUrl = new StringBuilder(METRIC_RESOURCE).append("/batch?");

        expressions.stream().forEach((expression) -> { requestUrl.append("expression=").append(expression); });
        requestUrl.append("&ttl=").append(ttl);

        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl.toString(), null);

        assertValidResponse(response, requestUrl.toString());

        Map<String, String> result = fromJson(response.getResult(), new TypeReference<Map<String, String>>() { });
        return new BigInteger(result.get("id"));
    }

    /**
     * Deletes a batch.
     *
     * @param   batchId  The ID of the batch to delete.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void deleteBatch(BigInteger batchId) throws IOException {
        String requestUrl = RESOURCE + "/" + batchId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Returns the batch for the given ID.
     *
     * @param   batchId  The ID of the batch to retrieve.
     *
     * @return  The batch.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Batch getBatch(BigInteger batchId) throws IOException {
        String requestUrl = RESOURCE + "/" + batchId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Batch.class);
    }

    /**
     * Returns the list of batches owned by the user.
     *
     * @return  The list of batches owned by the user.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Map<String, String> getBatches() throws IOException {
        String requestUrl = RESOURCE;
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<Map<String, String>>() { });
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
