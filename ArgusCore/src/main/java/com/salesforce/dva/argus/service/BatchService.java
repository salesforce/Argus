package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;

import java.util.Map;

/**
 * Provides methods to create, update, and read batches
 *
 * @author  Colbert Guan (cguan@salesforce.com)
 */
public interface BatchService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Retrieves a batch by its ID.
     *
     * @param   id  The ID of the batch. Cannot be null and must be in canonical UUID format.
     *
     * @return  The batch if one exists or null if no such batch exists.
     */
    BatchMetricQuery findBatchById(String id);

    /**
     * Retrieves a list of batches by username.
     *
     * @param   ownerName   The username for which to retrieve batches. Cannot be null.
     *
     * @return  A list of the owner's batches in the form of a mapping of UUIDs to statuses.
     */
    Map<String, String> findBatchesByOwnerName(String ownerName);

    /**
     * Enqueues the queries of a batch to be processed by the next available processor client
     *
     * @param   batch   The batch to enqueue
     */
    void enqueueBatch(BatchMetricQuery batch);

    /**
     * Dequeues and processes the next individual query of a batch
     *
     * @param   timeout     The maximum amount of time in milliseconds to attempt to dequeue alerts.
     *
     * @return  The individual query object that was dequeued.
     */
    AsyncBatchedMetricQuery executeNextQuery(int timeout);

    /**
     * Deletes a batch by its ID.
     *
     * @param   id  The ID of the batch. Cannot be null and must be in canonical UUID format.
     */
    void deleteBatch(String id);

}
