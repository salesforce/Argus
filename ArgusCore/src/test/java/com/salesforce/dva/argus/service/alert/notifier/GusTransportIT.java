package com.salesforce.dva.argus.service.alert.notifier;

import org.apache.zookeeper.Op;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class GusTransportIT {
    private String AUTH_ENDPOINT = "https://gus.my.salesforce.com/services/oauth2/token";
    private String AUTH_CLIENT_ID = "{INSERT VALUE}";
    private String AUTH_CLIENT_SECRET = "{INSERT VALUE}";
    private String AUTH_USERNAME = "{INSERT VALUE}";
    private String AUTH_PASSWORD = "{INSERT VALUE}";
    private GusTransport.EndpointInfo DEFAULT_EP = new GusTransport.EndpointInfo("default EP", "NO_TOKEN");
    private GusTransport gusTransport;

    @Before
    public void setUp() {
        gusTransport = new GusTransport(Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                DEFAULT_EP,
                1,
                1);
    }

    @Test
    public void getEndpointInfo_test() throws Exception {
        GusTransport.EndpointInfo ei = gusTransport.getEndpointInfo();
        assertNotSame(DEFAULT_EP, ei);
        assertEquals("https://gus.my.salesforce.com", ei.getEndPoint());
        assertTrue(ei.getToken().length() > 0);
    }
}
