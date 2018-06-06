package com.salesforce.dva.argus.service.alert;

import java.math.BigInteger;
import java.util.ArrayList;
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

	private AlertDefinitionsCache alertDefinitionsCache = null;

	private AlertService alertService;

	public AlertDefinitionsCacheRefresherThread(AlertDefinitionsCache cache, AlertService alertService) {
		this.alertDefinitionsCache = cache;
		this.alertService = alertService;
	}

	public void run() {
		while (!isInterrupted()) {
			try {
				_logger.info("Starting alert definitions cache refresh");
				long startTime = System.currentTimeMillis();
				List<Alert> enabledAlerts = alertService.findAlertsByStatus(true);
				Map<BigInteger, Alert> enabledAlertsMap = enabledAlerts.stream().collect(Collectors.toMap(alert -> alert.getId(), alert -> alert));
				Map<String/*cronEnty*/, List<BigInteger>> alertsByCronEntry = new HashMap<String, List<BigInteger>>();
				for(Alert a : enabledAlerts) {
					if(alertsByCronEntry.get(a.getCronEntry())==null) {
						alertsByCronEntry.put(a.getCronEntry(), new ArrayList<BigInteger>());
					}
					alertsByCronEntry.get(a.getCronEntry()).add(a.getId());
				}
				alertDefinitionsCache.setAlertsMapByCronEntry(alertsByCronEntry);
				alertDefinitionsCache.setAlertsMapById(enabledAlertsMap);
				if(!alertDefinitionsCache.isAlertsCacheInitialized()) {
					alertDefinitionsCache.setAlertsCacheInitialized(true);
				}
				long executionTime = System.currentTimeMillis() - startTime;
				_logger.info("Alerts cache refreshed successfully in {} millis", executionTime);
				if(executionTime < REFRESH_INTERVAL_MILLIS) {
				    sleep(REFRESH_INTERVAL_MILLIS - executionTime);
				}
			}catch(Exception e) {
				_logger.error("Exception occured when trying to refresh alert definition cache - " + ExceptionUtils.getFullStackTrace(e));
			}
		}

	}
}
