package com.salesforce.dva.warden.client;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.salesforce.dva.warden.dto.Policy;

public interface WebServiceClient {

	List<Policy> getPolicies();

	boolean updatePolicies(String policiesJson);

	boolean deletePolicies();

	List<Policy> createPolicies(List<Policy> policies);

}
