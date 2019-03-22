package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.quota.IBlacklistService;


/**
 *	@author Kunal Nawale (knawale@salesforce.com)
 *
 *      AmurKafkaSink: Abstract interface for consuming and processing kafka messages
 *
 */

public interface AmurTask {

    /**
     * This method gets called during the init phase, any connections that need to
     * be opened with external systems, any other resources that need to be initialized
     * must be done in this method
     *
     */
    void init(TSDBService tsdbservice,
                     SchemaService schemaService,
                     InstrumentationService instrumentationService,
                     IBlacklistService blacklistService);

    /**
     * This method gets called during the shutdown phase, any resources that need to
     * be closed can be done in this method
     */
    void shutdownTask();
}
