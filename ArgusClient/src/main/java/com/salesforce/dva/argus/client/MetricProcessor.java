package com.salesforce.dva.argus.client;

import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.service.MetricQueueService;
import com.salesforce.dva.argus.service.MetricService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by cguan on 6/6/16.
 */
public class MetricProcessor implements Runnable {
    //~ Static fields/initializers *******************************************************************************************************************

    private static final long POLL_INTERVAL_MS = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricProcessor.class);

    //~ Instance fields ******************************************************************************************************************************

    private final MetricQueueService metricQueueService;
    private final MetricService metricService;
    private final AtomicInteger jobCounter;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new MetricProcessor object.
     *
     * @param  metricQueueService The async metric queueing service to use.
     * @param  metricService The metric service to use.
     */
    MetricProcessor(MetricQueueService metricQueueService, MetricService metricService, AtomicInteger jobCounter) {
        this.metricQueueService = metricQueueService;
        this.metricService = metricService;
        this.jobCounter = jobCounter;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                AsyncBatchedMetricQuery query = metricQueueService.dequeueAndProcess(1);
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                LOGGER.info("Execution was interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                LOGGER.warn("Exception in MetricProcessor: {}", ex.toString());
                ex.printStackTrace();
            }
        }
    }
}
