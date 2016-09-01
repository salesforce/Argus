package com.salesforce.dva.warden.client;

import com.salesforce.dva.warden.dto.Credentials;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.salesforce.dva.warden.client.WardenHttpClient.WardenResponse;

public class AuthService extends AbstractService {

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
        WardenResponse response = client.executeHttpRequest(WardenHttpClient.RequestType.POST, requestUrl, creds);
        if (response.getErrorMessage()!= null) {
            throw new WardenException(response.getStatus(), response.getErrorMessage(), requestUrl, response.getResult());
        }
    }

    public void logout() throws IOException {
        String requestUrl = "/auth/logout";
        WardenResponse response = client.executeHttpRequest(WardenHttpClient.RequestType.GET, requestUrl, null);
        if (response.getErrorMessage()!= null) {
            throw new WardenException(response.getStatus(), response.getErrorMessage(), requestUrl, response.getResult());
        }
    }

}
