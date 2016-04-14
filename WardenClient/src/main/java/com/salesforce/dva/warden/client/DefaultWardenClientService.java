package com.salesforce.dva.warden.client;

import java.util.List;

import com.salesforce.dva.warden.client.WardenClientService;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.WardenEvent;

abstract class DefaultWardenClientService implements WardenClientService {
	
	@Override
	public void register(List<Policy> policies, int port) {
		//infraction history for each user identified in the policy
		//cache 3rd party in-memory cache. local cache. 
		//_registerAsServerListener();
		//_populateCache(policies);
		//_reconcilePolicies(policies);
		//_scheduleDataUpdates();
	}

	@Override
	public void unregister() {
		// TODO Auto-generated method stub
		
	}
	
	@Override
	public void pushMetricData() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void populateClientData() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void pushPolicy(Policy policy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onEvent(WardenEvent event) {

	}

}
