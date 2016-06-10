package com.salesforce.dva.warden.client;

import com.salesforce.dva.warden.WardenClient;
import com.salesforce.dva.warden.dto.Policy;

import java.util.List;

public abstract class DefaultWardenClient
    implements WardenClient
{
    // This is how the client talks to the server.
    private WardenWebServiceClient wardenWebServiceClient;

    public DefaultWardenClient(  )
    {
        clientService = new WardenWebServiceClient(  );
    }

    @Override
    public void register( List<Policy> policies, int port )
    {
        clientService.register( policies, port );
    }

    @Override
    public void unregister(  )
    {
        clientService.unregister(  );
    }

    @Override
    public void updateMetric( Policy policy, String user, double value )
    {
        clientService.updateMetric( policy, user, value );
    }

    @Override
    public void modifyMetric( Policy policy, String user, double delta )
    {
        clientService.modifyMetric( policy, user, delta );
    }
    
    
}
