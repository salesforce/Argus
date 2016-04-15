package com.salesforce.dva.warden.client;

import java.util.List;

import com.salesforce.dva.warden.client.WardenClientService;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.WardenEvent;

class DefaultWardenClientService implements WardenClientService {
	
	@Override
	public void register(List<Policy> policies, int port) {
		//infraction history for each user identified in the policy
		//*****The classes you may want these classes*******
		//cache 3rd party in-memory cache. local cache. users and suspentions. for database objects
		//argus, orchestra project has a http client & ArgusService.java is DefaultWardenClientService.java
		//sheduler - push usage data back to the server
		//metric registry - big map. for storing metric and its values in cache until ready to be pushed to the server.
		//Look @ arguswebservices DTOs <--> JPA Entitties in ArgusCore. 
		
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
	public void updateMetric(Policy policy, String username, double value){
		
	}
	
	@Override
	public void modifyMetric(Policy policy, String username, double delta){
	
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
