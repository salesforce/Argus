package com.salesforce.dva.warden.client;

import com.salesforce.dva.warden.client.WardenWebServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.warden.client.WardenHttpClient;
import com.salesforce.dva.warden.client.WardenHttpClient.RequestType;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.Credentials;
import com.salesforce.dva.warden.dto.WardenEvent;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static com.salesforce.dva.warden.client.util.Assert.requireArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultWardenWebServiceClient implements WardenWebServiceClient
{   
	private WardenHttpClient wardenHttpClient;
	private final String endpoint;
	private static final Logger LOGGER = LoggerFactory.getLogger(WardenWebServiceClient.class.getName());
	
	DefaultWardenWebServiceClient(String endpoint, int maxConn, int timeout, int reqTimeout ){
		requireArgument((endpoint != null) && (!endpoint.isEmpty()), "Illegal endpoint URL.");
		requireArgument(maxConn >= 2, "At least two connections are required.");
		requireArgument(timeout >= 1, "Timeout must be greater than 0.");
		requireArgument(reqTimeout >= 1, "Request timeout must be greater than 0.");
		
		this.endpoint = endpoint;
		this.wardenHttpClient = new WardenHttpClient(endpoint, maxConn, timeout, reqTimeout);
	}
	
    public boolean login(String username, String password) {
		String requestUrl = endpoint + "/auth/login";
		Credentials creds = new Credentials();
		creds.setPassword(password);
		creds.setUsername(username);
		HttpResponse response = null;
		try {
			StringEntity entity = new StringEntity(WardenHttpClient.toJson(creds));
			response = wardenHttpClient.executeHttpRequest(RequestType.POST, requestUrl, entity);
			EntityUtils.consume(response.getEntity());
		} catch (IOException ex) {
			LOGGER.warn("IOException while trying to log in to Warden.", ex);
			return false;
		}
		if (response.getStatusLine().getStatusCode() != 200) {
			String message = response.getStatusLine().getReasonPhrase();
			LOGGER.warn(message);
			return false;
		}
		LOGGER.info("Logged in as " + username);
		return true;
	}

	public boolean logout() {
		String requestUrl = endpoint + "/auth/logout";
		HttpResponse response = null;
		try {
			response = wardenHttpClient.executeHttpRequest(RequestType.GET, requestUrl, null);
			wardenHttpClient.clearCookies();
			EntityUtils.consume(response.getEntity());
		} catch (IOException ex) {
			LOGGER.warn("IOException while trying to logout of Warden.", ex);
			return false;
		}
		if (response.getStatusLine().getStatusCode() != 200) {
			String message = response.getStatusLine().getReasonPhrase();
			LOGGER.warn(message);
			return false;
		}
		LOGGER.info("Logout succeeded");
		return true;
	}
	
	@Override
	public List<Policy> GetPolicies() throws IOException{
		login("raj", "abc");
		logout();
		String endpoint = "argusuri";
		String requestUrl = endpoint + "/policy";
		HttpResponse response = null;
		response = wardenHttpClient.executeHttpRequest(RequestType.GET, requestUrl, null);
		EntityUtils.consume(response.getEntity());
		ObjectMapper mapper = new ObjectMapper();
		List<Policy> policies = mapper.readValue(extractStringResponse(response), mapper.getTypeFactory().constructCollectionType(List.class, Policy.class));		
		//List<Policy> policies = mapper.readValue(extractStringResponse(response), new TypeReference<List<Policy>>(){}););
		return policies;
	}
	
	/*
     * Helper method to extract the HTTP response and close the client connection. Please note that <tt>ByteArrayOutputStreams</tt> do not require to
     * be closed.
     */
    private String extractStringResponse(HttpResponse content) {
        requireArgument(content != null, "Response content is null.");

        String result;
        HttpEntity entity = null;

        try {
            entity = content.getEntity();
            if (entity == null) {
                result = "";
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                entity.writeTo(baos);
                result = baos.toString("UTF-8");
            }
            return result;
        } catch (IOException ex) {
            throw new SystemException(ex);
        } finally {
            if (entity != null) {
                try {
                    EntityUtils.consume(entity);
                } catch (IOException ex) {
                    _logger.warn("Failed to close entity stream.", ex);
                }
            }
        }
    }
	
	void dispose() {
		try {
			wardenHttpClient.dispose();
		} finally {
			wardenHttpClient = null;
		}
	}
}
