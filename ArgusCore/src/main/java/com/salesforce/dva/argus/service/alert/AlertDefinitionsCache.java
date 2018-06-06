package com.salesforce.dva.argus.service.alert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.service.AlertService;

public class AlertDefinitionsCache {

	private final Logger _logger = LoggerFactory.getLogger(AlertDefinitionsCache.class);
	
	private AlertDefinitionsCacheRefresherThread refresherThread;

	private Map<BigInteger/*alertId*/, Alert> alertsMapById = new HashMap<BigInteger, Alert>();

	private Map<String/*cronEntry*/, List<BigInteger/*alertId*/>> alertsMapByCronEntry = new HashMap<String, List<BigInteger>>();
	
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

	public List<Alert> getEnabledAlertsForMinute(long minuteStartTimeMillis){
		List<Alert> enabledAlerts = new ArrayList<Alert>();
		List<BigInteger> enabledAlertIds = new ArrayList<BigInteger>();

		for(String cronEntry : alertsMapByCronEntry.keySet()) {
			try {
				String quartzCronEntry = "0 " + cronEntry.substring(0, cronEntry.length() - 1) + "?";
				_logger.info("Parsing cron entry - " + quartzCronEntry);
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
			enabledAlerts.add(alertsMapById.get(alertId));
		}
		return enabledAlerts;
	}

}
