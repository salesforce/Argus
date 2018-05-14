package com.salesforce.dva.argus.service.alert;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;

import com.google.inject.Provider;

public class NotificationsCache {
	
	private NotificationsCacheRefresherThread refresherThread;

	private Map<BigInteger/*notificationId*/, Map<String/*metricKey*/, Long/*coolDownExpiration*/>> notificationCooldownExpirationMap = new HashMap<BigInteger, Map<String, Long>>();

	private Map<BigInteger/*notificationId*/, Map<String/*metricKey*/, Boolean/*activeStatus*/>> notificationActiveStatusMap = new HashMap<BigInteger, Map<String, Boolean>>();

	private boolean isNotificationsCacheRefreshed = false;
	
	public NotificationsCache(Provider<EntityManager> em) {
		refresherThread = new NotificationsCacheRefresherThread(this, em);
		refresherThread.setDaemon(true);
		refresherThread.start();
	}

	public Map<BigInteger, Map<String, Long>> getNotificationCooldownExpirationMap() {
		return notificationCooldownExpirationMap;
	}

	public void setNotificationCooldownExpirationMap(Map<BigInteger, Map<String, Long>> notificationCooldownExpirationMap) {
		this.notificationCooldownExpirationMap = notificationCooldownExpirationMap;
	}

	public Map<BigInteger, Map<String, Boolean>> getNotificationActiveStatusMap() {
		return notificationActiveStatusMap;
	}

	public void setNotificationActiveStatusMap(Map<BigInteger, Map<String, Boolean>> notificationActiveStatusMap) {
		this.notificationActiveStatusMap = notificationActiveStatusMap;
	}
	
	public boolean isNotificationsCacheRefreshed() {
		return isNotificationsCacheRefreshed;
	}

	public void setNotificationsCacheRefreshed(boolean isNotificationsCacheRefreshed) {
		this.isNotificationsCacheRefreshed = isNotificationsCacheRefreshed;
	}
}
