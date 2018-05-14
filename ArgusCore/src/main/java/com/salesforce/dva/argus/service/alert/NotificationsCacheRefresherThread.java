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

public class NotificationsCacheRefresherThread extends Thread{

	private final Logger _logger = LoggerFactory.getLogger(NotificationsCacheRefresherThread.class);
	
	private static final Long SLEEP_INTERVAL_MILLIS = 60*1000L;
	
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
				sleep(SLEEP_INTERVAL_MILLIS);
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
