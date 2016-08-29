package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.warden.dto.Credentials;
import com.salesforce.dva.warden.dto.Policy;
import org.apache.http.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import com.salesforce.dva.warden.client.WardenHttpClient.WardenResponse;

public class PolicyService extends AbstractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardenHttpClient.class);

    /**
     *
     * @param client
     */
    PolicyService(WardenHttpClient client) {
        super(client);
    }

    public List<Policy> getPolicies(  )
    {
        String requestUrl = "";
        try {
            WardenResponse response = client.executeHttpRequest( WardenHttpClient.RequestType.GET, requestUrl, null );
            //EntityUtils.consume( response.getEntity(  ) );
            ObjectMapper mapper = new ObjectMapper(  );
            List<Policy> policies =
                    mapper.readValue( response.getResult(),
                            mapper.getTypeFactory(  ).constructCollectionType( List.class, Policy.class ) );

            return policies;
        } catch (IOException ex){
            throw new SystemException("Error posting data", ex);
        }

    }
/*
    public List<Policy> createPolicies(List<Policy> policies){
        String endpoint = "argusuri";
        String requestUrl = endpoint + "/policy";

        try {
            StringEntity entity = new StringEntity(fromEntity(policies));
            WardenHttpClient.WardenResponse response = httpClient.executeHttpRequest( WardenHttpClient.RequestType.POST, requestUrl, entity );
            ObjectMapper mapper = new ObjectMapper(  );
            return mapper.readValue( response.getResult(),
                    mapper.getTypeFactory(  ).constructCollectionType( List.class, Policy.class ) );

        } catch (IOException ex) {
            throw new SystemException("Error posting data", ex);
        }

    }*/

    public boolean deletePolicies(){
        return false;

    }

    public boolean updatePolicies(String policiesJson){
        return false;

    }

}
