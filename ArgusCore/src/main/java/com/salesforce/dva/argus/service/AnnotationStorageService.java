package com.salesforce.dva.argus.service;

import java.util.List;

import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;

/**
 * Provides methods for putting or retrieving annotations from storage.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public interface AnnotationStorageService extends Service{
    /**
     * Writes annotation data. Any existing data is overwritten.
     *
     * @param  annotations  The list of annotations to write. Cannot be null, but may be empty.
     */
    void putAnnotations(List<Annotation> annotations);

    /**
     * Reads annotation data.
     *
     * @param   queries  The list of queries to execute. Cannot be null, but may be empty.
     *
     * @return  The query results. Will never be null, but may be empty.
     */
    List<Annotation> getAnnotations(List<AnnotationQuery> queries);
}