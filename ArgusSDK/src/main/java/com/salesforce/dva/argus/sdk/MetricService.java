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
import com.salesforce.dva.argus.sdk.ArgusService.PutResult;
import com.salesforce.dva.argus.sdk.entity.Metric;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Provides methods to manipulate metrics.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class MetricService extends EndpointService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESOURCE = "/metrics";
    private static final String COLLECTION_RESOURCE = "/collection";

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new MetricService object.
     *
     * @param  client  The HTTP client for use by the service.
     */
    MetricService(ArgusHttpClient client) {
        super(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the metrics for the given set of expressions.
     *
     * @param   expressions  The metric expressions to evaluate.
     *
     * @return  The metrics that match the given expressions.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Metric> getMetrics(List<String> expressions) throws IOException {
        StringBuilder requestUrl = new StringBuilder(RESOURCE);

        for (int i = 0; i < expressions.size(); i++) {
            requestUrl.append(i == 0 ? "?" : "&");
            requestUrl.append("expression=").append(expressions.get(i));
        }

        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl.toString(), null);

        assertValidResponse(response, requestUrl.toString());
        return fromJson(response.getResult(), new TypeReference<List<Metric>>() { });
    }

    /**
     * Submits metrics.
     *
     * @param   metrics  The metrics to submit. Cannot be null or empty.
     *
     * @return  A description of the operation result.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public PutResult putMetrics(List<Metric> metrics) throws IOException {
        String requestUrl = COLLECTION_RESOURCE + RESOURCE;
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, metrics);

        assertValidResponse(response, requestUrl);

        Map<String, Object> map = fromJson(response.getResult(), new TypeReference<Map<String, Object>>() { });

        List<String> errorMessages = (List<String>) map.get("Error Messages");

        return new PutResult(String.valueOf(map.get("Success")), String.valueOf(map.get("Errors")), errorMessages);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
