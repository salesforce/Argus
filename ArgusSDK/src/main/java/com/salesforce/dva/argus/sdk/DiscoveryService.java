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
import com.salesforce.dva.argus.sdk.entity.MetricSchemaRecord;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Provides methods to discover metric schema.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DiscoveryService extends EndpointService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESOURCE = "/discover";

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DiscoveryService object.
     *
     * @param  client  The HTTP client for use by the service.
     */
    DiscoveryService(ArgusHttpClient client) {
        super(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the schema records matching the specified criteria. At least one field matching expression must be supplied.
     *
     * @param   namespaceRegex  The regular expression on which to match the name space field. May be null.
     * @param   scopeRegex      The regular expression on which to match the scope field. May be null.
     * @param   metricRegex     The regular expression on which to match the metric field. May be null.
     * @param   tagKeyRegex     The regular expression on which to match the tag key field. May be null.
     * @param   tagValueRegex   The regular expression on which to match the tag value field. May be null.
     * @param   limit           The maximum number of records to return. Must be a positive non-zero integer.
     *
     * @return  The matching schema records.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<MetricSchemaRecord> getMatchingRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagKeyRegex,
        String tagValueRegex, int limit) throws IOException {
        StringBuilder urlBuilder = _buildBaseUrl(namespaceRegex, scopeRegex, metricRegex, tagKeyRegex, tagValueRegex, limit);
        String requestUrl = urlBuilder.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<MetricSchemaRecord>>() { });
    }

    /**
     * Returns the schema record fields values matching the specified criteria. At least one field matching expression must be supplied.
     *
     * @param   namespaceRegex  The regular expression on which to match the name space field. May be null.
     * @param   scopeRegex      The regular expression on which to match the scope field. May be null.
     * @param   metricRegex     The regular expression on which to match the metric field. May be null.
     * @param   tagKeyRegex     The regular expression on which to match the tag key field. May be null.
     * @param   tagValueRegex   The regular expression on which to match the tag value field. May be null.
     * @param   type            The field type to return values for.
     * @param   limit           The maximum number of records to return. Must be a positive non-zero integer.
     *
     * @return  The matching schema records.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<String> getMatchingRecordFields(String namespaceRegex, String scopeRegex, String metricRegex, String tagKeyRegex,
        String tagValueRegex, FieldSelector type, int limit) throws IOException {
        StringBuilder urlBuilder = _buildBaseUrl(namespaceRegex, scopeRegex, metricRegex, tagKeyRegex, tagValueRegex, limit);

        urlBuilder.append("&type=").append(type.name().toLowerCase(Locale.ENGLISH));

        String requestUrl = urlBuilder.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<String>>() { });
    }

    private StringBuilder _buildBaseUrl(String namespaceRegex, String scopeRegex, String metricRegex, String tagKeyRegex, String tagValueRegex,
        int limit) {
        StringBuilder urlBuilder = new StringBuilder(RESOURCE).append("?");

        if (namespaceRegex != null) {
            urlBuilder.append("namespace=").append(namespaceRegex).append("&");
        }
        if (scopeRegex != null) {
            urlBuilder.append("scope=").append(scopeRegex).append("&");
        }
        if (metricRegex != null) {
            urlBuilder.append("metric=").append(metricRegex).append("&");
        }
        if (tagKeyRegex != null) {
            urlBuilder.append("tagk=").append(tagKeyRegex).append("&");
        }
        if (tagValueRegex != null) {
            urlBuilder.append("tagv=").append(tagValueRegex).append("&");
        }
        urlBuilder.append("limit=").append(limit);
        return urlBuilder;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Specifies the field for which to return values from for discovery service methods that return a list of field values.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum FieldSelector {

        /** Match against the name space field. */
        NAMESPACE,
        /** Match against the scope field. */
        SCOPE,
        /** Match against the metric field. */
        METRIC,
        /** Match against the tag key field. */
        TAGK,
        /** Match against the tag value field. */
        TAGV;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
