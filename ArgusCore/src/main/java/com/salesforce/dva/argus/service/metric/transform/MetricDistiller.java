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
	 
package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper class to distill Metrics keeping only the meta data that is common across all metrics. DataPoints and UUID are not distilled.
 *
 * <p>e.g. for tags: [{a,1},{b,2}],[{a,1},{b,3}] => {a,1} because b has value 2 and 3.</p>
 *
 * @author  seamus.carroll
 */
public class MetricDistiller {

    //~ Instance fields ******************************************************************************************************************************

    private final Map<String, String> potentialTags = new HashMap<String, String>();
    private final Map<String, Integer> tagCounts = new HashMap<String, Integer>();
    private final Map<ATTR_TYPE, String> attrs = new HashMap<ATTR_TYPE, String>();
    private final Map<ATTR_TYPE, Integer> attrCounts = new HashMap<ATTR_TYPE, Integer>();
    private int totalAdds = 0;

    //~ Methods **************************************************************************************************************************************

    /**
     * Filters common attributes from list of metrics and writes them to result metric.
     *
     * @param  metrics  - The set of metrics that needs to be filtered
     * @param  result   - The resultant metric that gets populated with the common tags
     */
    public static void setCommonAttributes(List<Metric> metrics, Metric result) {
        MetricDistiller distiller = new MetricDistiller();

        distiller.distill(metrics);
        result.setDisplayName(distiller.getDisplayName());
        result.setUnits(distiller.getUnits());
        result.setTags(distiller.getTags());
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Distills a set of metrics into the common meta data. O(n), where n is number of metrics.
     *
     * @param  metrics  the set of metrics to distill.
     */
    public void distill(List<Metric> metrics) {
        for (Metric m : metrics) {
            distill(m);
        }
    }

    private void distill(Metric m) {
        totalAdds++;

        // Scope is mutable in Metric via protected code paths.  This is a guard against this.
        SystemAssert.requireArgument(m.getScope() != null && !m.getScope().isEmpty(), "Metric scope must be a non-empty string.");
        setAttrDetails(ATTR_TYPE.SCOPE, m.getScope());

        // Metric is mutable in Metric via protected code paths.  This is a guard against this.
        SystemAssert.requireArgument(m.getMetric() != null && !m.getMetric().isEmpty(), "Metric metric must be a non-empty string.");
        setAttrDetails(ATTR_TYPE.METRIC, m.getMetric());
        setAttrDetails(ATTR_TYPE.DISPLAY_NAME, m.getDisplayName());
        setAttrDetails(ATTR_TYPE.UNITS, m.getUnits());

        Map<String, String> tagsToDistill = m.getTags();

        for (Map.Entry<String, String> tag : tagsToDistill.entrySet()) {
            if (potentialTags.get(tag.getKey()) == null) {
                potentialTags.put(tag.getKey(), tag.getValue());
                tagCounts.put(tag.getKey(), 1);
            } else if (potentialTags.get(tag.getKey()).equals(tag.getValue())) {
                tagCounts.put(tag.getKey(), tagCounts.get(tag.getKey()) + 1);
            }
        }
    }

    private void setAttrDetails(ATTR_TYPE name, String value) {
        if (attrs.get(name) == null) {
            attrs.put(name, value);
            attrCounts.put(name, 1);
        } else if (attrs.get(name).equals(value)) {
            attrCounts.put(name, attrCounts.get(name) + 1);
        }
    }

    private String getAttrValue(ATTR_TYPE name) {
        if (attrs.containsKey(name) && attrCounts.get(name) == totalAdds) {
            return attrs.get(name);
        }
        return null;
    }

    /**
     * The common tags.
     *
     * @return  the common tags, empty map if known are common.
     */
    public Map<String, String> getTags() {
        Map<String, String> distilledTags = new HashMap<String, String>();

        for (Map.Entry<String, String> entry : potentialTags.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (tagCounts.get(key).equals(totalAdds)) {
                distilledTags.put(key, value);
            }
        }
        return distilledTags;
    }

    /**
     * The common metric scope.
     *
     * @return  the common value or null.
     */
    public String getScope() {
        return getAttrValue(ATTR_TYPE.SCOPE);
    }

    /**
     * Get the common metric metric.
     *
     * @return  the common value or null.
     */
    public String getMetric() {
        return getAttrValue(ATTR_TYPE.METRIC);
    }

    /**
     * Get the common metric displayName.
     *
     * @return  the common value or null.
     */
    public String getDisplayName() {
        return getAttrValue(ATTR_TYPE.DISPLAY_NAME);
    }

    /**
     * Get the common metric units.
     *
     * @return  the common value or null.
     */
    public String getUnits() {
        return getAttrValue(ATTR_TYPE.UNITS);
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The attribute data to distill.
     *
     * @author  Seamus Carroll (seamus.carroll@salesforce.com)
     */
    private static enum ATTR_TYPE {

        SCOPE,
        METRIC,
        DISPLAY_NAME,
        UNITS
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
