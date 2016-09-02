package com.salesforce.dva.warden.client;

import com.salesforce.dva.warden.dto.Credentials;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.salesforce.dva.warden.client.WardenHttpClient.WardenResponse;
import com.salesforce.dva.warden.client.WardenService.EndpointService;

public class AuthService extends EndpointService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardenHttpClient.class);

    /**
     *
     * @param client
     */
    AuthService(WardenHttpClient client) {
        super(client);
    }
    /**
     * Logs into the web services.
     *
     * @param username The username.
     * @param password The password.
     * @throws java.io.IOException
     */
    public void login(String username, String password) throws IOException {
        String requestUrl = "/auth/login";
        Credentials creds = new Credentials();
        creds.setPassword(password);
        creds.setUsername(username);
        WardenResponse response = getClient().executeHttpRequest(WardenHttpClient.RequestType.POST, requestUrl, creds);
        assertValidResponse(response, requestUrl);
    }

    public void logout() throws IOException {
        String requestUrl = "/auth/logout";
        WardenResponse response = getClient().executeHttpRequest(WardenHttpClient.RequestType.GET, requestUrl, null);
        assertValidResponse(response, requestUrl);
    }

}
