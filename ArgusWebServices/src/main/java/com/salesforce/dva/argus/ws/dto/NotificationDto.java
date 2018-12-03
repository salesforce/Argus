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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
    private List<BigInteger> triggersIds = new ArrayList<>();
    private BigInteger alertId;
    private String customText;
    private int severityLevel = 5;
    private boolean isSRActionable;
    private String articleNumber;
    private String eventName;
    private String elementName;
    private String productTag;

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

    public static boolean validateSRActionableUpdate(NotificationDto notificationDto) {
        boolean isSRActionable = notificationDto.getSRActionable();
        String articleNumber = notificationDto.getArticleNumber();
        return (!isSRActionable || ( articleNumber != null && articleNumber.trim().length() > 0 ));
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
   
    /**
     * Returns the custom text .
     *
     * @return  The custom text.
     */
    public String getCustomText() {
		return customText;
	}
    /**
     * Sets the custom text.
     *
     * @param  customText  The custom text.
     */
	public void setCustomText(String customText) {
		this.customText = customText;
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
    public void setSRActionable(boolean isSRActionable) { this.isSRActionable = isSRActionable; }
    
    /**
     * Gets the severity level of notification
     *
     * @return  The severity level
     */
    public int getSeverityLevel() {
        return severityLevel;
    }

    public String getArticleNumber() { return articleNumber; }


    public void setArticleNumber(String articleNumber) { this.articleNumber = articleNumber; }


    public String getElementName() { return elementName; }


    public void setElementName(String elementName) { this.elementName = elementName; }


    public String getEventName() { return eventName; }


    public void setEventName(String eventName) { this.eventName = eventName; }


    public String getProductTag() { return productTag; }


    public void setProductTag(String productTag) { this.productTag = productTag; }

    /**
     * Sets the severity level of notification
     *
     * @param  severityLevel  The severity level (1-5, 1 - Most Severe, 5 - Least Severe)
     * @throws  IllegalArgumentException  If an error occurs.
     */
    public void setSeverityLevel(int severityLevel) {
        this.severityLevel = severityLevel;
    }    
    
    @Override
    public Object createExample() {
        NotificationDto result = new NotificationDto();

        result.setAlertId(BigInteger.ONE);
        result.setCooldownPeriod(300000);
        result.setCreatedById(BigInteger.TEN);
        result.setCreatedDate(new Date());
        result.setId(BigInteger.ONE);
        result.setMetricsToAnnotate(Arrays.asList(new String[] { "scope:metric{tagk=tagv}" }));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date());
        result.setName("sample-notification");
        result.setNotifierName("email");
        result.setSeverityLevel(5);
        result.setSRActionable(true);
        result.setArticleNumber("sample-articleNumber");
        result.setElementName("sample-elementName");
        result.setEventName("sample-eventName");
        result.setProductTag("sample-productTag");
        result.setSubscriptions(Arrays.asList(new String[] { "joe.smith@salesforce.com" }));
        result.setCustomText("Sample custom text to include in the notification");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
