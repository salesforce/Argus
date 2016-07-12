package com.salesforce.dva.warden.client;

import com.salesforce.dva.warden.WardenClient;
import com.salesforce.dva.warden.dto.Policy;

import java.util.List;

public class DefaultWardenClient
    implements WardenClient
{
    // This is how the client talks to the server.
    private WebServiceClient wardenWebServiceClient;

    public DefaultWardenClient(  )
    {
    }

    @Override
    public void register( List<Policy> policies, int port )
    {
    }

    @Override
    public void unregister(  )
    {
    }

    @Override
    public void updateMetric( Policy policy, String user, double value )
    {
    }

    @Override
    public void modifyMetric( Policy policy, String user, double delta )
    {
    }
}
