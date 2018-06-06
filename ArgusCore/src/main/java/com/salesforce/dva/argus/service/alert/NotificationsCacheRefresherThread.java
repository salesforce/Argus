/*
 * Copyright (c) 2018, Salesforce.com, Inc.
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
package com.salesforce.dva.argus.service.alert;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provider;

/* 
 * This thread refreshes the notifications cache periodically
 */
public class NotificationsCacheRefresherThread extends Thread{

	private final Logger _logger = LoggerFactory.getLogger(NotificationsCacheRefresherThread.class);
	
	// keeping the refresh interval at 1 minute, as this corresponds to the minimum alert execution interval based on cron expression
	private static final Long REFRESH_INTERVAL_MILLIS = 60*1000L;
	
	private NotificationsCache notificationsCache = null;
	
	private Provider<EntityManager> _emProvider;
	
	public NotificationsCacheRefresherThread(NotificationsCache cache, Provider<EntityManager> em) {
        this.notificationsCache = cache;
        this._emProvider = em;
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				sleep(REFRESH_INTERVAL_MILLIS);
				_logger.info("Starting notifications cache refresh");
	
				EntityManager em = _emProvider.get();
				
				// populating notifications cooldown cache
				Query q = em.createNativeQuery("select * from notification_cooldownexpirationbytriggerandmetric");
				List<Object[]> objects = q.getResultList();
				Map<BigInteger/*notificationId*/, Map<String/*metricKey*/, Long/*coolDownExpiration*/>> currNotificationCooldownExpirationMap = new HashMap<BigInteger, Map<String, Long>>();

				for(Object[] object : objects) {
					BigInteger notificationId = new BigInteger(String.valueOf(Long.class.cast(object[0])));
					Long cooldownExpiration = Long.class.cast(object[1]);
					String key = String.class.cast(object[2]);
					if(currNotificationCooldownExpirationMap.get(notificationId)==null) {
						currNotificationCooldownExpirationMap.put(notificationId, new HashMap<String, Long>());
					}
					currNotificationCooldownExpirationMap.get(notificationId).put(key, cooldownExpiration);
				}
				notificationsCache.setNotificationCooldownExpirationMap(currNotificationCooldownExpirationMap);

				// populating the active status cache
				q = em.createNativeQuery("select * from notification_activestatusbytriggerandmetric");
				objects = q.getResultList();
				Map<BigInteger/*notificationId*/, Map<String/*metricKey*/, Boolean/*activeStatus*/>> currNotificationActiveStatusMap = new HashMap<BigInteger, Map<String, Boolean>>();

				for(Object[] object : objects) {
					BigInteger notificationId = new BigInteger(String.valueOf(Long.class.cast(object[0])));
					Boolean isActive;
					try {
						isActive = Boolean.class.cast(object[1]);
					} catch (ClassCastException e) {
						// This is because Embedded Derby stores booleans as 0, 1.
						isActive = Integer.class.cast(object[1]) == 0 ? Boolean.FALSE : Boolean.TRUE;
					}
					String key = String.class.cast(object[2]);
					if(currNotificationActiveStatusMap.get(notificationId)==null) {
						currNotificationActiveStatusMap.put(notificationId, new HashMap<String, Boolean>());
					}
					currNotificationActiveStatusMap.get(notificationId).put(key, isActive);
				}
				notificationsCache.setNotificationActiveStatusMap(currNotificationActiveStatusMap);
				
				notificationsCache.setNotificationsCacheRefreshed(true);
				_logger.info("Notifications cache refresh successful.");
			}catch(Exception e) {
				_logger.error("Exception occured when trying to refresh notifications cache - " + ExceptionUtils.getFullStackTrace(e));
				notificationsCache.setNotificationsCacheRefreshed(false);
			}
		}
	}
}
