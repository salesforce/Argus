package com.salesforce.dva.warden.dto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Policy Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Policy extends com.salesforce.dva.warden.dto.Entity {
	//~ Instance fields ******************************************************************************************************************************

	    private String servcie;		
	    private String name;
	    private List<String> owner  = new ArrayList<String>();
	    private List<String> user  = new ArrayList<String>();
	    private String subSystem;
	    private String metricName;
	    private String triggerType;
	    private String aggregator;
	    private List<Double> threshold;	    
	    private String timeUnit;
	    private double defaultValue;
	    private String cronEntry;
	    private List<BigInteger> suspensionLevels = new ArrayList<BigInteger>();

		public String getServcie() {
			return servcie;
		}

		public void setService(String service) {
			this.servcie = service;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public List<String> getOwner() {
			return owner;
		}

		public void setOwner(List<String> owner) {
			this.owner = owner;
		}

		public List<String> getUser() {
			return user;
		}

		public void setUser(List<String> user) {
			this.user = user;
		}

		public String getSubSystem() {
			return subSystem;
		}

		public void setSubSystem(String subSystem) {
			this.subSystem = subSystem;
		}

		public String getMetricName() {
			return metricName;
		}

		public void setMetricName(String metricName) {
			this.metricName = metricName;
		}

		public String getTriggerType() {
			return triggerType;
		}

		public void setTriggerType(String triggerType) {
			this.triggerType = triggerType;
		}

		public String getAggregator() {
			return aggregator;
		}

		public void setAggregator(String aggregator) {
			this.aggregator = aggregator;
		}

		public List<Double> getThreshold() {
			return threshold;
		}

		public void setThreshold(List<Double> threshold) {
			this.threshold = threshold;
		}

		public String getTimeUnit() {
			return timeUnit;
		}

		public void setTimeUnit(String timeUnit) {
			this.timeUnit = timeUnit;
		}

		public double getDefaultValue() {
			return defaultValue;
		}

		public void setDefaultValue(double defaultValue) {
			this.defaultValue = defaultValue;
		}

		public String getCronEntry() {
			return cronEntry;
		}

		public void setCronEntry(String cronEntry) {
			this.cronEntry = cronEntry;
		}

		public List<BigInteger> getSuspensionLevels() {
			return suspensionLevels;
		}
		
		public void setSuspensionLevels(List<BigInteger> suspensionLevels) {
			 this.suspensionLevels = suspensionLevels;
		}
		
		@Override
		public Object createExample() {
			Policy result = new Policy();
			
			result.setId(BigInteger.ONE);
			result.setCreatedById(BigInteger.ONE);
			result.setCreatedDate(new Date());
			result.setModifiedById(BigInteger.TEN);
			result.setModifiedDate(new Date());
			
			result.setService("example-service");
			result.setName("example-name");
			result.setOwner(Arrays.asList("example-owner"));
			result.setUser(Arrays.asList("example-user"));
			result.setSubSystem("example-subSystem");
			result.setMetricName("example-metricName");
			result.setTriggerType("NOT_BETWEEN");
			result.setAggregator("sum");
			result.setThreshold(Arrays.asList(0.0));
			result.setTimeUnit("5min");
			result.setDefaultValue(0.0);
			result.setCronEntry("0 */4 * * *");
			
			return result;
		}
}