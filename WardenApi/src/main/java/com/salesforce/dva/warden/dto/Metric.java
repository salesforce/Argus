package com.salesforce.dva.warden.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * TODO
 * The metric DTO.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class Metric extends Entity {

    //~ Instance fields ******************************************************************************************************************************
	private String scope;
    private String metric;
    private Map<String, String> tags;
    
    private String namespace;
    private String displayName;
    private String units;
    private Map<Long, String> datapoints;

 
    //~ Methods **************************************************************************************************************************************

    @Override
    public Object createExample() {
        Metric result = new Metric();
        Map<Long, String> dps = new TreeMap<>();

        dps.put(System.currentTimeMillis(), "1.2");
        result.setDatapoints(dps);
        result.setDisplayName("A description of the metric");
        result.setMetric("metric");
        result.setScope("scope");

        Map<String, String> sampleTags = new HashMap<>();

        sampleTags.put("tagk", "tagv");
        result.setTags(sampleTags);
        result.setUnits("ms");
        return result;
    }
    /**
     * Returns the scope.
     *
     * @return  The scope.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the scope.
     *
     * @param  scope  The scope.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Returns the metric name.
     *
     * @return  The metric name.
     */
    public String getMetric() {
        return metric;
    }
    /**
     * Sets the metric name.
     *
     * @param  metric  The metric name.
     */
    public void setMetric(String metric) {
        this.metric = metric;
    }

    /**
     * Returns the metric tags.
     *
     * @return  The metric tags.
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Sets the metric tags.
     *
     * @param  tags  The metric tags.
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
    

	/**
     * Returns the namespace.
     *
     * @return  The namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace.
     *
     * @param  namespace  The namespace.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Returns the display name.
     *
     * @return  The display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name.
     *
     * @param  displayName  The display name.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the metric units.
     *
     * @return  The metric units.
     */
    public String getUnits() {
        return units;
    }

    /**
     * Sets the metric units.
     *
     * @param  units  The metric units.
     */
    public void setUnits(String units) {
        this.units = units;
    }

    /**
     * Returns the metric data points.
     *
     * @return  The metric data points.
     */
    public Map<Long, String> getDatapoints() {
        return datapoints;
    }

    /**
     * Sets the metric data points.
     *
     * @param  datapoints  The metric data points.
     */
    public void setDatapoints(Map<Long, String> datapoints) {
        this.datapoints = datapoints;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
