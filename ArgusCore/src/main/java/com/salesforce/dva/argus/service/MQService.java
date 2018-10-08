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

import com.fasterxml.jackson.databind.JavaType;
import java.io.Serializable;
import java.util.List;

/**
 * Provides methods to synchronously queue and dequeue point to point text based messages.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface MQService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Enqueues an message. If the object is a <tt>String</tt> then a text message shall be enqueued, otherwise an object message shall be enqueued.
     * This method blocks until initialization is complete and the queue is available.
     *
     * @param  <T>        The serializable type of the object.
     * @param  queueName  The queue name to use. Cannot be null or empty.
     * @param  object     The serializable object to enqueue. Cannot be null or empty.
     */
    <T extends Serializable> void enqueue(String queueName, T object);

    /**
     * Enqueues messages. If the objects are of type <tt>String</tt> then text messages shall be enqueued, otherwise object messages shall be
     * enqueued. This method blocks until initialization is complete and the queue is available.
     *
     * @param  <T>        The serializable type of the objects.
     * @param  queueName  The queue name to use. Cannot be null or empty.
     * @param  objects    The serializable objects to enqueue. Cannot be null or empty.
     */
    <T extends Serializable> void enqueue(String queueName, List<T> objects);

    /**
     * Dequeues a message.
     *
     * @param   queueName  The queue name to use. Cannot be null or empty.
     * @param   timeout    If &lt;0 the call will block until a message becomes available. If 0, will return immediately even if no message is
     *                     available to dequeue. If &gt;0, the call will wait for the number of milliseconds before attempting to dequeue the message.
     *
     * @return  The text based object dequeued.
     */
    String dequeue(String queueName, int timeout);

    /**
     * Dequeues a message. If the return type is <tt>String</tt> then a text based message is assumed.
     *
     * @param   <T>        The serializable type of the object being dequeued.
     * @param   queueName  The queue name to use. Cannot be null or empty.
     * @param   type       The type of the object being dequeued.
     * @param   timeout    If &lt;0 the call will block until a message becomes available. If 0, will return immediately even if no message is
     *                     available to dequeue. If &gt;0, the call will wait for the number of milliseconds before attempting to dequeue the message.
     *
     * @return  The dequeued object.
     */
    <T extends Serializable> T dequeue(String queueName, Class<T> type, int timeout);

    /**
     * Dequeues a message. If the return type is <tt>String</tt> then a text based message is assumed.
     *
     * @param   <T>        The serializable type of the object being dequeued.
     * @param   queueName  The queue name to use. Cannot be null or empty.
     * @param   type       The type of the object being dequeued.
     * @param   timeout    If &lt;0 the call will block until a message becomes available. If 0, will return immediately even if no message is
     *                     available to dequeue. If &gt;0, the call will wait for the number of milliseconds before attempting to dequeue the message.
     *
     * @return  The dequeued object.
     */
    <T extends Serializable> T dequeue(String queueName, JavaType type, int timeout);

    /**
     * Dequeues messages.
     *
     * @param   queueName  The queue name to use. Cannot be null or empty.
     * @param   timeout    If &lt;0 the call will block until a message becomes available. If 0, will return immediately even if no message is
     *                     available to dequeue. If &gt;0, the call will wait for the number of milliseconds before attempting to dequeue the message.
     * @param   limit      The maximum number of messages to retrieve. Must be non-negative.
     *
     * @return  The text based object dequeued.
     */
    List<String> dequeue(String queueName, int timeout, int limit);

    /**
     * Dequeues messages. If the return type is <tt>String</tt> then text based messages is assumed.
     *
     * @param   <T>        The serializable type of the object being dequeued.
     * @param   queueName  The queue name to use. Cannot be null or empty.
     * @param   type       The type of the object being dequeued.
     * @param   timeout    If &lt;0 the call will block until a message becomes available. If 0, will return immediately even if no message is
     *                     available to dequeue. If &gt;0, the call will wait for the number of milliseconds before attempting to dequeue the message.
     * @param   limit      The maximum number of messages to retrieve. Must be non-negative.
     *
     * @return  The dequeued objects.
     */
    <T extends Serializable> List<T> dequeue(String queueName, Class<T> type, int timeout, int limit);

    /**
     * Dequeues messages. If the return type is <tt>String</tt> then text based messages is assumed.
     *
     * @param   <T>        The serializable type of the object being dequeued.
     * @param   queueName  The queue name to use. Cannot be null or empty.
     * @param   type       The type of the object being dequeued.
     * @param   timeout    If &lt;0 the call will block until a message becomes available. If 0, will return immediately even if no message is
     *                     available to dequeue. If &gt;0, the call will wait for the number of milliseconds before attempting to dequeue the message.
     * @param   limit      The maximum number of messages to retrieve. Must be non-negative.
     *
     * @return  The dequeued objects.
     */
    <T extends Serializable> List<T> dequeue(String queueName, JavaType type, int timeout, int limit);

    //~ Enums ****************************************************************************************************************************************

    /**
     * The list of available system queues.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum MQQueue {

        /** The name of the queue into which submitted alert data is put. */
        ALERT("argusAlertQueue"),
        /** The name of the queue into which submitted metric data is put. */
        METRIC("argusMetricQueue"),
        /** The name of the queue into which submitted annotation data is put. */
        ANNOTATION("argusAnnotationQueue"),
        /** The name of the queue into which all enabled jobs are put. 
         * The scheduler then enqueues them and determines whether they are to be scheduled or not. */
        TASKQUEUE("argusTaskQueue"),
        /** The name of the queue into which individual queries of batches are put. */
        BATCH("argusBatchQueue");

        private final String _queueName;

        private MQQueue(String queueName) {
            _queueName = queueName;
        }

        /**
         * Returns the collection queue enumeration constant that corresponds to the given name.
         *
         * @param   queueName  The collection queue name.
         *
         * @return  The queue enumeration constant or null if no constant exists for the name.
         */
        public static MQQueue fromQueueName(String queueName) {
            for (MQQueue queue : values()) {
                if (queue.getQueueName().equals(queueName)) {
                    return queue;
                }
            }
            return null;
        }

        /**
         * Returns the queue name for the constant.
         *
         * @return  The queue name.
         */
        public String getQueueName() {
            return _queueName;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
