package com.salesforce.dva.argus.service;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Properties;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.AlertIdWithTimestamp;

public class SchedulingServiceIT extends AbstractTest {

    @Test
    public void testAlertSchedulingWithDistributedDatabase() throws InterruptedException {
    	
    	Properties props = new Properties();
    	props.put("service.binding.scheduling", "com.salesforce.dva.argus.service.schedule.DistributedDatabaseSchedulingService");
    	system = getInstance(props);
        SchedulingService schedulingService = system.getServiceFactory().getSchedulingService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        MQService mqService = system.getServiceFactory().getMQService();
        UserService userService = system.getServiceFactory().getUserService();

        schedulingService.enableScheduling();

        long schedulingIterations = 1;
        int noOfAlerts = random.nextInt(10) + 1;
        PrincipalUser user = userService.findAdminUser();
        Alert alert;

        for (int i = 0; i < noOfAlerts; i++) {
            String expression = "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, " +
                "-1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

            alert = new Alert(user, user, createRandomName(), expression, "* * * * *");
            alert.setEnabled(true);
            alertService.updateAlert(alert);
        }
        schedulingService.startAlertScheduling();
        Thread.sleep((1000L * 60L * schedulingIterations));
        schedulingService.stopAlertScheduling();

        List<AlertIdWithTimestamp> list = mqService.dequeue(ALERT.getQueueName(), AlertIdWithTimestamp.class, 1000,
            (int) (noOfAlerts * schedulingIterations));

        assertEquals(schedulingIterations * noOfAlerts, list.size());
    }
}
