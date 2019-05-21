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
	 
package com.salesforce.dva.argus.service.annotation;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the annotation service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultAnnotationService extends DefaultService implements AnnotationService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String USER_FIELD_NAME = "user";
    protected static final int MAX_ANNOTATION_SIZE_BYTES = 2000;

    //~ Instance fields ******************************************************************************************************************************

    private Logger _logger = LoggerFactory.getLogger(getClass());
    private final AnnotationStorageService _annotationStorageService;
    private final MonitorService _monitorService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultAnnotationService object.
     *
     * @param  annotationStorageService     The storage service used to perform annotation operations. Cannot be null.
     * @param  monitorService               The monitor service instance to use. Cannot be null.
     */
    @Inject
    DefaultAnnotationService(AnnotationStorageService annotationStorageService, MonitorService monitorService, SystemConfiguration config) {
    	super(config);
        requireArgument(annotationStorageService != null, "The annotation storage service cannot be null.");
        _annotationStorageService = annotationStorageService;
        _monitorService = monitorService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Annotation> getAnnotations(String expression) {
        requireNotDisposed();
        requireArgument(AnnotationReader.isValid(expression), "Invalid annotation expression: " + expression);

        AnnotationReader<Annotation> reader = new AnnotationReader<Annotation>(_annotationStorageService);
        List<Annotation> annotations = new LinkedList<>();

        try {
            _logger.info("Retrieving annotations using {}.", expression);
            annotations.addAll(reader.parse(expression, Annotation.class));
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
        _logger.info("Number of annotations read={}", annotations.size());
        _monitorService.modifyCounter(Counter.ANNOTATION_READS, annotations.size(), null);
        return annotations;
    }

    @Override
    public List<AnnotationQuery> getQueries(String expression) {
        requireNotDisposed();
        requireArgument(AnnotationReader.isValid(expression), "Invalid annotation expression: " + expression);

        AnnotationReader<AnnotationQuery> reader = new AnnotationReader<AnnotationQuery>(_annotationStorageService);
        List<AnnotationQuery> queries = new LinkedList<>();

        try {
            _logger.debug("Retrieving annotations using {}.", expression);
            queries.addAll(reader.parse(expression, AnnotationQuery.class));
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
        return queries;
    }

    @Override
    public void updateAnnotations(Map<Annotation, PrincipalUser> annotations) {
        requireNotDisposed();
        requireArgument(annotations != null, "The set of annotations cannot be null.");
        List<Annotation> putAnnotationList = new LinkedList<>();
        for (Entry<Annotation, PrincipalUser> entry : annotations.entrySet()) {
            PrincipalUser user = entry.getValue();
            Annotation annotation = entry.getKey();

            requireArgument(annotation != null, "The annotation cannot be null.");
            if (annotation.computeSizeBytes() > MAX_ANNOTATION_SIZE_BYTES) {
                _logger.debug("Annotation size of {} bytes exceeded max size {} allowed for annotation {}.",
                        annotation.computeSizeBytes(),
                        MAX_ANNOTATION_SIZE_BYTES,
                        annotation);
                Map<String, String> tags = new HashMap<>();
                tags.put("source", annotation.getSource());
                _monitorService.modifyCounter(Counter.ANNOTATION_DROPS_MAXSIZEEXCEEDED, 1, tags);
                continue;
            }

            Map<String, String> fields = new HashMap<>(annotation.getFields());
            String userName;

            if (user == null || (userName = user.getUserName()) == null || userName.isEmpty()) {
                fields.remove(USER_FIELD_NAME);
            } else {
                fields.put(USER_FIELD_NAME, userName);
            }
            annotation.setFields(fields);
            putAnnotationList.add(annotation);
        }
        _monitorService.modifyCounter(Counter.ANNOTATION_WRITES, putAnnotationList.size(), null);
        if (!putAnnotationList.isEmpty()) {
            _annotationStorageService.putAnnotations(putAnnotationList);
        }
    }

    @Override
    public void updateAnnotation(PrincipalUser user, Annotation annotation) {
        requireNotDisposed();
        requireArgument(user != null, "The user cannot be null.");
        requireArgument(annotation != null, "The annotation cannot be null.");

        Map<Annotation, PrincipalUser> map = new HashMap<>(1);

        map.put(annotation, user);
        updateAnnotations(map);
    }

    @Override
    public void dispose() {
        super.dispose();
        // _tsdbService.dispose();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
