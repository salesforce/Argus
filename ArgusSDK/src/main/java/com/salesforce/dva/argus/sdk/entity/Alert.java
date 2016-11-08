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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Alert object.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Alert extends Entity {

    //~ Instance fields ******************************************************************************************************************************

    private String name;
    private String expression;
    private String cronEntry;
    private boolean enabled = false;
    private boolean missingDataNotificationEnabled;
    private final List<BigInteger> notificationsIds = new ArrayList<>(0);
    private final List<BigInteger> triggersIds = new ArrayList<>(0);
    private String ownerName;

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the alert name.
     *
     * @return  The alert name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the alert name.
     *
     * @param  name  The alert name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the alert expression.
     *
     * @return  The alert expression
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Sets the alert expression.
     *
     * @param  expression  The alert expression
     */
    public void setExpression(String expression) {
        this.expression = expression;
    }

    /**
     * Returns the cron.
     *
     * @return  The cron
     */
    public String getCronEntry() {
        return cronEntry;
    }

    /**
     * Sets the cron.
     *
     * @param  cronEntry  The cron.
     */
    public void setCronEntry(String cronEntry) {
        this.cronEntry = cronEntry;
    }

    /**
     * Returns true if this alert is enabled otherwise false.
     *
     * @return  boolean variable which indicates the alert status.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets the alert status.
     *
     * @param  enabled  Boolean variable indicating alert status
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the missingDataNotificationEnabled.
     *
     * @return  The missingDataNotificationEnabled
     */
    public boolean isMissingDataNotificationEnabled() {
        return missingDataNotificationEnabled;
    }

    /**
     * Sets the missingDataNotificationEnabled.
     *
     * @param  missingDataNotificationEnabled  The missingDataNotificationEnabled
     */
    public void setMissingDataNotificationEnabled(boolean missingDataNotificationEnabled) {
        this.missingDataNotificationEnabled = missingDataNotificationEnabled;
    }

    /**
     * Returns the list of notification Ids.
     *
     * @return  The list of notification Ids.
     */
    public List<BigInteger> getNotificationsIds() {
        return notificationsIds;
    }

    /**
     * Adds the notification.
     *
     * @param  notification  The notification.
     */
    public void addNotificationsIds(Notification notification) {
        this.getNotificationsIds().add(notification.getId());
    }

    /**
     * Returns the list of trigger Ids.
     *
     * @return  The list of trigger Ids.
     */
    public List<BigInteger> getTriggersIds() {
        return triggersIds;
    }

    /**
     * Adds the trigger.
     *
     * @param  trigger  The trigger
     */
    public void addTriggersIds(Trigger trigger) {
        this.getTriggersIds().add(trigger.getId());
    }

    /**
     * Returns the owner name.
     *
     * @return  The owner name.
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Sets the owner name.
     *
     * @param  ownerName  The owner name.
     */
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 37 * hash + super.hashCode();
        hash = 37 * hash + Objects.hashCode(this.name);
        hash = 37 * hash + Objects.hashCode(this.expression);
        hash = 37 * hash + Objects.hashCode(this.cronEntry);
        hash = 37 * hash + (this.enabled ? 1 : 0);
        hash = 37 * hash + (this.missingDataNotificationEnabled ? 1 : 0);
        hash = 37 * hash + Objects.hashCode(this.notificationsIds);
        hash = 37 * hash + Objects.hashCode(this.triggersIds);
        hash = 37 * hash + Objects.hashCode(this.ownerName);
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

        final Alert other = (Alert) obj;

        if (!super.equals(other)) {
            return false;
        }
        if (this.enabled != other.enabled) {
            return false;
        }
        if (this.missingDataNotificationEnabled != other.missingDataNotificationEnabled) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.expression, other.expression)) {
            return false;
        }
        if (!Objects.equals(this.cronEntry, other.cronEntry)) {
            return false;
        }
        if (!Objects.equals(this.ownerName, other.ownerName)) {
            return false;
        }
        if (!Objects.equals(this.notificationsIds, other.notificationsIds)) {
            return false;
        }
        if (!Objects.equals(this.triggersIds, other.triggersIds)) {
            return false;
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
