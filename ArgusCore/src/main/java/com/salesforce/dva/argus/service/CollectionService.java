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

import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import java.util.List;

/**
 * Provides methods to submit metrics and annotation to the collection queue and similarly commit metrics and annotations from the queue. The service
 * is intended to decouple the submission of new data from users and and the writing of that data into the data store.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface CollectionService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Submits a single metric to the collection queue. User based policy checks are enforced prior to the submission of data. If any policy condition
     * is not met, the method shall throw a runtime exception.
     *
     * @param  submitter  The user submitting the data. Cannot be null.
     * @param  metric     The metric to submit. Cannot be null.
     */
    void submitMetric(PrincipalUser submitter, Metric metric);

    /**
     * Submits metrics to the collection queue. Each message submitted to the queue contains a chunk of metrics. User based policy checks are enforced
     * prior to the submission of data. If any policy condition is not met, the method shall throw a runtime exception.
     *
     * @param  submitter  The user submitting the data. Cannot be null.
     * @param  metrics    The metrics to submit. Cannot be null.
     */
    void submitMetrics(PrincipalUser submitter, List<Metric> metrics);

    /**
     * Commits metrics from the collection queue into the data store. The actual number of metrics committed will be: Summation<SUB>(from i=1 to
     * i=k)</SUB> {n<SUB>i</SUB>}, where n<SUB>i</SUB> is the number of metrics contained in message i. The actual number of messages dequeued will be
     * the maximum number that can be dequeued from the collection queue within the specified timeout period, not to exceed the maximum number
     * specified.
     *
     * @param   messageCount  The maximum number of metric messages to commit from the queue. Must be a positive non-zero number.
     * @param   timeout       The timeout in milliseconds. Must be a positive non-zero number.
     *
     * @return  The list of metrics committed.
     */
    List<Metric> commitMetrics(int messageCount, int timeout);

    /**
     * Commits metric schema records from the collection queue into the data store. The actual number of records committed will be: Summation<SUB>(from i=1 to
     * i=k)</SUB> {n<SUB>i</SUB>}, where n<SUB>i</SUB> is the number of records contained in message i. The actual number of messages dequeued will be
     * the maximum number that can be dequeued from the collection queue within the specified timeout period, not to exceed the maximum number
     * specified.
     *
     * @param   metricCount  The maximum number of metric schema records to commit from the queue. Must be a positive non-zero number.
     * @param   timeout       The timeout in milliseconds. Must be a positive non-zero number.
     *
     * @return  The number of metric schema records committed.
     */
    int commitMetricSchema(int metricCount, int timeout);

    /**
     * Submits a single annotation to the collection queue. User based policy checks are enforced prior to the submission of data. If any policy
     * condition is not met, the method shall throw a runtime exception.
     *
     * @param  submitter   The user submitting the data. Cannot be null.
     * @param  annotation  The annotation to submit. Cannot be null.
     */
    void submitAnnotation(PrincipalUser submitter, Annotation annotation);

    /**
     * Submits annotations to the collection queue. User based policy checks are enforced prior to the submission of data. If any policy condition is
     * not met, the method shall throw a runtime exception.
     *
     * @param  submitter    The user submitting the data. Cannot be null.
     * @param  annotations  The annotations to submit. Cannot be null.
     */
    void submitAnnotations(PrincipalUser submitter, List<Annotation> annotations);

    /**
     * Commits annotations from the collection queue into the data store. The actual number of annotations committed will be the maximum number of
     * annotations which can be dequeued from the collection queue within the timeout period, not to exceed the maximum number specified.
     *
     * @param   annotationCount  The maximum number of annotations to commit from the queue. Must be a positive non-zero number.
     * @param   timeout          The timeout in milliseconds. Must be a positive non-zero number.
     *
     * @return  The number of annotations committed.
     */
    int commitAnnotations(int annotationCount, int timeout);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
