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

import java.util.Map;
import java.util.Objects;

/**
 * The metric object.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class Metric extends TSDBEntity {

    //~ Instance fields ******************************************************************************************************************************

    private String namespace;
    private String displayName;
    private String units;
    private Map<Long, String> datapoints;

    //~ Methods **************************************************************************************************************************************

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

    @Override
    public int hashCode() {
        int hash = 3;

        hash = 97 * hash + super.hashCode();
        hash = 97 * hash + Objects.hashCode(this.namespace);
        hash = 97 * hash + Objects.hashCode(this.displayName);
        hash = 97 * hash + Objects.hashCode(this.units);
        hash = 97 * hash + Objects.hashCode(this.datapoints);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final Metric other = (Metric) obj;

        if (!super.equals(other)) {
            return false;
        }
        if (!Objects.equals(this.namespace, other.namespace)) {
            return false;
        }
        if (!Objects.equals(this.displayName, other.displayName)) {
            return false;
        }
        if (!Objects.equals(this.units, other.units)) {
            return false;
        }
        if (!Objects.equals(this.datapoints, other.datapoints)) {
            return false;
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
