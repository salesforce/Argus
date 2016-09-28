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
	 
package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.AlertDto;
import com.salesforce.dva.argus.ws.dto.NotificationDto;
import com.salesforce.dva.argus.ws.dto.TriggerDto;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Web services for Alert.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Path("/alerts")
@Description("Provides methods to manipulate alerts.")
public class AlertResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private AlertService alertService = system.getServiceFactory().getAlertService();
    private final String OWNER_NAME = "ownername";

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the list of alerts created by the user.
     *
     * @param   req        The HttpServlet request object. Cannot be null.
     * @param   alertname  Name of the alert. It is optional.
     * @param   ownerName  Name of the owner. It is optional.
     *
     * @return  The list of alerts created by the user.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Returns all alerts.")
    public List<AlertDto> getAlerts(@Context HttpServletRequest req,
        @QueryParam("alertname") String alertname,
        @QueryParam(OWNER_NAME) String ownerName) {
        List<Alert> result = null;
        PrincipalUser owner = validateAndGetOwner(req, ownerName);

        if (alertname != null && !alertname.isEmpty()) {
            Alert alert = alertService.findAlertByNameAndOwner(alertname, owner);

            result = new ArrayList<Alert>();
            if (alert != null) {
                result.add(alert);
            }
        } else {
            result = owner.isPrivileged() ? alertService.findAllAlerts() : alertService.findAlertsByOwner(owner);
        }
        return AlertDto.transformToDto(result);
    }

    /**
     * Finds an alert by alert ID.
     *
     * @param   req      The HttpServlet request object. Cannot be null.
     * @param   alertId  ID of an alert. Cannot be null and must be a positive non-zero number.
     *
     * @return  Alert
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}")
    @Description("Returns an alert by its ID.")
    public AlertDto getAlertByID(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            return AlertDto.transformToDto(alert);
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Returns the list of notifications for a given alert id.
     *
     * @param   req      The HttpServlet request object. Cannot be null.
     * @param   alertId  The alert Id for which notifications are requested. Cannot be null and must be a positive non-zero number.
     *
     * @return  List of notifications.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications")
    @Description("Returns all notifications for the given alert ID.")
    public List<NotificationDto> getAllNotifications(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            return NotificationDto.transformToDto(alert.getNotifications());
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * returns the list of triggers for a given alert Id.
     *
     * @param   req      The HttpServlet request object. Cannot be null.
     * @param   alertId  The alert Id for which the triggers are requested. Cannot be null and must be a positive non-zero number.
     *
     * @return  List of triggers.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/triggers")
    @Description("Returns all triggers for the given alert ID.")
    public List<TriggerDto> getAllTriggers(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            return TriggerDto.transformToDto(alert.getTriggers());
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Returns the notification for a given alert id and Notification Id.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         The alert Id. Cannot be null and must be a positive non-zero number.
     * @param   notificationId  The notification Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  The notification.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the notification does not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}")
    @Description("Returns a notification by its ID.")
    public NotificationDto getNotificationById(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            for (Notification notification : alert.getNotifications()) {
                if (notification.getId().equals(notificationId)) {
                    return NotificationDto.transformToDto(notification);
                }
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Returns all triggers of a requested notification.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         The alert Id. Cannot be null.
     * @param   notificationId  The notification Id. Cannot be null.
     *
     * @return  List of triggers.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert or notification do not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}/triggers")
    @Description("Returns all the triggers for the given notification ID.")
    public List<TriggerDto> getTriggersByNotificationId(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            for (Notification notification : alert.getNotifications()) {
                if (notification.getId().equals(notificationId)) {
                    return TriggerDto.transformToDto(notification.getTriggers());
                }
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Returns the trigger for a given alert Id and trigger Id.
     *
     * @param   req        The HttpServlet request object. Cannot be null.
     * @param   alertId    The alert Id. Cannot be null and must be a positive non-zero number.
     * @param   triggerId  The trigger Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  The trigger
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the trigger does not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/triggers/{triggerId}")
    @Description("Returns a trigger by its ID.")
    public TriggerDto getTriggerById(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("triggerId") BigInteger triggerId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerId == null || triggerId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Trigger Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            for (Trigger trigger : alert.getTriggers()) {
                if (trigger.getId().equals(triggerId)) {
                    return TriggerDto.transformToDto(trigger);
                }
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Returns the trigger.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         The alert Id. Cannot be null.
     * @param   notificationId  The notification Id. Cannot be null.
     * @param   triggerId       The trigger Id. Cannot be null.
     *
     * @return  The trigger object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert or notification or trigger do not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}/triggers/{triggerId}")
    @Description("Returns an trigger by its ID only if it is associated with the given notification ID.")
    public TriggerDto getTriggerByNotificationNTriggerId(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId,
        @PathParam("triggerId") BigInteger triggerId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerId == null || triggerId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Trigger Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, null);
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), owner);
            for (Notification notification : alert.getNotifications()) {
                if (notification.getId().equals(notificationId)) {
                    for (Trigger trigger : notification.getTriggers()) {
                        if (trigger.getId().equals(triggerId)) {
                            return TriggerDto.transformToDto(trigger);
                        }
                    }
                }
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Creates a new alert.
     *
     * @param   req       The HttpServlet request object. Cannot be null.
     * @param   alertDto  The alert object. Cannot be null.
     *
     * @return  Created alert object.
     *
     * @throws  WebApplicationException  The exception with 400 status will be thrown if the alert object is null.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Creates an alert.")
    public AlertDto createAlert(@Context HttpServletRequest req, AlertDto alertDto) {
        if (alertDto == null) {
            throw new WebApplicationException("Null alert object cannot be created.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, alertDto.getOwnerName());
        Alert alert = new Alert(getRemoteUser(req), owner, alertDto.getName(), alertDto.getExpression(), alertDto.getCronEntry());

        copyProperties(alert, alertDto);
        return AlertDto.transformToDto(alertService.updateAlert(alert));
    }

    /**
     * Updates existing alert.
     *
     * @param   req       The HttpServlet request object. Cannot be null.
     * @param   alertId   The id of an alert. Cannot be null.
     * @param   alertDto  The new alert object. Cannot be null.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert does not exist.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{alertId}")
    @Description("Updates an alert having the given ID.")
    public AlertDto updateAlert(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId, AlertDto alertDto) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (alertDto == null) {
            throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, alertDto.getOwnerName());
        Alert oldAlert = alertService.findAlertByPrimaryKey(alertId);

        if (oldAlert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, oldAlert.getOwner(), owner);
        copyProperties(oldAlert, alertDto);
        oldAlert.setModifiedBy(getRemoteUser(req));
        return AlertDto.transformToDto(alertService.updateAlert(oldAlert));
    }

    /**
     * Updates the notification.
     *
     * @param   req              The HttpServlet request object. Cannot be null.
     * @param   alertId          The alert Id. Cannot be null.
     * @param   notificationId   The notification Id. Cannot be null.
     * @param   notificationDto  New notification object. Cannot be null.
     *
     * @return  Updated notification object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if either an alert or notification do not exist.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}")
    @Description("Updates a notification having the given notification ID if associated with the given alert ID.")
    public NotificationDto updateNotificationById(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId, NotificationDto notificationDto) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationDto == null) {
            throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, getRemoteUser(req).getUserName());
        Alert oldAlert = alertService.findAlertByPrimaryKey(alertId);

        if (oldAlert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, oldAlert.getOwner(), owner);
        for (Notification notification : oldAlert.getNotifications()) {
            if (notificationId.equals(notification.getId())) {
                copyProperties(notification, notificationDto);
                oldAlert.setModifiedBy(getRemoteUser(req));

                Alert alert = alertService.updateAlert(oldAlert);
                int index = alert.getNotifications().indexOf(notification);

                return NotificationDto.transformToDto(alert.getNotifications().get(index));
            }
        }
        throw new WebApplicationException("The notification does not exist.", Response.Status.NOT_FOUND);
    }

    /**
     * Updates the trigger.
     *
     * @param   req          The HttpServlet request object. Cannot be null.
     * @param   alertId      The alert Id. Cannot be null.
     * @param   triggerId    The trigger Id. Cannot be null.
     * @param   triggerdDto  New trigger object. Cannot be null.
     *
     * @return  Updated trigger object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if either an alert or trigger do not exist.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/triggers/{triggerId}")
    @Description("Updates a trigger having the given ID if it is associated with the given alert ID.")
    public TriggerDto updateTriggerById(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("triggerId") BigInteger triggerId, TriggerDto triggerdDto) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerId == null || triggerId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Trigger Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerdDto == null) {
            throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, getRemoteUser(req).getUserName());
        Alert oldAlert = alertService.findAlertByPrimaryKey(alertId);

        if (oldAlert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, oldAlert.getOwner(), owner);
        for (Trigger trigger : oldAlert.getTriggers()) {
            if (triggerId.equals(trigger.getId())) {
                copyProperties(trigger, triggerdDto);
                oldAlert.setModifiedBy(getRemoteUser(req));

                Alert alert = alertService.updateAlert(oldAlert);
                int index = alert.getTriggers().indexOf(trigger);

                return TriggerDto.transformToDto(alert.getTriggers().get(index));
            }
        }
        throw new WebApplicationException("The trigger does not exist.", Response.Status.NOT_FOUND);
    }

    /**
     * Creates a new notification for a given alert.
     *
     * @param   req              The HttpServlet request object. Cannot be null.
     * @param   alertId          The alert Id. Cannot be null and must be a positive non-zero number.
     * @param   notificationDto  The notification object. Cannot be null.
     *
     * @return  The updated alert object
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications")
    @Description("Creates new notifications for the given alert ID.")
    public List<NotificationDto> addNotification(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId, NotificationDto notificationDto) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationDto == null) {
            throw new WebApplicationException("Null notification object cannot be created.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));

            Notification notification = new Notification(notificationDto.getName(), alert, notificationDto.getNotifierName(),
                notificationDto.getSubscriptions(), notificationDto.getCooldownPeriod());

            notification.setMetricsToAnnotate(new ArrayList<>(notificationDto.getMetricsToAnnotate()));

            List<Notification> notifications = new ArrayList<Notification>(alert.getNotifications());

            notifications.add(notification);
            alert.setNotifications(notifications);
            alert.setModifiedBy(getRemoteUser(req));
            return NotificationDto.transformToDto(alertService.updateAlert(alert).getNotifications());
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Creates a new Trigger for a given alert.
     *
     * @param   req         The HttpServlet request object. Cannot be null.
     * @param   alertId     The alert Id. Cannot be null and must be a positive non-zero number.
     * @param   triggerDto  The trigger object. Cannot be null.
     *
     * @return  The alert object for which the trigger was added.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/triggers")
    @Description("Creates new triggers for the given alert ID.")
    public List<TriggerDto> addTrigger(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId, TriggerDto triggerDto) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerDto == null) {
            throw new WebApplicationException("Null trigger object cannot be created.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));

            Trigger trigger = new Trigger(alert, triggerDto.getType(), triggerDto.getName(), triggerDto.getThreshold(),
                triggerDto.getSecondaryThreshold(), triggerDto.getInertia());
            List<Trigger> triggers = new ArrayList<Trigger>(alert.getTriggers());

            triggers.add(trigger);
            alert.setTriggers(triggers);
            alert.setModifiedBy(getRemoteUser(req));
            return TriggerDto.transformToDto(alertService.updateAlert(alert).getTriggers());
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Adds trigger to the notification.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         The alert Id. Cannot be null and must be a positive non-zero number.
     * @param   notificationId  The notification Id. Cannot be null and must be a positive non-zero number.
     * @param   triggerId       The trigger Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  Updated alert.
     *
     * @throws  WebApplicationException  Throws exception if the input data is invalid.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}/triggers/{triggerId}")
    @Description(
        "Associates the trigger having the given ID to the given notification ID.  Both the trigger and notification must be owned by the alert."
    )
    public TriggerDto addTriggerToNotification(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId,
        @PathParam("triggerId") BigInteger triggerId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerId == null || triggerId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Trigger Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Notification notification = null;
        Trigger alertTrigger = null;
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert == null) {
            throw new WebApplicationException(Status.NOT_FOUND.getReasonPhrase(), Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));
        for (Notification tempNotification : alert.getNotifications()) {
            if (tempNotification.getId().equals(notificationId)) {
                notification = tempNotification;
                break;
            }
        }
        if (notification == null) {
            throw new WebApplicationException("Notification Id does not exist for this alert.", Status.BAD_REQUEST);
        }
        for (Trigger tempTrigger : alert.getTriggers()) {
            if (tempTrigger.getId().equals(triggerId)) {
                alertTrigger = tempTrigger;
                break;
            }
        }
        if (alertTrigger == null) {
            throw new WebApplicationException("Trigger Id does not exist for this alert. Create a trigger first then add it to the notification",
                Status.BAD_REQUEST);
        }

        // Make sure that the notification does not have this trigger.
        for (Trigger tempTrigger : notification.getTriggers()) {
            if (tempTrigger.getId().equals(triggerId)) {
                throw new WebApplicationException("This trigger already exists for the notification.", Status.BAD_REQUEST);
            }
        }

        List<Trigger> list = new ArrayList<Trigger>(notification.getTriggers());

        list.add(alertTrigger);
        notification.setTriggers(list);
        alert.setModifiedBy(getRemoteUser(req));
        alert = alertService.updateAlert(alert);
        for (Notification tempNotification : alert.getNotifications()) {
            if (tempNotification.getId().equals(notificationId)) {
                for (Trigger tempTrigger : notification.getTriggers()) {
                    if (tempTrigger.getId().equals(triggerId)) {
                        return TriggerDto.transformToDto(tempTrigger);
                    }
                }
            }
        }
        throw new WebApplicationException("Trigger update failed.", Status.INTERNAL_SERVER_ERROR);
    }

    /**
     * Deletes the alert.
     *
     * @param   req      The HttpServlet request object. Cannot be null.
     * @param   alertId  The alert Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  REST response indicating whether the alert deletion was successful.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}")
    @Description("Deletes the alert having the given ID along with all its triggers and notifications.")
    public Response deleteAlert(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));
            alertService.markAlertForDeletion(alert);
            return Response.status(Status.OK).build();
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Deletes all notifications for a given alert.
     *
     * @param   req      The HttpServlet request object. Cannot be null.
     * @param   alertId  The alert id. Cannot be null and must be a positive non-zero number.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications")
    @Description("Deletes all notifications for the given alert ID. Associated triggers are not deleted from the alert.")
    public Response deleteAllNotificationsByAlertId(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert != null) {
            validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));
            alert.setNotifications(new ArrayList<Notification>(0));
            alert.setModifiedBy(getRemoteUser(req));
            alertService.updateAlert(alert);
            return Response.status(Status.OK).build();
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Deletes the notification.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         The alert Id. Cannot be null and must be a positive non-zero number.
     * @param   notificationId  The notification id. Cannot be null and must be a positive non-zero number.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}")
    @Description(
        "Deletes a notification having the given ID if it is associated with the given alert ID.  Associated triggers are not deleted from the alert."
    )
    public Response deleteNotificationsById(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));

        List<Notification> listNotification = new ArrayList<Notification>(alert.getNotifications());
        Iterator<Notification> it = listNotification.iterator();

        while (it.hasNext()) {
            Notification notification = it.next();

            if (notification.getId().equals(notificationId)) {
                it.remove();
                alert.setNotifications(listNotification);
                alert.setModifiedBy(getRemoteUser(req));
                alertService.updateAlert(alert);
                return Response.status(Status.OK).build();
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Deletes all triggers.
     *
     * @param   req      The HttpServlet request object. Cannot be null.
     * @param   alertId  Alert Id. cannot be null.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/triggers")
    @Description("Deletes all triggers for the given alert ID. All associations to alert notifications are also removed.")
    public Response deleteAllTriggersByAlertId(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));

        // Remove triggers for all notifications TODO: is this required?
        for (Notification notification : alert.getNotifications()) {
            notification.setTriggers(new ArrayList<Trigger>(0));
        }
        alert.setTriggers(new ArrayList<Trigger>(0));
        alert.setModifiedBy(getRemoteUser(req));
        alertService.updateAlert(alert);
        return Response.status(Status.OK).build();
    }

    /**
     * deletes all triggers from notification.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         Alert Id. Cannot be null.
     * @param   notificationId  Notification Id. cannot be null.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}/triggers")
    @Description("Disassociates all triggers from the notification having the given notification ID.  The triggers are not deleted from the alert.")
    public Response deleteTriggersByNotificationId(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);
        Notification notification = null;

        if (alert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));
        for (Notification n : alert.getNotifications()) {
            if (n.getId().equals(notificationId)) {
                notification = n;
                break;
            }
        }
        if (notification == null) {
            throw new WebApplicationException("The notification does not exist for this alert", Status.BAD_REQUEST);
        }
        notification.setTriggers(new ArrayList<Trigger>(0));
        alertService.updateAlert(alert);
        return Response.status(Status.OK).build();
    }

    /**
     * Deletes the trigger from the notification.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   alertId         Alert Id. Cannot be null.
     * @param   notificationId  Notification Id. Cannot be null.
     * @param   triggerId       Trigger Id. Cannot be null.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/notifications/{notificationId}/triggers/{triggerId}")
    @Description("Disaccociates a trigger having the given ID from the given notification ID.  The trigger is not deleted from the alert.")
    public Response deleteTriggerInNotification(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("notificationId") BigInteger notificationId,
        @PathParam("triggerId") BigInteger triggerId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (notificationId == null || notificationId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerId == null || triggerId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Trigger Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Notification notification = null;
        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert == null) {
            throw new WebApplicationException(Status.NOT_FOUND.getReasonPhrase(), Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));
        for (Notification tempNotification : alert.getNotifications()) {
            if (tempNotification.getId().equals(notificationId)) {
                notification = tempNotification;
                break;
            }
        }
        if (notification == null) {
            throw new WebApplicationException("Notification Id does not exist for this alert.", Status.BAD_REQUEST);
        }

        List<Trigger> listTrigger = new ArrayList<Trigger>(notification.getTriggers());
        Iterator<Trigger> it = listTrigger.iterator();

        while (it.hasNext()) {
            Trigger tempTrigger = it.next();

            if (tempTrigger.getId().equals(triggerId)) {
                it.remove();
                notification.setTriggers(listTrigger);
                alert.setModifiedBy(getRemoteUser(req));
                alertService.updateAlert(alert);
                return Response.status(Status.OK).build();
            }
        }
        throw new WebApplicationException("This trigger does not exist for the notification.", Status.BAD_REQUEST);
    }

    /**
     * Deletes a trigger from alert.
     *
     * @param   req        The HttpServlet request object. Cannot be null.
     * @param   alertId    The alert Id. Cannot be null.
     * @param   triggerId  The trigger Id. Cannot be null.
     *
     * @return  Updated alert object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert or trigger do not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{alertId}/triggers/{triggerId}")
    @Description("Deletes a trigger having the given ID and removes any associations with the alert or notifications.")
    public Response deleteTriggersById(@Context HttpServletRequest req,
        @PathParam("alertId") BigInteger alertId,
        @PathParam("triggerId") BigInteger triggerId) {
        if (alertId == null || alertId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (triggerId == null || triggerId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Trigger Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert = alertService.findAlertByPrimaryKey(alertId);

        if (alert == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));

        List<Trigger> listTrigger = new ArrayList<Trigger>(alert.getTriggers());
        Iterator<Trigger> itTrigger = listTrigger.iterator();

        while (itTrigger.hasNext()) {
            Trigger trigger = itTrigger.next();

            if (triggerId.equals(trigger.getId())) {
                itTrigger.remove();
                alert.setTriggers(listTrigger);
                alertService.updateAlert(alert);
                return Response.status(Status.OK).build();
            }
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
