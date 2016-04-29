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
	 
package com.salesforce.dva.argus.ws.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * Alert Dto.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertDto extends EntityDTO {

    //~ Instance fields ******************************************************************************************************************************

    private String name;
    private String expression;
    private String cronEntry;
    private boolean enabled = false;
    private boolean missingDataNotificationEnabled;
    private List<BigInteger> notificationsIds = new ArrayList<>(0);
    private List<BigInteger> triggersIds = new ArrayList<>(0);
    private String ownerName;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts alert entity to alertDto.
     *
     * @param   alert  The alert object. Cannot be null.
     *
     * @return  AlertDto object.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static AlertDto transformToDto(Alert alert) {
        if (alert == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        AlertDto result = createDtoObject(AlertDto.class, alert);

        result.setOwnerName(alert.getOwner().getUserName());
        for (Trigger trigger : alert.getTriggers()) {
            result.addTriggersIds(trigger);
        }
        for (Notification notification : alert.getNotifications()) {
            result.addNotificationsIds(notification);
        }
        return result;
    }

    /**
     * Converts list of alert entity objects to list of alertDto objects.
     *
     * @param   alerts  List of alert entities. Cannot be null.
     *
     * @return  List of alertDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<AlertDto> transformToDto(List<Alert> alerts) {
        if (alerts == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<AlertDto> result = new ArrayList<AlertDto>();

        for (Alert alert : alerts) {
            result.add(transformToDto(alert));
        }
        return result;
    }

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
    public Object createExample() {
        AlertDto result = new AlertDto();

        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date());
        result.setCronEntry("0 */4 * * *");
        result.setEnabled(true);
        result.setExpression("DIVIDE(-1h:scope:metric{tagk=tagv}:avg, -2h:-1h:scope:metric{tagk=tagv}:avg)");
        result.setId(BigInteger.ONE);
        result.setMissingDataNotificationEnabled(true);
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date());
        result.setName("example-alert");
        result.setOwnerName("admin");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
