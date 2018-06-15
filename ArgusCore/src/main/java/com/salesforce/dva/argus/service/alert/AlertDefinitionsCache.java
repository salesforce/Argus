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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.util.Cron;

public class AlertDefinitionsCache {

	private static final Logger _logger = LoggerFactory.getLogger(AlertDefinitionsCache.class);
	
	private AlertDefinitionsCacheRefresherThread refresherThread;

	private static Map<BigInteger/*alertId*/, Alert> alertsMapById = new ConcurrentHashMap<BigInteger, Alert>();

	private static Map<String/*cronEntry*/, List<BigInteger/*alertId*/>> alertsMapByCronEntry = new ConcurrentHashMap<String, List<BigInteger>>();
	
	private boolean alertsCacheInitialized = false;

	public AlertDefinitionsCache(AlertService alertService) {
		refresherThread = new AlertDefinitionsCacheRefresherThread(this, alertService);
		refresherThread.setDaemon(true);
		refresherThread.start();
	}

	public Map<BigInteger, Alert> getAlertsMapById() {
		return alertsMapById;
	}

	public void setAlertsMapById(Map<BigInteger, Alert> alertsMapById) {
		this.alertsMapById = alertsMapById;
	}

	public Map<String, List<BigInteger>> getAlertsMapByCronEntry() {
		return alertsMapByCronEntry;
	}

	public void setAlertsMapByCronEntry(Map<String, List<BigInteger>> alertsMapByCronEntry) {
		this.alertsMapByCronEntry = alertsMapByCronEntry;
	}
	
	public boolean isAlertsCacheInitialized() {
		return alertsCacheInitialized;
	}

	public void setAlertsCacheInitialized(boolean alertsCacheInitialized) {
		this.alertsCacheInitialized = alertsCacheInitialized;
	}

	public static List<Alert> getEnabledAlertsForMinute(long minuteStartTimeMillis){
		List<Alert> enabledAlerts = new ArrayList<Alert>();
		List<BigInteger> enabledAlertIds = new ArrayList<BigInteger>();

		for(String cronEntry : alertsMapByCronEntry.keySet()) {
			try {
				String quartzCronEntry = Cron.convertToQuartzCronEntry(cronEntry);
				CronTrigger cronTrigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(quartzCronEntry)).build();
				Date nextFireTime = cronTrigger.getFireTimeAfter(new Date(minuteStartTimeMillis-1000));
				if(nextFireTime.equals(new Date(minuteStartTimeMillis))) {
					enabledAlertIds.addAll(alertsMapByCronEntry.get(cronEntry));
				}
			}catch(Exception e) {
                _logger.error("Exception occured when trying to parse cron entry - " + cronEntry + " Exception - "+ ExceptionUtils.getFullStackTrace(e));
			}
		}
		Collections.sort(enabledAlertIds);
		for(BigInteger alertId : enabledAlertIds) {
			Alert a = alertsMapById.get(alertId);
			if(a!=null) {
			    enabledAlerts.add(alertsMapById.get(alertId));
			}
		}
		return enabledAlerts;
	}

}
