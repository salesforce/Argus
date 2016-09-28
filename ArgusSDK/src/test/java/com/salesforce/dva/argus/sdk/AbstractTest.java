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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.mockito.Mockito.*;

public abstract class AbstractTest {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.ANY);
        MAPPER.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY);
    }
    
    protected enum ITParam {
        ENDPOINT("argus.endpoint"),
        USERNAME("argus.username"),
        PASSWORD("argus.password");
        private final String _value;
        
        private ITParam(String key) {
            _value = getTestParameters().getProperty(key);
        }
        
        public String getValue() {
            return _value;
        }
        
        
        private Properties getTestParameters() {
            Properties result = new Properties();
            try (InputStream is = getClass().getResourceAsStream("/integration-test.properties")) {
                result.load(is);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return result;
        }

    }
    
    ArgusHttpClient getMockedClient(String jsonFile) throws IOException {
        String endpoint = "https://localhost:8080/argusws";
        ArgusHttpClient client = spy(new ArgusHttpClient(endpoint, 10, 10, 10));
        HttpRequestResponse[] steps = MAPPER.readValue(AbstractTest.class.getResource(jsonFile), HttpRequestResponse[].class);

        for (HttpRequestResponse step : steps) {
            HttpResponse mockedResponse = mock(HttpResponse.class);
            StatusLine mockedStatusLine = mock(StatusLine.class);

            when(mockedStatusLine.getStatusCode()).thenReturn(step.status);
            when(mockedStatusLine.getReasonPhrase()).thenReturn(step.message);
            when(mockedResponse.getEntity()).thenReturn(new StringEntity(step.jsonOutput));
            when(mockedResponse.getStatusLine()).thenReturn(mockedStatusLine);
            doReturn(mockedResponse).when(client)._doHttpRequest(step.type, endpoint + step.endpoint, step.jsonInput);
        }
        return client;
    }

    private static class HttpRequestResponse {

        private ArgusHttpClient.RequestType type;
        private String endpoint;
        private String jsonInput;
        private int status;
        private String message;
        private String jsonOutput;

        private HttpRequestResponse() { }

        public ArgusHttpClient.RequestType getType() {
            return type;
        }

        public void setType(ArgusHttpClient.RequestType type) {
            this.type = type;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getJsonInput() {
            return jsonInput;
        }

        public void setJsonInput(String jsonInput) {
            this.jsonInput = jsonInput;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getJsonOutput() {
            return jsonOutput;
        }

        public void setJsonOutput(String jsonOutput) {
            this.jsonOutput = jsonOutput;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
