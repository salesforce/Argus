package com.salesforce.dva.warden.client;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class AuthServiceTest extends AbstractTest {
    


    @Test
    public void testLoginLogout() throws IOException {
        try (WardenService wardenService = new WardenService(getMockedClient("/AuthServiceTest.testLoginLogout.json"))){
            AuthService authService = wardenService.getAuthService();
            authService.login("aUsername", "aPassword");
            authService.logout();
        }
    }
    
    @Test
    public void testBadLogin() throws IOException {
        try (WardenService wardenService = new WardenService(getMockedClient("/AuthServiceTest.testLoginLogout.json"))){
            AuthService authService = wardenService.getAuthService();
            authService.login("aBadUsername", "aBadPassword");
        } catch (WardenException ex) {
            assertEquals(403,ex.getStatus());
            return;
        }
        fail("Expected an WardenServiceException for bad login.");
    }
    
}
