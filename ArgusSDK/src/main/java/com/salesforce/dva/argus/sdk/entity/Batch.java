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
package com.salesforce.dva.argus.sdk.entity;

import java.util.List;

/**
 * Batch query object.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Batch {

    //~ Instance fields ******************************************************************************************************************************

    private String status;
    private int ttl;
    private long createdDate;
    private String ownerName;
    private List<Query> queries;

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the batch status.
     *
     * @return  The batch status.
     */
    public String getStatus() {
        return status;
    }

    /**
     * Returns the time to live for the batch.
     *
     * @return  The time to live.
     */
    public int getTtl() {
        return ttl;
    }

    /**
     * Returns the time the batch was created in milliseconds from the epoch.
     *
     * @return  The time the batch was created.
     */
    public long getCreatedDate() {
        return createdDate;
    }

    /**
     * Returns the name of the batch owner.
     *
     * @return  The name of the batch owner.
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Returns the list of queries associated with the batch.
     *
     * @return  The queries associated with the batch.
     */
    public List<Query> getQueries() {
        return queries;
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * The batch query object.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static class Query {

        private String expression;
        private Metric result;
        private String message;

        /**
         * Creates a new Query object.
         *
         * @param  expression  The query expression.
         * @param  result      The query result.
         * @param  message     The status message.
         */
        Query(String expression, Metric result, String message) {
            this.expression = expression;
            this.result = result;
            this.message = message;
        }

        /**
         * Returns the expression.
         *
         * @return  The expression.
         */
        public String getExpression() {
            return expression;
        }

        /**
         * Sets the expression.
         *
         * @param  expression  The expression.
         */
        public void setExpression(String expression) {
            this.expression = expression;
        }

        /**
         * Returns the query result.
         *
         * @return  The query result.
         */
        public Metric getResult() {
            return result;
        }

        /**
         * Sets the query result.
         *
         * @param  result  The query result.
         */
        public void setResult(Metric result) {
            this.result = result;
        }

        /**
         * Returns the status message.
         *
         * @return  The status message.
         */
        public String getMessage() {
            return message;
        }

        /**
         * Sets the status message.
         *
         * @param  message  The status message.
         */
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
