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
package com.salesforce.dva.warden.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Policy Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com) Failing to add comment.
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("policy")
public class Policy extends Entity {

    //~ Instance fields ******************************************************************************************************************************

    private String service;
    private String name;
    private List<String> owners = new ArrayList<String>();
    private List<String> users = new ArrayList<String>();
    private String subSystem;
    private String metricName;
    private TriggerType triggerType;
    private Aggregator aggregator;
    private List<Double> threshold;
    private String timeUnit;
    private Double defaultValue;
    private String cronEntry;
    private List<BigInteger> suspensionLevelIds = new ArrayList<BigInteger>();

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getService() {
        return service;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  service  DOCUMENT ME!
     */
    public void setService(String service) {
        this.service = service;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getName() {
        return name;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  name  DOCUMENT ME!
     */
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((aggregator == null) ? 0 : aggregator.hashCode());
        result = prime * result + ((cronEntry == null) ? 0 : cronEntry.hashCode());
        result = prime * result + ((defaultValue == null) ? 0 : defaultValue.hashCode());
        result = prime * result + ((metricName == null) ? 0 : metricName.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((owners == null) ? 0 : owners.hashCode());
        result = prime * result + ((service == null) ? 0 : service.hashCode());
        result = prime * result + ((subSystem == null) ? 0 : subSystem.hashCode());
        result = prime * result + ((suspensionLevelIds == null) ? 0 : suspensionLevelIds.hashCode());
        result = prime * result + ((threshold == null) ? 0 : threshold.hashCode());
        result = prime * result + ((timeUnit == null) ? 0 : timeUnit.hashCode());
        result = prime * result + ((triggerType == null) ? 0 : triggerType.hashCode());
        result = prime * result + ((users == null) ? 0 : users.hashCode());
        return result;
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

        Policy other = (Policy) obj;

        if (aggregator != other.aggregator) {
            return false;
        }
        if (cronEntry == null) {
            if (other.cronEntry != null) {
                return false;
            }
        } else if (!cronEntry.equals(other.cronEntry)) {
            return false;
        }
        if (defaultValue == null) {
            if (other.defaultValue != null) {
                return false;
            }
        } else if (!defaultValue.equals(other.defaultValue)) {
            return false;
        }
        if (metricName == null) {
            if (other.metricName != null) {
                return false;
            }
        } else if (!metricName.equals(other.metricName)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (owners == null) {
            if (other.owners != null) {
                return false;
            }
        } else if (!owners.equals(other.owners)) {
            return false;
        }
        if (service == null) {
            if (other.service != null) {
                return false;
            }
        } else if (!service.equals(other.service)) {
            return false;
        }
        if (subSystem == null) {
            if (other.subSystem != null) {
                return false;
            }
        } else if (!subSystem.equals(other.subSystem)) {
            return false;
        }
        if (suspensionLevelIds == null) {
            if (other.suspensionLevelIds != null) {
                return false;
            }
        } else if (!suspensionLevelIds.equals(other.suspensionLevelIds)) {
            return false;
        }
        if (threshold == null) {
            if (other.threshold != null) {
                return false;
            }
        } else if (!threshold.equals(other.threshold)) {
            return false;
        }
        if (timeUnit == null) {
            if (other.timeUnit != null) {
                return false;
            }
        } else if (!timeUnit.equals(other.timeUnit)) {
            return false;
        }
        if (triggerType != other.triggerType) {
            return false;
        }
        if (users == null) {
            if (other.users != null) {
                return false;
            }
        } else if (!users.equals(other.users)) {
            return false;
        }
        return true;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public List<String> getOwners() {
        return owners;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  owner  DOCUMENT ME!
     */
    public void setOwners(List<String> owner) {
        this.owners = owner;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public List<String> getUsers() {
        return users;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  user  DOCUMENT ME!
     */
    public void setUsers(List<String> user) {
        this.users = user;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getSubSystem() {
        return subSystem;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  subSystem  DOCUMENT ME!
     */
    public void setSubSystem(String subSystem) {
        this.subSystem = subSystem;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getMetricName() {
        return metricName;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  metricName  DOCUMENT ME!
     */
    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public TriggerType getTriggerType() {
        return triggerType;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  triggerType  DOCUMENT ME!
     */
    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Aggregator getAggregator() {
        return aggregator;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  aggregator  DOCUMENT ME!
     */
    public void setAggregator(Aggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public List<Double> getThresholds() {
        return threshold;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  threshold  DOCUMENT ME!
     */
    public void setThresholds(List<Double> threshold) {
        this.threshold = threshold;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getTimeUnit() {
        return timeUnit;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  timeUnit  DOCUMENT ME!
     */
    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Double getDefaultValue() {
        return defaultValue;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  defaultValue  DOCUMENT ME!
     */
    public void setDefaultValue(Double defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getCronEntry() {
        return cronEntry;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  cronEntry  DOCUMENT ME!
     */
    public void setCronEntry(String cronEntry) {
        this.cronEntry = cronEntry;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public List<BigInteger> getSuspensionLevels() {
        return suspensionLevelIds;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  suspensionLevels  DOCUMENT ME!
     */
    public void setSuspensionLevels(List<BigInteger> suspensionLevels) {
        this.suspensionLevelIds = suspensionLevels;
    }

    @Override
    public Object createExample() {
        Policy result = new Policy();

        result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date());
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date());
        result.setService("example-service");
        result.setName("example-name");
        result.setOwners(Arrays.asList("example-owners"));
        result.setUsers(Arrays.asList("example-users"));
        result.setSubSystem("example-subSystem");
        result.setMetricName("example-metricName");
        result.setTriggerType(TriggerType.NOT_BETWEEN);
        result.setAggregator(Aggregator.SUM);
        result.setThresholds(Arrays.asList(0.0));
        result.setTimeUnit("5min");
        result.setDefaultValue(0.0);
        result.setCronEntry("0 */4 * * *");
        return result;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The supported methods for aggregation and downsampling.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Aggregator {

        MIN("min"),
        MAX("max"),
        SUM("sum"),
        AVG("avg"),
        DEV("dev"),
        ZIMSUM("zimsum"),
        MINMIN("minmin"),
        MINMAX("minmax");

        private final String _description;

        private Aggregator(String description) {
            _description = description;
        }

        /**
         * Returns the element corresponding to the given name.
         *
         * @param   name  The aggregator name.
         *
         * @return  The corresponding aggregator element.
         */
        public static Aggregator fromString(String name) {
            if ((name != null) && !name.isEmpty()) {
                for (Aggregator aggregator : Aggregator.values()) {
                    if (name.equalsIgnoreCase(aggregator.name())) {
                        return aggregator;
                    }
                }
            }
            return null;
        }

        /**
         * Returns the short hand description of the method.
         *
         * @return  The method description.
         */
        public String getDescription() {
            return _description;
        }
    }

    /**
     * The type of trigger.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum TriggerType {

        /** Greater than. */
        GREATER_THAN,
        /** Greater than or equal to. */
        GREATER_THAN_OR_EQ,
        /** Less than. */
        LESS_THAN,
        /** Less than or equal to. */
        LESS_THAN_OR_EQ,
        /** Equal to. */
        EQUAL,
        /** Not equal to. */
        NOT_EQUAL,
        /** Between. */
        BETWEEN,
        /** Not between. */
        NOT_BETWEEN;

        /**
         * Converts a string to a trigger type.
         *
         * @param   name  The trigger type name.
         *
         * @return  The corresponding trigger type.
         *
         * @throws  IllegalArgumentException  If no corresponding trigger type is found.
         */
        @JsonCreator
        public static TriggerType fromString(String name) {
            for (TriggerType t : TriggerType.values()) {
                if (t.toString().equalsIgnoreCase(name)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Trigger Type does not exist.");
        }

        /**
         * Returns the name of the trigger type.
         *
         * @return  The name of the trigger type.
         */
        @JsonValue
        public String value() {
            return this.toString();
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
