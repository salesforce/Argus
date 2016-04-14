package com.salesforce.dva.warden.client;

import java.util.List;

import com.salesforce.dva.warden.WardenClient;
import com.salesforce.dva.warden.dto.Policy;

public abstract class DefaultWardenClient implements WardenClient {
	
	// This is how the client talks to the server.
	private WardenClientService clientService;
	public DefaultWardenClient()
	{
		clientService = new DefaultWardenClientService();
	}
	@Override
	public void register(List <Policy> policies, int port) {
		clientService.register(policies, port);
	}

	@Override
	public void unregister() {
       clientService.unregister();		
	}

	@Override
	public void updateMetric(Policy policy, String user, double value) {
		clientService.updateMetric(policy, user, value);
	}

	@Override
	public void modifyMetric(Policy policy, String user, double delta) {
		clientService.modifyMetric(policy, user, delta);
	}
		

}
