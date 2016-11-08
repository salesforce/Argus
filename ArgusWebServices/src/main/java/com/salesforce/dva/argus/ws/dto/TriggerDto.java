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
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Trigger Dto.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class TriggerDto extends EntityDTO {

    //~ Instance fields ******************************************************************************************************************************

    private TriggerType type;
    private String name;
    private Double threshold;
    private Double secondaryThreshold;
    private Long inertia;
    private BigInteger alertId;
    private List<BigInteger> notificationIds = new ArrayList<BigInteger>();

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts trigger entity to triggerDto object.
     *
     * @param   trigger  trigger entity. Cannot be null.
     *
     * @return  triggerDto.
     */
    public static TriggerDto transformToDto(Trigger trigger) {
        TriggerDto result = createDtoObject(TriggerDto.class, trigger);

        // Now copy ID fields
        result.setAlertId(trigger.getAlert().getId());
        for (Notification notification : trigger.getNotifications()) {
            result.addNotificationIds(notification);
        }
        return result;
    }

    /**
     * Converts list of trigger entity objects to list of triggerDto objects.
     *
     * @param   triggers  list of trigger entities.
     *
     * @return  list of triggerDto.
     */
    public static List<TriggerDto> transformToDto(List<Trigger> triggers) {
        List<TriggerDto> result = new ArrayList<TriggerDto>();

        for (Trigger trigger : triggers) {
            result.add(transformToDto(trigger));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns trigger type.
     *
     * @return  The trigger type.
     */
    public TriggerType getType() {
        return type;
    }

    /**
     * Sets the trigger type.
     *
     * @param  type  The trigger type.
     */
    public void setType(TriggerType type) {
        this.type = type;
    }

    /**
     * Returns the trigger name.
     *
     * @return  The trigger name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the trigger name.
     *
     * @param  name  The trigger name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the trigger threshold value.
     *
     * @return  The trigger threshold value.
     */
    public Double getThreshold() {
        return threshold;
    }

    /**
     * Sets the trigger threshold value.
     *
     * @param  threshold  The trigger threshold value.
     */
    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    /**
     * Returns the trigger second threshold value.
     *
     * @return  The trigger second threshold value.
     */
    public Double getSecondaryThreshold() {
        return secondaryThreshold;
    }

    /**
     * Sets the trigger second threshold value.
     *
     * @param  secondaryThreshold  The trigger second threshold value.
     */
    public void setSecondaryThreshold(Double secondaryThreshold) {
        this.secondaryThreshold = secondaryThreshold;
    }

    /**
     * Returns the inertia value.
     *
     * @return  The inertia value.
     */
    public Long getInertia() {
        return inertia;
    }

    /**
     * Sets the inertia value.
     *
     * @param  inertia  The inertia value.
     */
    public void setInertia(Long inertia) {
        this.inertia = inertia;
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
     * Returns the list of notification IDs associated with the trigger.
     * 
     *
     * @return  The list of dashboards IDs owned by the user.
     */
    public List<BigInteger> getNotificationIds() {
        return notificationIds;
    }

    /**
     * Adds a notification ID to the notifications associated with the trigger.
     *
     * @param  notification  The notification to add.
     */
    public void addNotificationIds(Notification notification) {
        this.getNotificationIds().add(notification.getId());
    }

    @Override
    public Object createExample() {
        TriggerDto result = new TriggerDto();

        result.setAlertId(BigInteger.ONE);
        result.setCreatedById(BigInteger.TEN);
        result.setId(BigInteger.valueOf(2));
        result.setInertia(300000L);
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date());
        result.setCreatedDate(new Date());
        result.setName("sample-trigger");
        result.setSecondaryThreshold(10.);
        result.setThreshold(-10.);
        result.setType(TriggerType.NOT_BETWEEN);
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
