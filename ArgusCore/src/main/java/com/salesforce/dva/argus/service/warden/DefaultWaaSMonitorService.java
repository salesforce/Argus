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
	 
package com.salesforce.dva.argus.service.warden;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.inject.SLF4JTypeListener.InjectLogger;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.WaaSMonitorService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.salesforce.dva.argus.service.MQService.MQQueue.METRIC;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the WaaS monitor service.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@Singleton
public class DefaultWaaSMonitorService extends DefaultJPAService implements WaaSMonitorService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String HOSTNAME;    
    private static final int BATCH_METRICS = 50;

    static {
        HOSTNAME = SystemConfiguration.getHostname();
    }

    //~ Instance fields ******************************************************************************************************************************

    @InjectLogger
    private Logger _logger;
    @Inject
    private final MQService _mqService;
    private final TSDBService _tsdbService;
    
    private Thread _monitorThread;

  //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultWaaSMonitorService object to push metrics.
     *
     * @param  mqService         The MQ service implementation with which to queue and dequeue submitted metrics and annotations
     * @param  tsdbService       The TSDB service implementation with which to write metrics and annotations into storage.
     * @param  auditService      The audit service instance to use. Cannot be null.
     * @param  configuration     The system configuration instance to use. Cannot be null.
     */
    @Inject
    DefaultWaaSMonitorService(MQService mqService, TSDBService tsdbService, AuditService auditService, SystemConfiguration configuration) {
        super(auditService, configuration);
        _mqService = mqService;
        _tsdbService = tsdbService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void submitMetric(PrincipalUser submitter, Metric metric) {
        submitMetrics(submitter, Arrays.asList(new Metric[] { metric }));
    }

    @Override
    public void submitMetrics(PrincipalUser submitter, List<Metric> metrics) {
        requireNotDisposed();
        requireArgument(submitter != null, "Submitting user cannot be null.");
        requireArgument(metrics != null, "The list of metrics to submit cannot be null.");

        List<ArrayList<Metric>> batches = _batchMetrics(metrics);

        _mqService.enqueue(METRIC.getQueueName(), batches);
    }
    
    private List<ArrayList<Metric>> _batchMetrics(List<Metric> metrics) {
        List<ArrayList<Metric>> batches = new ArrayList<ArrayList<Metric>>();
        int count = 0;
        ArrayList<Metric> batch = new ArrayList<Metric>(BATCH_METRICS);

        for (Metric metric : metrics) {
            if (count == BATCH_METRICS) {
                count = 0;
                batches.add(batch);
                batch = new ArrayList<Metric>(BATCH_METRICS);
            }
            batch.add(metric);
            count++;
        }
        batches.add(batch);
        return batches;
    }
    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public synchronized void startPushingMetrics() {
        requireNotDisposed();
        if (_monitorThread != null && _monitorThread.isAlive()) {
            _logger.info("Request to start warden monitoring aborted as it is already running.");
        } else {
            _logger.info("Starting warden monitor thread.");
            _monitorThread = new WaaSMonitorThread("warden-monitor");
            _monitorThread.start();
            _logger.info("Warden monitor thread started.");
        }
    }

    @Override
    @Transactional
    public synchronized void stopPushingMetrics() {
        requireNotDisposed();
        if (_monitorThread != null && _monitorThread.isAlive()) {
            _logger.info("Stopping system monitoring.");
            _monitorThread.interrupt();
            _logger.info("System monitor thread interrupted.");
            try {
                _logger.info("Waiting for system monitor thread to terminate.");
                _monitorThread.join();
            } catch (InterruptedException ex) {
                _logger.warn("System monitoring was interrupted while shutting down.");
            }
            _logger.info("System monitoring stopped.");
        } else {
            _logger.info("Requested shutdown of warden monitoring aborted as it is not yet running.");
        }
    }



    @Override
    public synchronized void dispose() {
        stopPushingMetrics();
        super.dispose();
    }

    @Override
    public List<Metric> commitMetrics(int messageCount, int timeout) {
        requireNotDisposed();
        requireArgument(messageCount > 0, "Message count must be greater than zero.");
        requireArgument(timeout > 0, "The timeout in milliseconds must be greater than zero.");

        CollectionType type = new ObjectMapper().getTypeFactory().constructCollectionType(ArrayList.class, Metric.class);
        List<ArrayList<Metric>> dequeuedMessages = _mqService.dequeue(METRIC.getQueueName(), type, timeout, messageCount);
        List<Metric> dequeued = new ArrayList<Metric>();

        for (List<Metric> list : dequeuedMessages) {
            dequeued.addAll(list);
        }
        if (!dequeued.isEmpty()) {
            _tsdbService.putMetrics(dequeued);
            _logger.debug("Committed {} metrics.", dequeued.size());
        }
        return dequeued;
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * WaaS Monitoring thread.
     *
     * @author  Ruofan Zhang (rzhang@salesforce.com)
     */
    private class WaaSMonitorThread extends Thread {
    	
    	//~ Static fields/initializers *******************************************************************************************************************

        private static final int METRIC_MESSAGES_CHUNK_SIZE = 100;
    	protected static final int TIMEOUT = 500;
    	private static final long TIME_BETWEEN_PUSHINGS = 60 * 1000;
        /**
         * Creates a new SchedulingThread object.
         *
         * @param  name  The thread name.
         */
        public WaaSMonitorThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!isInterrupted()) {
                _sleepForPollPeriod();
                if (!isInterrupted()) {
                    try {
                    	
                        commitMetrics(METRIC_MESSAGES_CHUNK_SIZE, TIMEOUT);
                    } catch (Exception t) {
                        _logger.error("Error occured while pushing monitor counters for {}. Reason: {}", HOSTNAME, t.getMessage());
                    }
                }
            }
        }

        private void _sleepForPollPeriod() {
            try {
                _logger.info("Sleeping for {}s before pushing metrics.", TIME_BETWEEN_PUSHINGS / 1000);
                sleep(TIME_BETWEEN_PUSHINGS);
            } catch (InterruptedException ex) {
                _logger.warn("Warden monitoring was interrupted.");
                interrupt();
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
