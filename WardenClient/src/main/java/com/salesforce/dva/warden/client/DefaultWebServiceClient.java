package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.warden.client.HttpClient;
import com.salesforce.dva.warden.client.HttpClient.RequestType;
import com.salesforce.dva.warden.client.WebServiceClient;
import static com.salesforce.dva.warden.client.util.Assert.requireArgument;
import com.salesforce.dva.warden.dto.Credentials;
import com.salesforce.dva.warden.dto.Policy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

class DefaultWebServiceClient
    implements WebServiceClient
{
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
                             throws IOException
    {
        login( "raj", "abc" );
        String endpoint = "argusuri";
        String requestUrl = endpoint + "/policy";
        HttpResponse response = null;
        response = httpClient.executeHttpRequest( RequestType.GET, requestUrl, null );
        EntityUtils.consume( response.getEntity(  ) );

        ObjectMapper mapper = new ObjectMapper(  );
        List<Policy> policies =
            mapper.readValue( extractStringResponse( response ),
                              mapper.getTypeFactory(  ).constructCollectionType( List.class, Policy.class ) );
        //List<Policy> policies = mapper.readValue(extractStringResponse(response), new TypeReference<List<Policy>>(){}););
        logout(  );

        return policies;
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
