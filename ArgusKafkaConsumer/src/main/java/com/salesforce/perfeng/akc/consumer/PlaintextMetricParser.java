package com.salesforce.perfeng.akc.consumer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.salesforce.mandm.ajna.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaintextMetricParser {
	private static final Logger LOGGER = LoggerFactory.getLogger(PlaintextMetricParser.class);
	public static final String METRIC_CONTEXT_DELIMITER = "\\.";
	// static final Pattern lineRegex = Pattern.compile("(?<name>\\S+)\\s(?<value>-?\\d+(?:\\.\\d+(?:[eE]-?\\d+)?)?)\\s(?<timestamp>\\d+)");
	private static final Pattern patternRegex = Pattern.compile("(?<name>\\S+)\\s(?<value>-?\\d+(?:\\.\\d+(?:[eE]-?\\d+)?)?)\\s(?<timestamp>\\d+)");
	private static final String DEVICE_REPLACE_NAME_FROM="-ops-sfdc-net";
	private static final String DEVICE_REPLACE_NAME_TO=".ops.sfdc.net";

	/*
	 * The parameter data looks like
	 * 	core.CHI.SP1.na9.na9-ffx1-10-chi-ops-sfdc-net.java-lang_type-GarbageCollector-name-PS_Scavenge.LastGcInfo_startTime 1190454441 1472082139
	 */
	public Metric parse(byte[] data){

		Metric result = null;
		Matcher matcher = patternRegex.matcher(new String(data));

		try{
			if(matcher.matches()){
			String metricName = matcher.group("name");
			 Double value = Double.parseDouble(matcher.group("value"));
             Long timestamp = Long.parseLong(matcher.group("timestamp"));
             result=parseMetricName(metricName);
             if(result != null){
	             result.setMetricValue(value);
	             result.setTimestamp(timestamp);
             }
			}
		}catch(Throwable e){
			LOGGER.error("Error occured while parsing ULI metric: " + Arrays.toString(data) + "Reason: " + e.getMessage());
		}
		return result;
	}

	private Metric parseMetricName(String metricName){
		Metric result = null;

		if(metricName != null && !metricName.isEmpty()){
			String[] names = metricName.split(METRIC_CONTEXT_DELIMITER,6);
			if(names.length==6){
				String metricname=names[5];
				Map<CharSequence, CharSequence> tags=new HashMap<>();
				tags.put("datacenter", names[1]);
				tags.put("superpod", names[2]);
				tags.put("pod", names[3]);
				tags.put("device", replaceDeviceNameWithDot(names[4]));
				result=new Metric();
				result.setService("uli_"+ names[0]);
				result.setTags(tags);
				result.setMetricName(Arrays.asList(metricname.split(METRIC_CONTEXT_DELIMITER)));
			}
		}
		return result;
	}

	public String replaceDeviceNameWithDot(String s){
		if(s!=null && s.length()>0){
			int indexReplace = s.indexOf(DEVICE_REPLACE_NAME_FROM);
			if(indexReplace>0){
				StringBuilder sb=new StringBuilder(s.length());
				sb.append(s.substring(0,indexReplace));
				sb.append(DEVICE_REPLACE_NAME_TO);
				return sb.toString();
			}
		}
		return s;
	}
}
