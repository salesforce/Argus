/**
 *
 * Copyright 2013, salesforce.com
 * All rights reserved
 * Company confidential
 */
package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.perfeng.akc.AKCConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.TimeUnit;

/**
 *	@author Colby Guan (colbert.guan@salesforce.com)
 *	@author Bhinav Sura (bhinav.sura@salesforce.com)
 *
 *	The site manager creates a consumer connector for each topic from which
 *      data must be consumed which is then responsible for creating streams
 * 	for that particular topic.
 *
 */
public class OneConsumerPerThreadSiteManager implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(OneConsumerPerThreadSiteManager.class);

    /** The default number of connections to Argus.
     *   These will be used if the corresponding property is not set or set incorrectly.
     **/
    private int numStreams = 1;
    private final Properties config;
    private final ExecutorService streamExecutor;
    private final TSDBService tsdbService;

    SystemMain system;
    private List<AmurKafkaRunner> amurRunners;

    public OneConsumerPerThreadSiteManager(SystemMain system,
                                           Properties props,
                                           ExecutorService execService,
                                           List<AmurKafkaRunner> amurRunners,
                                           TSDBService tsdbService) {
        this.system = system;
        this.config = props;
        this.streamExecutor = execService;
        this.amurRunners = amurRunners;
        this.tsdbService = tsdbService;

        try {
            this.numStreams = Integer.parseInt(props.getProperty(AKCConfiguration.Parameter.NUM_STREAMS.getKeyName()));
        } catch(NumberFormatException nfe) {
            LOGGER.warn("num.streams was defined errorneously, will default to a single stream", nfe);
        }
    }

    @Override
    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread("shutdown-hook-thread") {
                @Override
                public void run() {
                    Thread.currentThread().setPriority(MAX_PRIORITY);
                    stopAmur();
                }
            });
        kickoffAmur();
    }

    protected void kickoffAmur() {
        //Start threads that will consume from Ajna
        LOGGER.info("Creating {} streams", numStreams);
        for (int i=0; i < numStreams; i++) {
            LOGGER.info("OneConsumerPerThreadSiteManager: Starting amur thread: " + i);
            this.streamExecutor.submit(amurRunners.get(i));
        }
    }

    protected void stopAmur() {
        LOGGER.info("Shutting down the stream executor for site: " + config.getProperty("bootstrap.servers"));
        if (this.streamExecutor != null) {
            for (AmurKafkaRunner akr: amurRunners) {
                akr.shutdown();
            }
            this.streamExecutor.shutdown();
            try {
                this.streamExecutor.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                LOGGER.warn("Interrupted while awaiting termination");
            }
            InstrumentationService.getInstance(tsdbService).dispose();
        }

        LOGGER.info("COMPLETED shutdown of argus kafka consumer for site: " + config.getProperty("bootstrap.servers"));
    }
}
