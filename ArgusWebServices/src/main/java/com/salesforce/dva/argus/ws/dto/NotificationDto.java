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
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * Notification Dto.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificationDto extends EntityDTO {

    //~ Instance fields ******************************************************************************************************************************

    private String name;
    private String notifierName;
    private List<String> subscriptions;
    private List<String> metricsToAnnotate;
    private long cooldownPeriod;
    private long cooldownExpiration;
    private List<BigInteger> triggersIds = new ArrayList<>();
    private BigInteger alertId;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts notification entity object to notificationDto object.
     *
     * @param   notification  notification entity. Cannot be null.
     *
     * @return  notificationDto.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static NotificationDto transformToDto(Notification notification) {
        if (notification == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        NotificationDto result = createDtoObject(NotificationDto.class, notification);

        result.setAlertId(notification.getAlert().getId());
        for (Trigger trigger : notification.getTriggers()) {
            result.addTriggersIds(trigger);
        }
        return result;
    }

    /**
     * Converts list of notification entity objects to list of notificationDto objects.
     *
     * @param   notifications  list of notification objects. Cannot be null.
     *
     * @return  list of notificationDto objects
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<NotificationDto> transformToDto(List<Notification> notifications) {
        if (notifications == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<NotificationDto> result = new ArrayList<NotificationDto>();

        for (Notification notification : notifications) {
            result.add(transformToDto(notification));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the notification name.
     *
     * @return  The notification name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the notification name.
     *
     * @param  name  The notification name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the notifier name.
     *
     * @return  The notifier name.
     */
    public String getNotifierName() {
        return notifierName;
    }

    /**
     * Sets the notifier name.
     *
     * @param  notifierName  The notifier name.
     */
    public void setNotifierName(String notifierName) {
        this.notifierName = notifierName;
    }

    /**
     * Returns the subscriptions.
     *
     * @return  The subscriptions
     */
    public List<String> getSubscriptions() {
        return subscriptions;
    }

    /**
     * Sets the subscriptions.
     *
     * @param  subscriptions  The subscriptions
     */
    public void setSubscriptions(List<String> subscriptions) {
        this.subscriptions = subscriptions;
    }

    /**
     * Returns the list of metric names that should be annotated.
     *
     * @return  The list of metric names.
     */
    public List<String> getMetricsToAnnotate() {
        return metricsToAnnotate;
    }

    /**
     * Sets the list of metrics that should be annotated.
     *
     * @param  metricsToAnnotate  The list of metric names.
     */
    public void setMetricsToAnnotate(List<String> metricsToAnnotate) {
        this.metricsToAnnotate = metricsToAnnotate;
    }

    /**
     * Returns the cool down period.
     *
     * @return  The cool down period.
     */
    public long getCooldownPeriod() {
        return cooldownPeriod;
    }

    /**
     * Sets the cool down period.
     *
     * @param  cooldownPeriod  The cool down period.
     */
    public void setCooldownPeriod(long cooldownPeriod) {
        this.cooldownPeriod = cooldownPeriod;
    }

    /**
     * Returns the cool down expiration time in milliseconds.
     *
     * @return  The cool down expiration time
     */
    public long getCooldownExpiration() {
        return cooldownExpiration;
    }

    /**
     * Sets the cool down expiration time.
     *
     * @param  cooldownExpiration  The cool down expiration time
     */
    public void setCooldownExpiration(long cooldownExpiration) {
        this.cooldownExpiration = cooldownExpiration;
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
     * Adds the trigger to notification.
     *
     * @param  trigger  The trigger.
     */
    public void addTriggersIds(Trigger trigger) {
        this.getTriggersIds().add(trigger.getId());
    }

    /**
     * Returns the alert Id.
     *
     * @return  The alert Id.
     */
    public BigInteger getAlertId() {
        return alertId;
    }

    /**
     * Sets the alert Id.
     *
     * @param  alertId  The alert Id.
     */
    public void setAlertId(BigInteger alertId) {
        this.alertId = alertId;
    }

    @Override
    public Object createExample() {
        NotificationDto result = new NotificationDto();

        result.setAlertId(BigInteger.ONE);
        result.setCooldownExpiration(System.currentTimeMillis() + 300000);
        result.setCooldownPeriod(300000);
        result.setCreatedById(BigInteger.TEN);
        result.setCreatedDate(new Date());
        result.setId(BigInteger.ONE);
        result.setMetricsToAnnotate(Arrays.asList(new String[] { "scope:metric{tagk=tagv}" }));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date());
        result.setName("sample-notification");
        result.setNotifierName("email");
        result.setSubscriptions(Arrays.asList(new String[] { "joe.smith@salesforce.com" }));
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
