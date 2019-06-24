package com.salesforce.dva.argus.service.alert;

import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class NotificationsCacheTest {
    @Mock
    private NotificationsCacheRefresherThread thread;
    private NotificationsCache cache;

    @Before
    public void setUp() {
        cache = new NotificationsCache(thread);
    }

    @Test
    public void initCacheAndStartRefresherThread_test() {
        verify(thread).runOnce();
        verify(thread).start();
    }

    @Test
    public void setNotificationCooldownExpirationMap_test() {
        Map<BigInteger, Map<String, Long>> notificationCooldownExpirationMap = ImmutableMap.of(BigInteger.TEN,
                ImmutableMap.of("TEST", Long.MIN_VALUE));
        cache.setNotificationCooldownExpirationMap(notificationCooldownExpirationMap);
        assertSame(notificationCooldownExpirationMap, cache.getNotificationCooldownExpirationMap());
    }

    @Test
    public void setNotificationActiveStatusMap_test() {
        Map<BigInteger, Map<String, Boolean>> notificationActiveStatusMap = ImmutableMap.of(BigInteger.TEN,
                ImmutableMap.of("TEST", Boolean.TRUE));
        cache.setNotificationActiveStatusMap(notificationActiveStatusMap);
        assertSame(notificationActiveStatusMap, cache.getNotificationActiveStatusMap());
    }

    @Test
    public void setNotificationsCacheRefreshed_test() {
        boolean refreshed = false;
        cache.setNotificationsCacheRefreshed(refreshed);
        assertEquals(refreshed, cache.isNotificationsCacheRefreshed());
        refreshed = true;
        cache.setNotificationsCacheRefreshed(refreshed);
        assertEquals(refreshed, cache.isNotificationsCacheRefreshed());
    }
}
