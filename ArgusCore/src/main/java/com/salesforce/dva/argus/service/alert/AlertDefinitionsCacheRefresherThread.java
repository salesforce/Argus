package com.salesforce.dva.argus.service.alert;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.service.AlertService;

public class AlertDefinitionsCacheRefresherThread extends Thread{

	private final Logger _logger = LoggerFactory.getLogger(AlertDefinitionsCacheRefresherThread.class);

	// keeping the refresh interval at 1 minute, as this corresponds to the minimum alert execution interval based on cron expression
	private static final Long REFRESH_INTERVAL_MILLIS = 60*1000L;

	private static final Long LOOKBACK_PERIOD_FOR_REFRESH_MILLIS = 2*REFRESH_INTERVAL_MILLIS;

	private AlertDefinitionsCache alertDefinitionsCache = null;

	private AlertService alertService;

	public AlertDefinitionsCacheRefresherThread(AlertDefinitionsCache cache, AlertService alertService) {
		this.alertDefinitionsCache = cache;
		this.alertService = alertService;
	}

	public void run() {
		while (!isInterrupted()) {
			long executionTime = 0L;
			try {
				_logger.info("Starting alert definitions cache refresh");
				long startTime = System.currentTimeMillis();
				if(!alertDefinitionsCache.isAlertsCacheInitialized()) {
					List<Alert> enabledAlerts = alertService.findAlertsByStatus(true);
					Map<BigInteger, Alert> enabledAlertsMap = enabledAlerts.stream().collect(Collectors.toMap(alert -> alert.getId(), alert -> alert));
					Map<String/*cronEnty*/, List<BigInteger>> alertsByCronEntry = new HashMap<String, List<BigInteger>>();
					for(Alert a : enabledAlerts) {
						addEntrytoCronMap(a);
					}
					alertDefinitionsCache.setAlertsMapByCronEntry(alertsByCronEntry);
					alertDefinitionsCache.setAlertsMapById(enabledAlertsMap);
					alertDefinitionsCache.setAlertsCacheInitialized(true);
				}else {
					List<Alert> modifiedAlerts = alertService.findAlertsModifiedAfterDate(new Date(startTime - Math.max(executionTime, LOOKBACK_PERIOD_FOR_REFRESH_MILLIS)));

					// updating only the modified/deleted alerts in the cache
					if(modifiedAlerts!=null && modifiedAlerts.size()>0) {
						for(Alert a : modifiedAlerts) {
							if(alertDefinitionsCache.getAlertsMapById().containsKey(a.getId())) {
								Alert prevAlert = alertDefinitionsCache.getAlertsMapById().get(a.getId());
								if(a.isDeleted() || !a.isEnabled()) {
									alertDefinitionsCache.getAlertsMapById().remove(a.getId());  
									alertDefinitionsCache.getAlertsMapByCronEntry().get(a.getCronEntry()).remove(a.getId());
								}else {
									alertDefinitionsCache.getAlertsMapById().put(a.getId(), a);                	 
								}

								if(!a.getCronEntry().equals(prevAlert.getCronEntry())) {
									alertDefinitionsCache.getAlertsMapByCronEntry().get(prevAlert.getCronEntry()).remove(a.getId());
									if(!a.isDeleted() && a.isEnabled()) {
										addEntrytoCronMap(a);
									}
								}   
							}else if(a.isEnabled() && !a.isDeleted()) {
								alertDefinitionsCache.getAlertsMapById().put(a.getId(), a);
								addEntrytoCronMap(a);
							}
						}
					}
					_logger.info("Number of modified alerts since last refresh - " + modifiedAlerts.size());
				}
				executionTime = System.currentTimeMillis() - startTime;
				_logger.info("Alerts cache refreshed successfully in {} millis", executionTime);
				if(executionTime < REFRESH_INTERVAL_MILLIS) {
					sleep(REFRESH_INTERVAL_MILLIS - executionTime);
				}
			}catch(Exception e) {
				_logger.error("Exception occured when trying to refresh alert definition cache - " + ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private void addEntrytoCronMap(Alert a) {
		if(alertDefinitionsCache.getAlertsMapByCronEntry().get(a.getCronEntry())==null) {
			alertDefinitionsCache.getAlertsMapByCronEntry().put(a.getCronEntry(), new ArrayList<BigInteger>());
		}
		alertDefinitionsCache.getAlertsMapByCronEntry().get(a.getCronEntry()).add(a.getId());
	}
}
