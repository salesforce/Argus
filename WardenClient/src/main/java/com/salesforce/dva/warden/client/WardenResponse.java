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
package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.warden.dto.WardenResource;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Warden request response object which encapsulates information about a completed request.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
public class WardenResponse<T> {

    //~ Instance fields ******************************************************************************************************************************

    @JsonInclude(Include.NON_NULL)
    private List<WardenResource<T>> _resources;
    private int _status;
    private String _message;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new WardenResponse object. */
    WardenResponse() { }

    //~ Methods **************************************************************************************************************************************

    static <T> WardenResponse<T> generateResponse(HttpResponse response) throws IOException {
        EntityUtils.consume(response.getEntity());

        int status = response.getStatusLine().getStatusCode();
        String message;

        if (status >= 200 && status < 300) {
            message = response.getStatusLine().getReasonPhrase();
        } else {
            message = response.getStatusLine().getReasonPhrase();
        }

        HttpEntity entity = response.getEntity();
        List<WardenResource<T>> resources = new ArrayList<>();

        if (entity != null) {
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                entity.writeTo(baos);

                String payload = baos.toString();

                resources.addAll(new ObjectMapper().readValue(payload, new TypeReference<List<WardenResource<T>>>() { }));
            }
        }

        WardenResponse result = new WardenResponse();

        result.setMessage(message);
        result.setStatus(status);
        result.setResources(resources);
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public List<WardenResource<T>> getResources() {
        return _resources;
    }

    /**
     * Returns the HTTP status code for the response.
     *
     * @return  The HTTP status code for the response.
     */
    public int getStatus() {
        return _status;
    }

    /**
     * The message associated with the response.
     *
     * @return  The message associated with the response.
     */
    public String getMessage() {
        return _message;
    }

    void setResources(List<WardenResource<T>> resources) {
        this._resources = resources;
    }

    void setStatus(int status) {
        this._status = status;
    }

    void setMessage(String message) {
        this._message = message;
    }

    @Override
    public int hashCode() {
        int hash = 3;

        hash = 73 * hash + Objects.hashCode(this._resources);
        hash = 73 * hash + this._status;
        hash = 73 * hash + Objects.hashCode(this._message);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final WardenResponse<?> other = (WardenResponse<?>) obj;

        if (this._status != other._status) {
            return false;
        }
        if (!Objects.equals(this._message, other._message)) {
            return false;
        }
        if (!Objects.equals(this._resources, other._resources)) {
            return false;
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
