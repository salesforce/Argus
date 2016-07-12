package com.salesforce.dva.warden.client;

import java.io.IOException;
import java.util.List;

import com.salesforce.dva.warden.dto.Policy;

public interface WebServiceClient {

	List<Policy> getPolicies() throws IOException;

}
