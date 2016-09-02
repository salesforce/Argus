package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.warden.dto.Policy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.salesforce.dva.warden.client.WardenHttpClient.WardenResponse;
import com.salesforce.dva.warden.client.WardenHttpClient.RequestType;
import com.salesforce.dva.warden.client.WardenService.PutResult;
import com.salesforce.dva.warden.client.WardenService.EndpointService;


public class PolicyService extends AbstractService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WardenHttpClient.class);
    private static final String REQUESTURL = "/policy";

    /**
     *
     * @param client
     */
    PolicyService(WardenHttpClient client) {
        super(client);
    }

    public List<Policy> getPolicies(  ) throws IOException
    {
        String requestUrl = REQUESTURL;
            WardenResponse response = client.executeHttpRequest( RequestType.GET, requestUrl, null );
            ObjectMapper mapper = new ObjectMapper(  );
            List<Policy> policies =
                    mapper.readValue( response.getResult(),
                            mapper.getTypeFactory(  ).constructCollectionType( List.class, Policy.class ) );

            return policies;

    }

    public WardenService.PutResult createPolicies(List<Policy> policies) throws IOException{
        String requestUrl = REQUESTURL;
        WardenResponse response = client.executeHttpRequest( RequestType.POST, requestUrl, policies );

        assertValidResponse(response, requestUrl);

        Map<String, Object> map = fromJson(response.getResult(), new TypeReference<Map<String, Object>>() { });

        List<String> errorMessages = (List<String>) map.get("Error Messages");

        return new PutResult(String.valueOf(map.get("Success")), String.valueOf(map.get("Errors")), errorMessages);
    }

    public boolean deletePolicies(){
        return false;

    }

    public boolean updatePolicies(String policiesJson){
        return false;

    }

}
