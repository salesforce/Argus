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
     
package com.salesforce.dva.argus.client;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class MainTest extends AbstractTest {

    @Test(timeout = 20000L)
    public void testMetricCommitClientInterrupt() throws InterruptedException {
        final Main main = new Main(getTestConfig(10000));
        Thread invoker = new Thread(new Runnable() {

                @Override
                public void run() {
                    main.invoke(ClientType.COMMIT_METRICS);
                }
            }, "metriccommitclient");

        invoker.setPriority(Thread.MIN_PRIORITY);
        invoker.start();
        Thread.sleep(5000);
        while (invoker.isAlive()) {
            invoker.join(500);
            invoker.interrupt();
        }
    }

    @Test(timeout = 20000L)
    public void testAnnotationCommitClientInterrupt() throws InterruptedException {
        final Main main = new Main(getTestConfig(10000));
        Thread invoker = new Thread(new Runnable() {

                @Override
                public void run() {
                    main.invoke(ClientType.COMMIT_ANNOTATIONS);
                }
            }, "annotationcommitclient");

        invoker.setPriority(Thread.MIN_PRIORITY);
        invoker.start();
        Thread.sleep(5000);
        while (invoker.isAlive()) {
            invoker.join(500);
            invoker.interrupt();
        }
    }

    @Test(timeout = 20000L)
    public void testAlertClientInterrupt() throws InterruptedException {
        final Main main = new Main(getTestConfig(10000));
        Thread invoker = new Thread(new Runnable() {

                @Override
                public void run() {
                    main.invoke(ClientType.ALERT);
                }
            }, "alertclient");

        invoker.setPriority(Thread.MIN_PRIORITY);
        invoker.start();
        Thread.sleep(5000);
        while (invoker.isAlive()) {
            invoker.join(500);
            invoker.interrupt();
        }
    }

    @Test(timeout = 20000L)
    public void testSchemaCommitClientInterrupt() throws InterruptedException {
        final Main main = new Main(getTestConfig(10000));
        Thread invoker = new Thread(new Runnable() {

                @Override
                public void run() {
                    main.invoke(ClientType.COMMIT_SCHEMA);
                }
            }, "schemacommitclient");

        invoker.setPriority(Thread.MIN_PRIORITY);
        invoker.start();
        Thread.sleep(5000);
        while (invoker.isAlive()) {
            invoker.join(500);
            invoker.interrupt();
        }
    }

    public Properties getTestConfig(int clientTimeout) {
        String configFileLocation = System.getProperty("argus.config.testing.location");
        Properties config = new Properties();

        if (configFileLocation != null) {
            InputStream is = null;

            try {
                is = new FileInputStream(configFileLocation);
                config.load(is);
            } catch (IOException ex) {
                throw new SystemException(ex);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        assert false : "This should never occur.";
                    }
                }
            }
        } else {
            config.put("service.property.tsdb.endpoint.read", "http://localhost:4466");
            config.put("service.property.tsdb.endpoint.write", "http://localhost:4466");
            config.put("service.property.tsdb.endpoint.timeout", String.valueOf(clientTimeout));
            config.put("system.property.log.level", "error");
        }
        return config;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
