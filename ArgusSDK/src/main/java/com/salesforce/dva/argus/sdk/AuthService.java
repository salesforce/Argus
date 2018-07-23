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
import com.salesforce.dva.argus.sdk.entity.Credentials;
import com.salesforce.dva.argus.sdk.exceptions.TokenExpiredException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpStatus;

/**
 * Provides methods to authenticate to Argus.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class AuthService extends EndpointService {

	//~ Static fields/initializers *******************************************************************************************************************

    private static final String RESOURCE = "/v2/auth";

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new AuthService object.
     *
     * @param  client  The HTTP client for use by the service.
     */
    AuthService(ArgusHttpClient client) {
        super(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Logs into Argus.
     *
     * @param   username  The username.
     * @param   password  The password.
     *
     * @throws  IOException  If the server is unavailable.
     */
    public void login(String username, String password) throws IOException {
        String requestUrl = RESOURCE + "/login";
        Credentials creds = new Credentials();

        creds.setPassword(password);
        creds.setUsername(username);

        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, creds);

        try {
		assertValidResponse(response, requestUrl);
	} catch (TokenExpiredException e) {
		//This should never happen
		throw new RuntimeException("This should never happen. login() method should never throw a TokenExpiredException", e);
	}
        
        Map<String, String> tokens = fromJson(response.getResult(), new TypeReference<Map<String, String>>() {});
        getClient().accessToken = tokens.get("accessToken");
        getClient().refreshToken = tokens.get("refreshToken");
    }
    
    public void obtainNewAccessToken() throws TokenExpiredException, IOException {
    	String requestUrl = RESOURCE + "/token/refresh";
    	Map<String, String> token = new HashMap<>();
    	token.put("refreshToken", getClient().refreshToken);
    	
    	ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, token);
    	
    	if(response.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
    		throw new TokenExpiredException(response.getErrorMessage(), requestUrl, response.getResult());
    	}
    	assertValidResponse(response, requestUrl);
    	
    	Map<String, String> tokenMap = fromJson(response.getResult(), new TypeReference<Map<String, String>>() {});
    	getClient().accessToken = tokenMap.get("accessToken");
    }

    /**
     * Logs out from Argus. Not need with a TokenBased Auth Service. 
     */
    public void logout() {
        return;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
