package com.salesforce.dva.warden.client;

import java.util.List;

import com.salesforce.dva.warden.WardenClient;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.WardenEvent;


public interface WardenClientService extends WardenClient {
	
	void pushMetricData();
	void populateClientData();
	void pushPolicy(Policy policy);
	void onEvent(WardenEvent event);

}
