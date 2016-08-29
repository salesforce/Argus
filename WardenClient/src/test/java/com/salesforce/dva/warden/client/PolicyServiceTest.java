package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.warden.dto.Policy;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PolicyServiceTest extends AbstractTest {
    


    @Test
    public void testGetPolicies() throws IOException {
        try (WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))){
            AuthService authService = wardenService.getAuthService();
            authService.login("aUsername","aPassword");
            PolicyService policyService = wardenService.getPolicyService();
            List<Policy> resultPolicies = policyService.getPolicies();
            List<Policy> expectedPolicies = Arrays.asList(new Policy [] {_constructPolicy()});
            assertEquals(expectedPolicies, resultPolicies);
            authService.logout();
        }
    }

    private Policy _constructPolicy() throws JsonProcessingException {
        Policy result = new Policy();
        result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate( new Date());
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate( new Date());
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

}
