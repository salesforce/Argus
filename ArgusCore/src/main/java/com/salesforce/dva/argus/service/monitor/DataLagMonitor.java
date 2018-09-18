package com.salesforce.dva.argus.service.monitor;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/*
 * This class runs a thread which periodically checks if there is data lag on Argus side.
 * 
 */
public class DataLagMonitor extends Thread{

	private String _dataLagQueryExpression;

	private long _dataLagThreshold;

	private String _dataLagNotificationEmailId;

	private String _hostName;

	private boolean isDataLagging = false;

	private MetricService _metricService;

	private MailService _mailService;

	private static final Long SLEEP_INTERVAL_MILLIS = 60*1000L;

	private final Logger _logger = LoggerFactory.getLogger(DataLagMonitor.class);

	public DataLagMonitor(SystemConfiguration sysConfig, MetricService metricService, MailService mailService) {
		_metricService = metricService;
		_mailService = mailService;
		_dataLagQueryExpression = sysConfig.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DATA_LAG_QUERY_EXPRESSION);
		_dataLagThreshold = Long.valueOf(sysConfig.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DATA_LAG_THRESHOLD));
		_dataLagNotificationEmailId = sysConfig.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DATA_LAG_NOTIFICATION_EMAIL_ADDRESS);
		_hostName = sysConfig.getHostname();
		_logger.info("Data lag monitor initialized");
	}

	@Override
	public void run() {
		_logger.info("Data lag monitor thread started");
		boolean firstTime = true;
		while (!isInterrupted()) {
			try {
				if(!firstTime) {
				    sleep(SLEEP_INTERVAL_MILLIS);
				}else {
					// waiting 5 seconds for everything to initialize
					sleep(5*1000);
					firstTime = false;
				}
				long currTime = System.currentTimeMillis();
				List<Metric> metrics = _metricService.getMetrics(_dataLagQueryExpression, currTime);
				if(metrics==null || metrics.isEmpty()) {
					_logger.info("Data lag detected as metric list is empty");
					if(!isDataLagging) {
						isDataLagging=true;
						sendDataLagEmailNotification();
					}
					continue;
				}

				//assuming only one time series in result
				Metric currMetric = metrics.get(0);
				if(currMetric.getDatapoints()==null || currMetric.getDatapoints().size()==0) {
					_logger.info("Data lag detected as data point list is empty");
					if(!isDataLagging) {
						isDataLagging=true;
						sendDataLagEmailNotification();
					}
					continue;
				}else {
					long lastDataPointTime = 0L;
					for(Long dataPointTime : currMetric.getDatapoints().keySet()) {
						if(dataPointTime > lastDataPointTime) {
							lastDataPointTime = dataPointTime;
						}
					}
					if((currTime - lastDataPointTime)> _dataLagThreshold) {
						_logger.info("Data lag detected as the last data point recieved is more than the data threshold of " + _dataLagThreshold + " ms");
						if(!isDataLagging) {
							isDataLagging=true;
							sendDataLagEmailNotification();
						}
						continue;
					}
				}
				if(isDataLagging) {
					isDataLagging = false;
					sendDataLagEmailNotification();
				}
			}catch(Exception e) {
				_logger.error("Exception thrown in data lag monitor thread - " + ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private void sendDataLagEmailNotification() {
		Set<String> emailAddresseses = new HashSet<String>();
		emailAddresseses.add(_dataLagNotificationEmailId);
		String subject = "";
		if(isDataLagging) {
			subject = "Alert evaluation on host - "+ _hostName + " has been stopped due to metric data lag";
		}else {
			subject = "Alert evaluation on host - "+ _hostName + " has been resumed as the metric data lag has cleared";
		}
		
		StringBuilder body = new StringBuilder();
		body.append(MessageFormat.format("<b>Evaluated metric expression:  </b> {0}<br/>", _dataLagQueryExpression));
        body.append(MessageFormat.format("<b>Configured data lag threshold:  </b> {0}<br/>", _dataLagThreshold));
		
		_mailService.sendMessage(emailAddresseses, subject, body.toString(), "text/html; charset=utf-8", MailService.Priority.NORMAL);
	}

	public boolean isDataLagging() {
		return isDataLagging;
	}
}
