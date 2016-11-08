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
package com.salesforce.dva.argus.sdk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.salesforce.dva.argus.sdk.ArgusHttpClient.ArgusResponse;
import com.salesforce.dva.argus.sdk.ArgusService.EndpointService;
import com.salesforce.dva.argus.sdk.entity.Alert;
import com.salesforce.dva.argus.sdk.entity.Notification;
import com.salesforce.dva.argus.sdk.entity.Trigger;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * Provides methods to manage alerts in Argus.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class AlertService extends EndpointService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESOURCE = "/alerts";

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new AlertService object.
     *
     * @param  client  The HTTP client for use by the service.
     */
    AlertService(ArgusHttpClient client) {
        super(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns all alerts the user is authorized to access.
     *
     * @return  The list of alerts. Will never be null, but may be empty.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Alert> getAlerts() throws IOException {
        String requestUrl = RESOURCE;
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Alert>>() { });
    }

    /**
     * Returns the alert for the given ID.
     *
     * @param   alertId  The alert ID.
     *
     * @return  The alert for the given ID. May be null.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Alert getAlert(BigInteger alertId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Alert.class);
    }

    /**
     * Returns all notifications for the given alert ID.
     *
     * @param   alertId  The alert ID.
     *
     * @return  The list of notifications for the alert. Will never be null, but may be empty.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Notification> getNotifications(BigInteger alertId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Notification>>() { });
    }

    /**
     * Returns all triggers for the given alert ID.
     *
     * @param   alertId  The alert ID.
     *
     * @return  The triggers for the alert. Will never be null, but may be empty.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Trigger> getTriggers(BigInteger alertId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/triggers";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Trigger>>() { });
    }

    /**
     * Returns the notification for the given the alert ID and notification ID.
     *
     * @param   alertId         The alert ID.
     * @param   notificationId  The notification ID.
     *
     * @return  The notification.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Notification getNotification(BigInteger alertId, BigInteger notificationId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Notification.class);
    }

    /**
     * Returns the triggers for the given alert ID and notification ID.
     *
     * @param   alertId         The alert ID.
     * @param   notificationId  The notification ID.
     *
     * @return  The triggers linked with the notification for the specified alert.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Trigger> getTriggersForNotification(BigInteger alertId, BigInteger notificationId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString() + "/triggers";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Trigger>>() { });
    }

    /**
     * Returns the trigger for the given alert ID and trigger ID.
     *
     * @param   alertId    The alert ID.
     * @param   triggerId  The trigger ID.
     *
     * @return  The trigger.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Trigger getTrigger(BigInteger alertId, BigInteger triggerId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/triggers/" + triggerId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Trigger.class);
    }

    /**
     * Returns the trigger given the alert ID, notification ID and the trigger ID.
     *
     * @param   alertId         The alert ID.
     * @param   notificationId  The notification ID.
     * @param   triggerId       The trigger ID.
     *
     * @return  The trigger if it's associated with the notification, or <tt>null</tt> otherwise.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Trigger getTriggerIfAssigned(BigInteger alertId, BigInteger notificationId, BigInteger triggerId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString() + "/triggers/" + triggerId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.GET, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Trigger.class);
    }

    /**
     * Creates a new alert.
     *
     * @param   alert  The alert to create with an un-populated ID field.
     *
     * @return  The new alert having the ID field populated.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Alert createAlert(Alert alert) throws IOException {
        String requestUrl = RESOURCE;
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, alert);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Alert.class);
    }

    /**
     * Updates an existing alert.
     *
     * @param   alertId  The alert ID.
     * @param   alert    The updated alert information.
     *
     * @return  The updated alert.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Alert updateAlert(BigInteger alertId, Alert alert) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.PUT, requestUrl, alert);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Alert.class);
    }

    /**
     * Updates an existing notification.
     *
     * @param   alertId         The ID of the alert that owns the notification.
     * @param   notificationId  The ID of the notification to update.
     * @param   notification    The updated notification information.
     *
     * @return  The updated notification.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Notification updateNotification(BigInteger alertId, BigInteger notificationId, Notification notification) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.PUT, requestUrl, notification);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Notification.class);
    }

    /**
     * Updates an existing trigger.
     *
     * @param   alertId    The ID of the alert owning the trigger.
     * @param   triggerId  The ID of the trigger to update.
     * @param   trigger    The updated trigger information.
     *
     * @return  The updated trigger.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Trigger updateTrigger(BigInteger alertId, BigInteger triggerId, Trigger trigger) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/triggers/" + triggerId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.PUT, requestUrl, trigger);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Trigger.class);
    }

    /**
     * Creates a new notification.
     *
     * @param   alertId       The ID of the alert that will own the notification.
     * @param   notification  The notification to create having an un-populated ID field.
     *
     * @return  The notification having a populated ID field.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Notification> createNotification(BigInteger alertId, Notification notification) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, notification);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Notification>>() { });
    }

    /**
     * Creates a new trigger.
     *
     * @param   alertId  The ID of the alert that will own the trigger.
     * @param   trigger  The trigger to create having an un-populated ID field.
     *
     * @return  The trigger having a populated ID field.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public List<Trigger> createTrigger(BigInteger alertId, Trigger trigger) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/triggers";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, trigger);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), new TypeReference<List<Trigger>>() { });
    }

    /**
     * Links a trigger to a notification.
     *
     * @param   alertId         The ID of the alert that owns the notification and the trigger.
     * @param   notificationId  The ID of the notification to link.
     * @param   triggerId       The ID of the trigger to link.
     *
     * @return  The updated trigger.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public Trigger linkTrigger(BigInteger alertId, BigInteger notificationId, BigInteger triggerId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString() + "/triggers/" + triggerId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.POST, requestUrl, null);

        assertValidResponse(response, requestUrl);
        return fromJson(response.getResult(), Trigger.class);
    }

    /**
     * Deletes an alert including its notifications and triggers.
     *
     * @param   alertId  The ID of the alert to delete.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void deleteAlert(BigInteger alertId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Deletes all notifications for the given alert ID. Associated triggers are not deleted from the alert.
     *
     * @param   alertId  The ID of the alert for which notifications will be deleted.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void deleteNotifications(BigInteger alertId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Deletes the notification for the given alert ID. Associated triggers are not deleted from the alert.
     *
     * @param   alertId         The ID of the alert from which the notification will be deleted.
     * @param   notificationId  The ID of the notification to delete.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void deleteNotification(BigInteger alertId, BigInteger notificationId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Deletes all triggers for the given alert ID. Associated notifications are not deleted from the alert.
     *
     * @param   alertId  The ID of the alert from which triggers will be deleted.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void deleteTriggers(BigInteger alertId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/triggers";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Deletes a trigger having the given ID and removes any associations with the alert or notifications.
     *
     * @param   alertId    The ID of the alert from which the trigger will be deleted.
     * @param   triggerId  The ID of the trigger to delete.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void deleteTrigger(BigInteger alertId, BigInteger triggerId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/triggers/" + triggerId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Disassociates all triggers from the specified notification.
     *
     * @param   alertId         The ID of the alert that owns the notification.
     * @param   notificationId  The notification for which all triggers will be unlinked.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void unlinkTriggers(BigInteger alertId, BigInteger notificationId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString() + "/triggers";
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }

    /**
     * Unlinks the specified trigger from the specified notification.
     *
     * @param   alertId         The ID of the alert that owns the notification and the trigger.
     * @param   notificationId  The ID of the notification.
     * @param   triggerId       The ID of the trigger.
     *
     * @throws  IOException  If the server cannot be reached.
     */
    public void unlinkTrigger(BigInteger alertId, BigInteger notificationId, BigInteger triggerId) throws IOException {
        String requestUrl = RESOURCE + "/" + alertId.toString() + "/notifications/" + notificationId.toString() + "/triggers/" + triggerId.toString();
        ArgusResponse response = getClient().executeHttpRequest(ArgusHttpClient.RequestType.DELETE, requestUrl, null);

        assertValidResponse(response, requestUrl);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
