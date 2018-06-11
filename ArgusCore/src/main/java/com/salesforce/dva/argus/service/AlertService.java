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

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.*;
import com.salesforce.dva.argus.service.warden.WardenApiNotifier;
import com.salesforce.dva.argus.service.warden.WardenPostingNotifier;

import java.math.BigInteger;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Provides methods to create, update and delete alerts.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj sarkapally (rsarkapally@salesforce.com)
 */
public interface AlertService extends Service {

	//~ Methods **************************************************************************************************************************************

	/**
	 * Creates or updates an alert. This method cascades operations to the alert triggers and notifications.
	 *
	 * @param   alert  The alert to create or update. Cannot be null.
	 *
	 * @return  The updated alert.
	 */
	Alert updateAlert(Alert alert);

	/**
	 * Deletes an alert. This method cascades operations to the alert triggers and notifications.
	 *
	 * @param  name   The name of the alert. Cannot be null or empty.
	 * @param  owner  The owner of the alert. Cannot be null.
	 */
	void deleteAlert(String name, PrincipalUser owner);

	/**
	 * Marks the alert for deletion. Subsequent reads for this alert would result in null entity. All alerts marked for deletion will be
	 * periodically/eventually removed from the system.
	 *
	 * @param  name   The name of the alert. Cannot be null or empty.
	 * @param  owner  The owner of the alert. Cannot be null.
	 */
	void markAlertForDeletion(String name, PrincipalUser owner);

	/**
	 * Marks the given alert for deletion. Subsequent reads for this alert would result in null entity. All alerts marked for deletion will be
	 * periodically/eventually removed from the system.
	 *
	 * @param  alert  The alert to mark for deletion. Cannot be null.
	 */
	void markAlertForDeletion(Alert alert);

	/**
	 * Deletes a trigger from the alert.
	 *
	 * @param  trigger  The trigger to delete.
	 */
	void deleteTrigger(Trigger trigger);

	/**
	 * Deletes a notification from the alert.
	 *
	 * @param  notification  The notification to delete.
	 */
	void deleteNotification(Notification notification);

	/**
	 * Deletes an alert. This method cascades operations to the alert triggers and notifications.
	 *
	 * @param  alert  The alert to delete. Cannot be null.
	 */
	void deleteAlert(Alert alert);

	/**
	 * Dequeues scheduled alerts from the alert evaluation queue and evaluates the triggers sending notifications if required. The number of alerts
	 * evaluated will be determined by the number of alerts dequeued within the timeout period, not to exceed the specified alert count.
	 *
	 * @param   alertCount  The maximum number of alerts to dequeue.
	 * @param   timeout     The maximum amount of time in milliseconds to attempt to dequeue alerts.
	 *
	 * @return  returns Job history of alerts executed.
	 */
	List<History> executeScheduledAlerts(int alertCount, int timeout);

	/**
	 * Enqueues alerts to be executed by the next available alert client.
	 *
	 * @param  alerts  The alerts to enqueue. Cannot be null, but may be empty.
	 */
	void enqueueAlerts(List<Alert> alerts);

	/**
	 * Returns a list of alerts for an owner.
	 *
	 * @param   owner  The owner to return alerts for. Cannot be null.
	 *
	 * @return  The list of alerts. Will never be null, but may be empty.
	 */
	List<Alert> findAlertsByOwner(PrincipalUser owner, boolean metadataOnly);

	/**
	 * Returns a list of alerts that have been marked for deletion.
	 *
	 * @return  The list of alerts marked for deletion.
	 */
	List<Alert> findAlertsMarkedForDeletion();

	/**
	 * Returns a list of alerts that have been marked for deletion.
	 * 
	 * @param	limit  The maximum number of such alerts to find. Must be greater than 0. 
	 *
	 * @return  The list of alerts marked for deletion.
	 */
	List<Alert> findAlertsMarkedForDeletion(int limit);

	/**
	 * Finds an alert by its primary key.
	 *
	 * @param   id  The primary key for the alert. Cannot be null and must be a positive non-zero number.
	 *
	 * @return  The alert or null if no alert exists for the primary key.
	 */
	Alert findAlertByPrimaryKey(BigInteger id);

	/**
	 * Finds alerts for the given list of primary keys.
	 *
	 * @param   ids  The primary keys for the alerts to find. Cannot be null or empty and must be a positive non-zero number.
	 *
	 * @return  The list of alerts or an empty list if no alerts exist for the given primary keys.
	 */
	List<Alert> findAlertsByPrimaryKeys(List<BigInteger> ids);

	/**
	 * Finds an alert by its name and owner.
	 *
	 * @param   name   The name of the alert. Cannot be null or empty.
	 * @param   owner  The owner of the alert. Cannot be null.
	 *
	 * @return  The alert or null if no alert having the given name exists for the owner.
	 */
	Alert findAlertByNameAndOwner(String name, PrincipalUser owner);

	/**
	 * Returns a list of all alerts.
	 *
	 * @return  The list of all alerts. Will never be null, but may be empty.
	 */
	List<Alert> findAllAlerts(boolean metadataOnly);

	/**
	 * Returns a list of alerts by status (enabled alerts or disabled alerts).
	 *
	 * @param   enabled  Alert status (true for enabled alerts and false for disabled alerts)
	 *
	 * @return  The list of alerts for the given status. Will never be null but may be empty.
	 */
	List<Alert> findAlertsByStatus(boolean enabled);

	/**
	 * Returns a list of alert ids by status (enabled alert ids or disabled alert ids).
	 *
	 * @param   enabled  Alert status (true for enabled alerts and false for disabled alerts)
	 *
	 * @return  The list of alert ids for the given status. Will never be null but may be empty.
	 */
	List<BigInteger> findAlertIdsByStatus(boolean enabled);

    /**
     * Returns a list of alerts that are in the range of ids and also filtered by status (enabled alerts or disabled alerts).
     *
     * @param   fromId - starting alert id in range
     *
     * @param   toId  - ending alert id in range
     * 
     * @param   enabled  Alert status (true for enabled alerts and false for disabled alerts)
     *
     * @return  The list of alerts for the given status. Will never be null but may be empty.
     */
    List<Alert> findAlertsByRangeAndStatus(BigInteger fromId, BigInteger toId, boolean enabled);
	
    /**
     * Returns a list of alerts that are created/modified/deleted after the specified date
     *
     * @param   modifiedDate - modifiedDate of alert object
     *
     * @return  The list of alerts modified after the input date
     */
	List<Alert> findAlertsModifiedAfterDate(Date modifiedDate);
	
    /**
	 * Returns the total count of alerts by status (enabled alerts or disabled alerts).
	 *
	 * @param   enabled  Alert status (true for enabled alerts and false for disabled alerts)
	 *
	 * @return  Alert count
	 */
	int alertCountByStatus(boolean enabled);

	/**
	 * Returns a list of alerts by status (enabled alerts or disabled alerts), from a given offset.
	 *
	 * @param   limit    Number of alerts to fetch
	 * @param   offset   Position from where to start fetching the alerts
	 * @param   enabled  Alert status (true for enabled alerts and false for disabled alerts)
	 *
	 * @return  The list of alerts for the given status. Will never be null but may be empty.
	 */
	List<Alert> findAlertsByLimitOffsetStatus(int limit, int offset, boolean enabled);

	/**
	 * Returns a list of alerts whose name start with prefix.
	 *
	 * @param   prefix  The prefix to find alerts by
	 *
	 * @return  The list of alerts starting with given prefix. Will neverl be null but may be empty.
	 */
	List<Alert> findAlertsByNameWithPrefix(String prefix);

	/**
	 * Returns a list of shared alerts.
	 * @param   metadataOnly    Get metadata only
	 * @param   owner           The owner of shared alerts to filter on. If null no filtering applied
	 * @param   limit           The maximum number of rows to return. If null no filtering applied
	 * 
	 * @return  The list of all alerts. Will never be null, but may be empty.
	 */
	List<Alert> findSharedAlerts(boolean metadataOnly, PrincipalUser owner, Integer limit);

	/**
	 * Returns the list of supported notifiers.
	 *
	 * @return  The list of supported notifiers. Cannot be null, but may be empty.
	 */
	List<String> getSupportedNotifiers();

	/**
	 * Returns the notifier instance corresponding to the supported notifier type.
	 *
	 * @param   notifier  The supported notifier type.
	 *
	 * @return  The notifier instance.
	 */
	Notifier getNotifier(SupportedNotifier notifier);
	
	/**
	 * Update activeStatusByTriggerAndMetric and cooldownExpirationByTriggerAndMetric for the given notifications.
	 * 
	 * @param notifications  The notifications to update.	
	 */
	void updateNotificationsActiveStatusAndCooldown(List<Notification> notifications);

	
	//~ Enums ****************************************************************************************************************************************

	/**
	 * Describes the list of supported notifiers.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 */
	enum SupportedNotifier {

		DATABASE(AuditNotifier.class.getName()),
		EMAIL(EmailNotifier.class.getName()),
		GOC(GOCNotifier.class.getName()),
		WARDENAPI(WardenApiNotifier.class.getName()),
		WARDENPOSTING(WardenPostingNotifier.class.getName()),
		GUS(GusNotifier.class.getName()),
		CALLBACK(CallbackNotifier.class.getName());

		String name;

		/**
		 * Creates a new SupportedNotifier object.
		 *
		 * @param  name  Name of the notifier implementation class.
		 */
		SupportedNotifier(String name) {
			this.name = name;
		}

		/**
		 * Returns a notifier for the supported notifier class name.
		 *
		 * @param   notifierClassName  The class name.
		 *
		 * @return  The supported notifier or null if no corresponding notifier is found.
		 */
		public static SupportedNotifier fromClassName(String notifierClassName) {
			for (SupportedNotifier n : SupportedNotifier.values()) {
				if (n.getName().equalsIgnoreCase(notifierClassName)) {
					return n;
				}
			}

			throw new IllegalArgumentException("No such notifier class: " + notifierClassName);
		}

		/**
		 * returns name of the notifier implementation class.
		 *
		 * @return  Name of the notifier implementation class.
		 */
		public String getName() {
			return name;
		}
	}

	//~ Inner Interfaces *****************************************************************************************************************************

	/**
	 * Implementations of this interface are used to notify subscribers of alert conditions.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 */
	public static interface Notifier {

		/**
		 * Sends notifications for the trigger on which the alert condition occurred.
		 *
		 * @param  notificationContext  The context for the notification. Cannot be null.
		 */
		void sendNotification(NotificationContext notificationContext);

		/**
		 * Clears notifications for the trigger on which the alert condition occurred.
		 *
		 * @param  notificationContext  The context for the notification. Cannot be null.
		 */
		void clearNotification(NotificationContext notificationContext);

		/**
		 * Returns the name of the notifier.
		 *
		 * @return  The notifier name.  Will never return null.
		 */
		String getName();

		/**
		 * Indicates the status of a notification.
		 *
		 * @author  Tom Valine (tvaline@salesforce.com)
		 */
		public enum NotificationStatus {

			/** Indicates a notification for a triggering condition. */
			TRIGGERED,
			/** Indicates a notification for when a triggering condition is cleared. */
			CLEARED;
		}

		/**
		 * Get notifier specific configuration property names and  defaults so that they may be merged with the system level configuration properties.
		 * Notifier specific implementations of this method should return a properties object which contains the name of the property and the default 
		 * value.  All values of the properties should be null.
		 * <p>
		 * <b>Implementations of this method must always complete successfully and should never throw an exception.</b>
		 * </p>
		 * 
		 * @return The notifier specific configuration property names and defaults required to configure the service.
		 */
		Properties getNotifierProperties();
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */