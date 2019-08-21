package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

/**
 * The common interface for the existing schema record list classes when they are being used
 * to retrieve record by Id
 * @param <T>
 */
public interface RecordFinder<T> {

    Set<String> getIdSet();

    String getScrollID();

    void setScrollID(String scrollID);

    T getRecord(String id);

    List<T> getRecords();


}