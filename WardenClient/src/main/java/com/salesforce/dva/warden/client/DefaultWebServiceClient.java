package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.warden.client.HttpClient;
import com.salesforce.dva.warden.client.HttpClient.RequestType;
import com.salesforce.dva.warden.client.WebServiceClient;
import static com.salesforce.dva.warden.client.util.Assert.requireArgument;
import com.salesforce.dva.warden.dto.Credentials;
import com.salesforce.dva.warden.dto.Policy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

class DefaultWebServiceClient
    implements WebServiceClient
{

    private final ObjectMapper _mapper;
	private HttpClient httpClient;
    private final String endpoint;
    private static final Logger LOGGER = LoggerFactory.getLogger( WebServiceClient.class.getName(  ) );
    
    DefaultWebServiceClient(HttpClient httpClient)
    {
        requireArgument( httpClient != null , "Illegal endpoint URL." );
        requireArgument( ( httpClient.endpoint != null ) && ( ! httpClient.endpoint.isEmpty(  ) ), "Illegal endpoint URL." );
        requireArgument( httpClient.maxConn >= 2, "At least two connections are required." );
        requireArgument( httpClient.connTimeout >= 1, "Timeout must be greater than 0." );
        requireArgument( httpClient.connRequestTimeout >= 1, "Request timeout must be greater than 0." );
        this.endpoint = httpClient.endpoint;
        this.httpClient = httpClient;
        this._mapper = new ObjectMapper();
    }

    DefaultWebServiceClient( String endpoint, int maxConn, int timeout, int reqTimeout )
    {
        this(new HttpClient(endpoint, maxConn, timeout, reqTimeout));
    }

    public boolean login( String username, String password )
    {
        String requestUrl = endpoint + "/auth/login";
        Credentials creds = new Credentials(  );
        creds.setPassword( password );
        creds.setUsername( username );

        HttpResponse response = null;

        try
        {
            StringEntity entity = new StringEntity( HttpClient.toJson( creds ) );
            response = httpClient.executeHttpRequest( RequestType.POST, requestUrl, entity );
            EntityUtils.consume( response.getEntity(  ) );
        } catch ( IOException ex )
        {
            LOGGER.warn( "IOException while trying to log in to Warden.", ex );

            return false;
        }

        if ( response.getStatusLine(  ).getStatusCode(  ) != 200 )
        {
            String message = response.getStatusLine(  ).getReasonPhrase(  );
            LOGGER.warn( message );

            return false;
        }

        LOGGER.info( "Logged in as " + username );

        return true;
    }

    public boolean logout(  )
    {
        String requestUrl = endpoint + "/auth/logout";
        HttpResponse response = null;

        try
        {
            response = httpClient.executeHttpRequest( RequestType.GET, requestUrl, null );
            httpClient.clearCookies(  );
            EntityUtils.consume( response.getEntity(  ) );
        } catch ( IOException ex )
        {
            LOGGER.warn( "IOException while trying to logout of Warden.", ex );

            return false;
        }

        if ( response.getStatusLine(  ).getStatusCode(  ) != 200 )
        {
            String message = response.getStatusLine(  ).getReasonPhrase(  );
            LOGGER.warn( message );

            return false;
        }

        LOGGER.info( "Logout succeeded" );

        return true;
    }

    @Override
    public List<Policy> getPolicies(  )
    {
        login( "raj", "abc" );
        String endpoint = "argusuri";
        String requestUrl = endpoint + "/policy";
        HttpResponse response = null;
        try {
        	response = httpClient.executeHttpRequest( RequestType.GET, requestUrl, null );
	        EntityUtils.consume( response.getEntity(  ) );
	        ObjectMapper mapper = new ObjectMapper(  );
	        List<Policy> policies =
	            mapper.readValue( extractStringResponse( response ),
	                              mapper.getTypeFactory(  ).constructCollectionType( List.class, Policy.class ) );
	        logout(  );

	        return policies;
        } catch (IOException ex){	
        	throw new SystemException("Error posting data", ex);
        }

    }

    @Override
    public String createPolicies(List<Policy> policies){
    	login( "raj", "abc" );
        String endpoint = "argusuri";
        String requestUrl = endpoint + "/policy";
        
        try {
	        StringEntity entity = new StringEntity(fromEntity(policies));
	        HttpResponse response = httpClient.executeHttpRequest( RequestType.POST, requestUrl, entity );
	        return extractResponse(response);
        } catch (IOException ex) {
            throw new SystemException("Error posting data", ex);
        }
    	
    }
    
    @Override
    public boolean deletePolicies(){
		return false;
    	
    }
    
    @Override
    public boolean updatePolicies(String policiesJson){
		return false;
    
    }
    
    /* Helper method to convert JSON String representation to the corresponding Java entity. */
    private <T> T toEntity(String content, TypeReference<T> type) {
        try {
            return _mapper.readValue(content, type);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    /* Helper method to convert a Java entity to a JSON string. */
    @VisibleForTesting
    <T> String fromEntity(T type) {
        try {
            return _mapper.writeValueAsString(type);
        } catch (JsonProcessingException ex) {
            throw new SystemException(ex);
        }
    }
    
    /* Helper to process the response. */
    private String extractResponse(HttpResponse response) {
        if (response != null) {
            int status = response.getStatusLine().getStatusCode();

            if ((status < HttpStatus.SC_OK) || (status >= HttpStatus.SC_MULTIPLE_CHOICES)) {
                Map<String, Map<String, String>> errorMap = toEntity(extractStringResponse(response),
                    new TypeReference<Map<String, Map<String, String>>>() { });
                if (errorMap != null) {
                    throw new SystemException("Error : " + errorMap.toString());
                } else {
                    throw new SystemException("Status code: " + status + " .  Unknown error occured. ");
                }
            } else {
                return extractStringResponse(response);
            }
        }
        return null;
    }
    
    /*
    * Helper method to extract the HTTP response and close the client connection. Please note that <tt>ByteArrayOutputStreams</tt> do not require to
    * be closed.
    */
    private String extractStringResponse( HttpResponse content )
    {
        requireArgument( content != null, "Response content is null." );

        String result;
        HttpEntity entity = null;

        try
        {
            entity = content.getEntity(  );

            if ( entity == null )
            {
                result = "";
            } else
            {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(  );

                entity.writeTo( baos );
                result = baos.toString( "UTF-8" );
            }

            return result;
        } catch ( IOException ex )
        {
            throw new SystemException( ex );
        } finally
        {
            if ( entity != null )
            {
                try
                {
                    EntityUtils.consume( entity );
                } catch ( IOException ex )
                {
                    LOGGER.warn( "Failed to close entity stream.", ex );
                }
            }
        }
    }

    void dispose(  )
    {
        try
        {
            httpClient.dispose(  );
        } finally
        {
            httpClient = null;
        }
    }
}
