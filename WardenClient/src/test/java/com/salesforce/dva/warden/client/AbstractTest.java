/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.entity.StringEntity;
import static org.mockito.Mockito.*;
/**
 *
 * @author tvaline
 */
public abstract class AbstractTest {
    
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.ANY);
        MAPPER.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY);
    }

    private static class HttpRequestResponse {
        private HttpRequestResponse() {}
        private WardenHttpClient.RequestType type;
        private String endpoint;
        private String jsonInput;
        private int status;
        private String message;
        private String jsonOutput;

        public WardenHttpClient.RequestType getType() {
            return type;
        }

        public void setType(WardenHttpClient.RequestType type) {
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
    
    WardenHttpClient getMockedClient(String jsonFile) {
        try {
            String endpoint = "https://localhost:8080/wardenws";
            WardenHttpClient client = spy(new WardenHttpClient(endpoint,10,10,10));
            HttpRequestResponse[] steps = MAPPER.readValue(getClass().getResource(jsonFile), HttpRequestResponse[].class);
            for (HttpRequestResponse step : steps) {
                HttpResponse mockedResponse = mock(HttpResponse.class);
                StatusLine mockedStatusLine = mock(StatusLine.class);
                when(mockedStatusLine.getStatusCode()).thenReturn(step.status);
                when(mockedStatusLine.getReasonPhrase()).thenReturn(step.message);
                when(mockedResponse.getEntity()).thenReturn(new StringEntity(step.jsonOutput));
                when(mockedResponse.getStatusLine()).thenReturn(mockedStatusLine);
                doReturn(mockedResponse).when(client).doHttpRequest(step.type, endpoint + step.endpoint, step.jsonInput);
            }
            return client;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
    
}
