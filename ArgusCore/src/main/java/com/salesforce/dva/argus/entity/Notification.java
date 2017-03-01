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
	 
package com.salesforce.dva.argus.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Encapsulates information about an alert notification. When a condition is triggered, it sends one or more notifications. The interval over which
 * the trigger conditions are evaluated is the entire interval specified by the alert expression.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj Sarkapally(rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "NOTIFICATION", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "alert_id" }))
public class Notification extends JPAEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(name = "name", nullable = false)
    String name;
    String notifierName;
    @ElementCollection
    List<String> subscriptions = new ArrayList<>(0);
    @ElementCollection
    List<String> metricsToAnnotate = new ArrayList<>(0);
    long cooldownPeriod;
    @ManyToOne(optional = false)
    @JoinColumn(name = "alert_id")
    private Alert alert;
    @ManyToMany
    @JoinTable(
        name = "NOTIFICATION_TRIGGER", joinColumns = @JoinColumn(name = "TRIGGER_ID"), inverseJoinColumns = @JoinColumn(name = "NOTIFICATION_ID")
    )
    List<Trigger> triggers = new ArrayList<>(0);
    boolean isSRActionable = false;
    @Lob
    private String customText;
    @ElementCollection
    private Map<String, Long> cooldownExpirationByTriggerAndMetric = new HashMap<>();
	@ElementCollection
    private Map<String, Boolean> activeStatusByTriggerAndMetric = new HashMap<>();

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Notification object with a cool down of one hour and having specified no metrics on which to create annotations.
     *
     * @param  name            The notification name. Cannot be null or empty.
     * @param  alert           The alert with which the notification is associated.
     * @param  notifierName    The notifier implementation class name.
     * @param  subscriptions   The notifier specific list of subscriptions to which notification shall be sent.
     * @param  cooldownPeriod  The cool down period of the notification
     */
    public Notification(String name, Alert alert, String notifierName, List<String> subscriptions, long cooldownPeriod) {
        super(alert.getOwner());
        setAlert(alert);
        setName(name);
        setNotifierName(notifierName);
        setSubscriptions(subscriptions);
        setCooldownPeriod(cooldownPeriod);
        setSRActionable(false);
    }

    /** Creates a new Notification object. */
    protected Notification() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Given a metric to annotate expression, return a corresponding metric object.
     *
     * @param   metric  The metric to annotate expression.
     *
     * @return  The corresponding metric or null if the metric to annotate expression is invalid.
     */
    public static Metric getMetricToAnnotate(String metric) {
        Metric result = null;

        if (metric != null && !metric.isEmpty()) {
            Pattern pattern = Pattern.compile(
                "([\\w,\\-,\\.,/]+):([\\w,\\-,\\.,/]+)(\\{(?:[\\w,\\-,\\.,/]+=[\\w,\\-,\\.,/,\\*,|]+)(?:,[\\w,\\-,\\.,/]+=[\\w,\\-,\\.,/,\\*,|]+)*\\})?:([\\w,\\-,\\.,/]+)");
            Matcher matcher = pattern.matcher(metric.replaceAll("\\s", ""));

            if (matcher.matches()) {
                String scopeName = matcher.group(1);
                String metricName = matcher.group(2);
                String tagString = matcher.group(3);
                Map<String, String> tags = new HashMap<>();

                if (tagString != null) {
                    tagString = tagString.replaceAll("\\{", "").replaceAll("\\}", "");
                    for (String tag : tagString.split(",")) {
                        String[] entry = tag.split("=");

                        tags.put(entry[0], entry[1]);
                    }
                }
                result = new Metric(scopeName, metricName);
                result.setTags(tags);
            }
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the alert with which the notification is associated.
     *
     * @return  The associated alert.
     */
    public Alert getAlert() {
        return alert;
    }

    /**
     * Sets the alert with which the notification is associated.
     *
     * @param  alert  The associated alert. Cannot be null.
     */
    public void setAlert(Alert alert) {
        requireArgument(alert != null, "The alert with which the notification is associated cannot be null.");
        this.alert = alert;
    }

    /**
     * Returns the notifier implementation class name associated with the notification.
     *
     * @return  The notifier implementation class name.
     */
    public String getNotifierName() {
        return notifierName;
    }

    /**
     * Sets the notifier implementation class name.
     *
     * @param  notifierName  The notifier implementation class name. Cannot be null.
     */
    public void setNotifierName(String notifierName) {
        this.notifierName = notifierName;
    }

    /**
     * Returns the subscriptions used by the notifier to send the notifications.
     *
     * @return  The list of subscriptions.
     */
    public List<String> getSubscriptions() {
        return Collections.unmodifiableList(subscriptions);
    }

    /**
     * Replaces the subscriptions used by the notifier to send the notifications.
     *
     * @param  subscriptions  The subscription list.
     */
    public void setSubscriptions(List<String> subscriptions) {
        this.subscriptions.clear();
        if (subscriptions != null && !subscriptions.isEmpty()) {
            this.subscriptions.addAll(subscriptions);
        }
    }

    /**
     * Returns the cool down period of notification.
     *
     * @return  cool down period in milliseconds
     */
    public long getCooldownPeriod() {
        return cooldownPeriod;
    }

    /**
     * Sets the cool down period to notification.
     *
     * @param  cooldownPeriod  cool down period in milliseconds
     */
    public void setCooldownPeriod(long cooldownPeriod) {
        requireArgument(cooldownPeriod >= 0, "Cool down period cannot be negative.");
        this.cooldownPeriod = cooldownPeriod;
    }
    
    /**
     * Returns the cool down expiration time of the notification given a metric,trigger combination.
     *
     * @return  cool down expiration time in milliseconds
     */
    public long getCooldownExpirationByTriggerAndMetric(Trigger trigger, Metric metric) {
    	String key = _hashTriggerAndMetric(trigger, metric);
		return this.cooldownExpirationByTriggerAndMetric.containsKey(key) ? this.cooldownExpirationByTriggerAndMetric.get(key) : 0;
    }

    /**
     * Sets the cool down expiration time of the notification given a metric,trigger combination.
     *
     * @param  cooldownExpiration  cool down expiration time in milliseconds
     */
    public void setCooldownExpirationByTriggerAndMetric(Trigger trigger, Metric metric, long cooldownExpiration) {
        requireArgument(cooldownExpiration >= 0, "Cool down expiration time cannot be negative.");
        
		String key = _hashTriggerAndMetric(trigger, metric);
		this.cooldownExpirationByTriggerAndMetric.put(key, cooldownExpiration);
    }
    
    public Map<String, Long> getCooldownExpirationMap() {
		return cooldownExpirationByTriggerAndMetric;
	}

    /**
     * Returns all metrics to be annotated.
     *
     * @return  list of metrics
     */
    public List<String> getMetricsToAnnotate() {
        return metricsToAnnotate;
    }

    /**
     * Sets metrics to be annotated.
     *
     * @param  metricsToAnnotate  list of metrics.
     */
    public void setMetricsToAnnotate(List<String> metricsToAnnotate) {
        this.metricsToAnnotate.clear();
        if (metricsToAnnotate != null && !metricsToAnnotate.isEmpty()) {
            for (String metric : metricsToAnnotate) {
                requireArgument(getMetricToAnnotate(metric) != null, "Metrics to annotate should be of the form 'scope:metric[{[tagk=tagv]+}]");
                this.metricsToAnnotate.add(metric);
            }
        }
    }
    
    public boolean onCooldown(Trigger trigger, Metric metric) {
        return getCooldownExpirationByTriggerAndMetric(trigger, metric) >= System.currentTimeMillis();
    }

    /**
     * returns the notification name.
     *
     * @return  notification name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Sets the notification name.
     *
     * @param  name  Notification name. Cannot be null or empty.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the triggers associated with the notification.
     *
     * @return  The triggers associated with the notification.
     */
    public List<Trigger> getTriggers() {
        return Collections.unmodifiableList(triggers);
    }

    /**
     * Replaces the triggers associated with the notification.
     *
     * @param  triggers  The triggers associated with the notification.
     */
    public void setTriggers(List<Trigger> triggers) {
        this.triggers.clear();
        if (triggers != null) {
            this.triggers.addAll(triggers);
        }
    }
    
    /**
     * Given a metric,notification combination, indicates whether a triggering condition associated with this notification is still in a triggering state.
     * 
     * @param trigger	The Trigger that caused this notification
     * @param metric	The metric that caused this notification
     * 
     * @return	True if the triggering condition is still in a triggering state.
     */
    public boolean isActiveForTriggerAndMetric(Trigger trigger, Metric metric) {
    	String key = _hashTriggerAndMetric(trigger, metric);
    	return this.activeStatusByTriggerAndMetric.containsKey(key) ? activeStatusByTriggerAndMetric.get(key) : false;
    }
    
    /**
     * When a notification is sent out when a metric violates the trigger threshold, set this notification active for that trigger,metric combination 
     * 
     * @param trigger	The Trigger that caused this notification
     * @param metric	The metric that caused this notification
     */
    public void setActiveForTriggerAndMetric(Trigger trigger, Metric metric, boolean active) {
    	String key = _hashTriggerAndMetric(trigger, metric);
		this.activeStatusByTriggerAndMetric.put(key, active);
    }
    
    /**
     * Indicates whether the notification is monitored by SR
     *
     * @return  True if notification is monitored by SR
     */
    public boolean getSRActionable() {
        return isSRActionable;
    }

    /**
     * Specifies whether the notification should be monitored by SR (actionable by SR)
     *
     * @param  isSRActionable  True if  SR should monitor the notification
     */
    public void setSRActionable(boolean isSRActionable) {
        this.isSRActionable = isSRActionable;
    }
    
	public Map<String, Boolean> getActiveStatusMap() {
		return activeStatusByTriggerAndMetric;
	}
    
    /**
     * Return the custom text in order to include in the notification
	 * @return the customText is optional
	 */
	public String getCustomText() {
		return customText;
	}

	/**
	 * Sets the custom text to the notification
	 * @param customText customText is optional
	 */
	public void setCustomText(String customText) {
		this.customText = customText;
	}

	@Override
    public int hashCode() {
        int hash = 5;

        hash = 29 * hash + Objects.hashCode(this.name);
        hash = 29 * hash + Objects.hashCode(this.alert);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final Notification other = (Notification) obj;

        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.alert, other.alert)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Notification{" + "name=" + name + ", notifierName=" + notifierName + ", subscriptions=" + subscriptions + ", metricsToAnnotate=" +
            metricsToAnnotate + ", cooldownPeriod=" + cooldownPeriod + ", triggers=" + triggers + ", srActionable=" + isSRActionable +  ", customText;" + customText + '}';
    }
    

	private String _hashTriggerAndMetric(Trigger trigger, Metric metric) {
		requireArgument(trigger != null, "Trigger cannot be null.");
        requireArgument(metric != null, "Metric cannot be null");
        
		return trigger.getId().toString() + "$$" + metric.getIdentifier().hashCode();
	}
	
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
