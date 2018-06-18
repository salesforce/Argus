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
import java.util.Map;

import javax.persistence.EntityManager;

import com.google.inject.Provider;

/*
 * This class maintains a cache which has the latest status of notification properties like cool down and active trigger status. 
 * 
 * The cache is kept up to date by a refresher thread which updates the cache periodically
 */
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
