/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
     
package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.AlertIdWithTimestamp;

import org.junit.Test;
import java.util.List;
import java.util.Properties;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static org.junit.Assert.*;

public class SchedulingServiceTest extends AbstractTest {

    @Test
    public void testAlertSchedulingWithGlobalInterlock() throws InterruptedException {
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
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
