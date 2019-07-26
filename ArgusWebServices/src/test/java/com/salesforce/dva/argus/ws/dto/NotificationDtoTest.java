package com.salesforce.dva.argus.ws.dto;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.alert.notifier.EmailNotifier;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NotificationDtoTest {

    @Test
    public void transformToDto_test() throws Exception {
        // set up test data
        PrincipalUser pu = new PrincipalUser(null, "username", "email");
        final String alertName = "TEST ALERT";
        final String expression = "EXP";
        final String cronEntry = "TEST CRON";
        Alert testAlert = new Alert(pu, pu, alertName, expression, cronEntry);
        final String notificationName = "TEST NAME";
        final String notifierName = EmailNotifier.class.getName();
        List<String> subscriptions = new LinkedList<>();
        subscriptions.add("test@salesforce.com");
        final long cooldown = 100;
        final BigInteger nid = new BigInteger("123456");
        final BigInteger aid = new BigInteger("98765");
        Notification testNotification = new Notification(notificationName, testAlert, notifierName, subscriptions, cooldown);
        Class<JPAEntity> clazz = JPAEntity.class;
        Field idField = clazz.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(testNotification, nid);
        idField.set(testAlert, aid);

        final String subject = "TEST SUBJECT";
        testNotification.setEmailSubject(subject);
        final boolean enableClearNotification = false;
        testNotification.setEnableClearNotification(enableClearNotification);

        // test
        NotificationDto dto = NotificationDto.transformToDto(testNotification);

        // verify
        assertEquals(nid, dto.getId());
        assertEquals(aid, dto.getAlertId());
        assertEquals(notificationName, dto.getName());
        assertEquals(notifierName, dto.getNotifierName());
        assertEquals(subscriptions, dto.getSubscriptions());
        assertEquals(cooldown, dto.getCooldownPeriod());
        assertEquals(subject, dto.getEmailSubject());
        assertEquals(enableClearNotification, dto.isEnableClearNotification());
    }
}
