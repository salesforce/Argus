package com.salesforce.dva.argus.client;

import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.service.BatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by cguan on 6/6/16.
 */
public class MetricProcessor implements Runnable {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final long POLL_INTERVAL_MS = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricProcessor.class);

    //~ Instance fields ******************************************************************************************************************************

    private final BatchService batchService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new MetricProcessor object.
     *
     * @param  batchService     The batch service to use. Cannot be null.
     */
    MetricProcessor(BatchService batchService) {
        this.batchService = batchService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AsyncBatchedMetricQuery query = batchService.executeNextQuery(5000);
                if (query != null) {
                    LOGGER.info("Finished processing " + query.getExpression() + " of batch " + query.getBatchId());
                }
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                LOGGER.info("Execution was interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                LOGGER.warn("Exception in MetricProcessor: {}", ex.toString());
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
