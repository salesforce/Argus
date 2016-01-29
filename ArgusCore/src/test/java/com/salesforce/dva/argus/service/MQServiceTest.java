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
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MQServiceTest extends AbstractTest {

    @Test
    public void testServiceIsSingleton() {
        MQService service1 = system.getServiceFactory().getMQService();
        MQService service2 = system.getServiceFactory().getMQService();

        assertTrue(service1 == service2);
    }

    @Test
    public void testPutAndBulkGetMessages() throws InterruptedException {
        MQService service = system.getServiceFactory().getMQService();
        String queueName = createRandomName();
        int count = 1000;

        try {
            for (int i = 0; i < count; i++) {
                service.enqueue(queueName, "Message " + i);
            }

            List<String> msg = service.dequeue(queueName, 10000, count);

            assertEquals(count, msg.size());
        } finally {
            service.dispose();
        }
    }

    @Test(timeout = 100000L)
    public void testBulkPutAndGetMessages() throws InterruptedException {
        int count = 1000;
        String queueName = createRandomName();
        MQService service = system.getServiceFactory().getMQService();
        List<String> messages = new ArrayList<String>(count);

        for (int i = 0; i < count; i++) {
            messages.add("Message " + i);
        }
        try {
            service.enqueue(queueName, messages);
            while (count > 0) {
                List<String> dequeuedMessages = service.dequeue(queueName, count * 10, 100);

                if (dequeuedMessages != null) {
                    count -= dequeuedMessages.size();
                }
            }
        } finally {
            service.dispose();
        }
    }

    @Test
    public void testPutAndBulkGetMessagesMultiThredaing() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final MQService mqService = system.getServiceFactory().getMQService();
        int count = 20;
        final String queueName = createRandomName();

        class MessageThread extends Thread {

            String message;

            public MessageThread(String message) {
                this.message = message;
            }

            @Override
            public void run() {
                try {
                    latch.await();
                    mqService.enqueue(queueName, message);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        try {
            for (int i = 0; i < count; i++) {
                new MessageThread("message_" + i).start();
            }
            latch.countDown();

            List<String> msg = mqService.dequeue(queueName, 10000, count);

            assertEquals(count, msg.size());
        } finally {
            mqService.dispose();
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
