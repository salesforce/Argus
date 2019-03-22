package com.salesforce.perfeng.akc.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.io.Serializable;


/**
 *	@author Kunal Nawale (knawale@salesforce.com)
 *
 *      AmurSinkTask: Abstract interface for consuming and processing kafka messages
 *
 */

public interface AmurSinkTask<K extends Serializable, V extends Serializable> extends AmurTask {


    /**
     * Everytime a batch of messages is collected from kafka, this method will be called.
     * Processing of those Consumer records should happen in this method.
     * IMPORTANT: Care should be taken such this method return within session.timeout time
     * period, else kafka will kill this consumer and rebalance will happen
     * Once this method returns, offsets will be committed.
     *
     * @param records
     *
     */
    public void handleBatch(ConsumerRecords<K, V> records);
}
