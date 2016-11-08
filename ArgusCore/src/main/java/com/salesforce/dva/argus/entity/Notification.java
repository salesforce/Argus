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
    long cooldownExpiration;
    @ManyToOne(optional = false)
    @JoinColumn(name = "alert_id")
    private Alert alert;
    @ManyToMany
    @JoinTable(
        name = "NOTIFICATION_TRIGGER", joinColumns = @JoinColumn(name = "TRIGGER_ID"), inverseJoinColumns = @JoinColumn(name = "NOTIFICATION_ID")
    )
    List<Trigger> triggers = new ArrayList<>(0);
    boolean active = false;
    Trigger firedTrigger;

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
        setActive(false);
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
     * @return  The corresponding metric of if the metric to annotate expression is invalid.
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
     * Returns the cool down expiration time of the notification.
     *
     * @return  cool down expiration time in milliseconds
     */
    public long getCooldownExpiration() {
        return cooldownExpiration;
    }

    /**
     * Sets the cool down expiration time of the notification.
     *
     * @param  cooldownExpiration  cool down expiration time in milliseconds
     */
    public void setCooldownExpiration(long cooldownExpiration) {
        requireArgument(cooldownExpiration >= 0, "Cool down expiration time cannot be negative.");
        this.cooldownExpiration = cooldownExpiration;
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

    /**
     * Finds out if the notification is on cool down period.
     *
     * @return  true if the notification is on cool down period otherwise false
     */
    public boolean onCooldown() {
        return getCooldownExpiration() >= System.currentTimeMillis();
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
     * Sets the notification name.
     *
     * @param  name  Notification name. Cannot be null or empty.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Indicates whether a triggering condition associated with this notification is still in a triggering state.
     *
     * @return  True if a triggering condition associated with this notification is still in a triggering state.
     * @todo Use 'firedTrigger != null' and remove the 'active' field.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Specifies whether a triggering condition associated with this notification is still in a triggering state.
     *
     * @param  active  True if  a triggering condition associated with this notification is still in a triggering state.
     * @todo Determine if this can be removed since you can just use 'firedTrigger != null' to determine if the notification is active.
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Indicates the trigger which caused the notification to last be sent.
     *
     * @return  The trigger or null if the notification has not been triggered or the triggering condition has been cleared.
     */
    public Trigger getFiredTrigger() {
        return firedTrigger;
    }

    /**
     * Specifies the trigger which caused the notification to last be sent.
     *
     * @param  firedTrigger  The trigger or null if the notification has not been triggered or the triggering condition has been cleared.
     */
    public void setFiredTrigger(Trigger firedTrigger) {
        this.firedTrigger = firedTrigger;
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
            metricsToAnnotate + ", cooldownPeriod=" + cooldownPeriod + ", cooldownExpiration=" + cooldownExpiration + ", triggers=" + triggers + '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
