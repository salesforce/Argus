package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.BatchMetricQuery;

import java.util.Map;

/**
 * Provides methods to create, update, and read batches
 *
 * @author  Colbert Guan (cguan@salesforce.com)
 */
public interface BatchService {

    //~ Methods **************************************************************************************************************************************

    /**
     * Retrieves a batch by its ID.
     *
     * @param   id  The ID of the batch. Cannot be null and must be in canonical UUID format.
     *
     * @return  The Dashboard if one exists or null if no such Dashboard exists.
     */
    BatchMetricQuery findBatchById(String id);

    /**
     * Retrieves a list of batches by username.
     *
     * @param   ownerName   The username for which to retrieve batches. Cannot be null.
     *
     * @return  A list of batches created by this username or an empty list if there are none.
     */
    Map<String, String> findBatchesByOwnerName(String ownerName);

    /**
     * Updates a batch, creating it if necessary.
     *
     * @param   batch   The batch to update
     */
    void updateBatch(BatchMetricQuery batch);

}
