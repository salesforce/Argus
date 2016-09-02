package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.warden.dto.Policy;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

import com.salesforce.dva.warden.client.WardenService.PutResult;

public class PolicyServiceTest extends AbstractTest {
    


    @Test
    public void testGetPolicies() throws IOException {
        try (WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))){
            PolicyService policyService = wardenService.getPolicyService();
            List<Policy> resultPolicies = policyService.getPolicies();
            List<Policy> expectedPolicies = Arrays.asList(new Policy [] {_constructPolicy()});
            assertEquals(expectedPolicies, resultPolicies);
        }
    }

    @Test
    public void testCreatePolicies () throws IOException{
        try (WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))){
            PolicyService policyService = wardenService.getPolicyService();
            List<Policy> policies = Arrays.asList(new Policy [] {_constructPolicy()});
            PutResult result = policyService.createPolicies(policies);
            assertEquals(_constructSuccessfulResult(policies, 0), result);
        }
    }

    private Policy _constructPolicy() throws JsonProcessingException {
        Policy result = new Policy();
        result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate( new Date(1472847819167L));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate( new Date(1472847819167L ));
        result.setService("TestService");
        result.setName("TestName");
        result.setOwners(Arrays.asList("TestOwner"));
        result.setUsers(Arrays.asList("TestUser"));
        result.setSubSystem("TestSubSystem");
        result.setMetricName("TestMetricName");
        result.setTriggerType(Policy.TriggerType.BETWEEN);
        result.setAggregator(Policy.Aggregator.AVG);
        result.setThresholds(Arrays.asList(0.0));
        result.setTimeUnit("5min");
        result.setDefaultValue(0.0);
        result.setCronEntry("0 */4 * * *");

        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writeValueAsString(result));

        return result;
    }

    private PutResult _constructSuccessfulResult(List<Policy> annotations, int errorCount) {
        String failCount = Integer.toString(errorCount);
        String successCount = Integer.toString(annotations.size() - errorCount);
        List<String> errorMessages = new LinkedList<>();

        if (errorCount > 0) {
            errorMessages.add(failCount);
        }
        return new PutResult(successCount, failCount, errorMessages);
    }

}
