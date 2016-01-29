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
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import java.util.List;
import java.util.Map;

/**
 * Provides methods for creating, updating and querying annotations.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 * @see     <a href="http://google.com">Grammar for this Service</a>
 */
public interface AnnotationService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Evaluates the given expression and returns a list of annotations.
     *
     * @param   expression  A valid query expression. Cannot be null.
     *
     * @return  A list of annotations for the given expressions. Will never be null, but may be empty.
     */
    List<Annotation> getAnnotations(String expression);

    /**
     * Evaluates the given expression and returns a list of annotation queries.
     *
     * @param   expression  A valid query expression. Cannot be null.
     *
     * @return  A list of annotation queries for the given expression. Will never be null, but may be empty.
     */
    List<AnnotationQuery> getQueries(String expression);

    /**
     * Creates or updates an annotation.
     *
     * @param  user        The user for which to create or update the annotation. If null, the annotation will be visible to all users.
     * @param  annotation  The annotation to create or update. Cannot be null.
     */
    void updateAnnotation(PrincipalUser user, Annotation annotation);

    /**
     * Updates a set of annotations having the specified users.
     *
     * @param  annotations  A map of annotations to users. The users may be null if the annotation should be anonymous.
     */
    void updateAnnotations(Map<Annotation, PrincipalUser> annotations);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
