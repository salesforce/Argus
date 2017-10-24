/*
 * Copyright (c) 2016, Salesforce.com, Inc.
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
package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;

/**
 * Returns all datapoint's for which the interval between adjacent datapoints is less than or equal to the specified time interval, for consecutive specified time threshold.
 * 
 * @param	metric	Time series to be eveluate and transform. Cannot be null or empty
 * @param	constants	The constants requires two values: The first one is a time value. It is a desired goal that all adjacent datapoints lasts longer than should be returned; The second one is a time value. It is the default interval distance between any two data points in time series.
 *
 * @author  Ethan Wang (ethan.wang@salesforce.com)
 */
public class ConsecutiveValueMapping implements ValueMapping {
	private long threshold;
	private long connectDistance;
	private ArrayList<Long> keyList;
	private ArrayList<Long> resultKeyList;
	
    /**
     * mapping method implementation<br>
     * This is the start point of this transform. It firstly verifies if all inputs are legal, then it crawls forward on this series and collects every qualified adjacent datapoints along the way.
     * 
     * @param	originalDatapoints	Time series to be eveluate and transform. Cannot be null or empty.
     * @param	constants	The constants requires two values. Details see above. Both time value and can be null but can not be empty.
     *	
     * @return	resultMetric	A new time series that has been transformed.
     */
	@Override
	public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
		SystemAssert.requireArgument(constants != null, "This transform needs constants");
        SystemAssert.requireArgument(constants.size() == 2, "This transform must provide exactly 2 constants.");    
        
        this.threshold = getOffsetInSeconds(constants.get(0)) * 1000;
        this.connectDistance = getOffsetInSeconds(constants.get(1)) * 1000;
              
        Map<Long, Double> resultMetric = new TreeMap<>();
        this.keyList = new ArrayList<Long>();
        this.resultKeyList = new ArrayList<Long>();
		keyList.addAll(originalDatapoints.keySet());
		Collections.sort(keyList);
		
		if (keyList.size() > 1){
			connect(0, new ArrayList<>(Arrays.asList(keyList.get(0))));
		}
		
		for(Long resultKey : resultKeyList){
			resultMetric.put(resultKey, originalDatapoints.get(resultKey));
		}
		return resultMetric;
	}
	
	@Override
	public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
		SystemAssert.requireArgument(constants != null, "This transform needs constants");
        SystemAssert.requireArgument(constants.size() == 2, "This transform must provide exactly 2 constants.");
        
        this.threshold = getOffsetInSeconds(constants.get(0)) * 1000;
        this.connectDistance = getOffsetInSeconds(constants.get(1)) * 1000;
        
        Map<Long, Double> resultMetric = new TreeMap<>();
        Map<Long, Double> dps = new HashMap<>();
        this.keyList = new ArrayList<Long>();
        this.resultKeyList = new ArrayList<Long>();
        
	    if (scanner.hasNextDP()) {
	       	connectScanner(scanner, dps);
	    }
        
        for (Long resultKey : resultKeyList) {
        	resultMetric.put(resultKey, dps.get(resultKey));
        }
        return resultMetric;
	}
	
	@Override
	public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
		SystemAssert.requireArgument(constants != null, "This transform needs constants");
		SystemAssert.requireArgument(constants.size() == 2, "This transform must provide exactly 2 constants.");
		
		this.threshold = getOffsetInSeconds(constants.get(0)) * 1000;
		this.connectDistance = getOffsetInSeconds(constants.get(1)) * 1000;
		this.keyList = new ArrayList<>();
		this.resultKeyList = new ArrayList<>();
		
		Map.Entry<Long, Double> next = scanner.peek();
		if (next == null || next.getKey() > end + connectDistance) {
			/* all metric */
			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
			Long startKey = dps.ceilingKey(start - Math.max(connectDistance, threshold));
			Long endKey = dps.floorKey(end + Math.max(connectDistance, threshold));
			if (startKey == null || endKey == null || startKey > endKey) {
				return new HashMap<>();
			}
			this.keyList = new ArrayList<>(dps.subMap(startKey, endKey + 1).keySet());
			Collections.sort(keyList);
			this.resultKeyList = new ArrayList<>();
			if (keyList.size() >= 2) {
				connect(0, new ArrayList<>(Arrays.asList(keyList.get(0))));
			}
		} else {
			List<Long> carryList = new ArrayList<>();
			if (next.getKey() > Collections.min(scanner.getMetric().getDatapoints().keySet())) {
				/* some metric */
				TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
				Long startKey = dps.ceilingKey(start - connectDistance);
				Long endKey = dps.floorKey(next.getKey());
				if (startKey != null && endKey != null && startKey < endKey) {
					this.keyList = new ArrayList<>(dps.subMap(startKey, endKey).keySet());
					Collections.sort(keyList);
					this.resultKeyList = new ArrayList<>();
					carryList = new ArrayList<>(Arrays.asList(keyList.get(0)));
					
					for (int i = 0; i < keyList.size() - 1; i++) {
						if (keyList.get(i + 1) - keyList.get(i) <= connectDistance) {
							carryList.add(keyList.get(i + 1));
							if (i != keyList.size() - 2) {
								continue;
							}
						}
						if (carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold) {
							resultKeyList.addAll(carryList);
						}
						if (i != keyList.size() - 2) {
							carryList = new ArrayList<>(Arrays.asList(keyList.get(i + 1)));
						}
					}
				}
			} else {
				while (scanner.peek() != null && scanner.peek().getKey() < start - connectDistance) {
					scanner.getNextDP();
				}
			}
			
			if (next != null && next.getKey() <= end + Math.max(connectDistance, threshold)) {
				Map.Entry<Long, Double> dp = scanner.getNextDP();
				keyList.add(dp.getKey());
				Collections.sort(keyList);
				if (!carryList.isEmpty() && dp.getKey() - carryList.get(carryList.size() - 1) <= connectDistance) {
					carryList.add(dp.getKey());
					if ((scanner.peek() == null || scanner.peek().getKey() > end + connectDistance) &&
							carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold) {
						resultKeyList.addAll(carryList);
					}
				} else {
					carryList = new ArrayList<>(Arrays.asList(dp.getKey()));
				}
				int current = carryList.size() - 1;
				Collections.sort(keyList);
				while (scanner.peek() != null && scanner.peek().getKey() <= end + Math.max(connectDistance, threshold)) {
					dp = scanner.getNextDP();
					keyList.add(dp.getKey());
					if (scanner.peek() == null || scanner.peek().getKey() > end + connectDistance) {
						// are on the last point
						if (dp.getKey() - keyList.get(current) <= connectDistance) {
							carryList.add(dp.getKey());
						}
						if (carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold) {
							resultKeyList.addAll(carryList);
						}
					} else {
						if (dp.getKey() - keyList.get(current) <= connectDistance) {
							carryList.add(dp.getKey());
							current++;
							continue;
						}
						if (carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold) {
							resultKeyList.addAll(carryList);
						}
						current++;
						carryList = new ArrayList<>(Arrays.asList(dp.getKey()));
					}
				}
			}
		}
		/* down here, should have updated keyList and resultKeyList */
		TreeMap<Long, Double> result = new TreeMap<>();
		Map<Long, Double> original = scanner.getMetric().getDatapoints();
		for (Long key : resultKeyList) {
			result.put(key, original.get(key));
		}
		Long sKey = result.ceilingKey(start);
		Long eKey = result.floorKey(end);
		if (sKey == null || eKey == null || sKey > eKey) {
			return new HashMap<>();
		}
		return result.subMap(sKey, eKey + 1);
	}
	
	private Object connect(int current, ArrayList<Long> carryList){	
		if (current + 2 == keyList.size()){
			if (keyList.get(current + 1) - keyList.get(current) <= connectDistance){
				carryList.add(keyList.get(current + 1));
			}
			if (carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold){
				resultKeyList.addAll(carryList);
			}
			return null;
		}
		
		if (keyList.get(current + 1) - keyList.get(current) <= connectDistance){
			carryList.add(keyList.get(current + 1));
			return connect(current + 1 ,carryList);
		}
		if (carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold){
			resultKeyList.addAll(carryList);
		}
		return connect(current + 1, new ArrayList<>(Arrays.asList(keyList.get(current+1)))); 
	}
	
	private void connectScanner(MetricScanner scanner, Map<Long, Double> dps) {    	
    	Map.Entry<Long, Double> dp = scanner.getNextDP();
    	this.keyList.add(dp.getKey());
    	dps.put(dp.getKey(), dp.getValue());
    	
    	List<Long> carryList = new ArrayList<>(Arrays.asList(keyList.get(0)));
    	int current = 0;
    	
    	while (scanner.hasNextDP()) {	// know that we then have a datapoint at current + 1
    		dp = scanner.getNextDP();
    		this.keyList.add(dp.getKey());
    		dps.put(dp.getKey(), dp.getValue());
    		
    		if (keyList.get(current + 1) - keyList.get(current) <= connectDistance) {
    			carryList.add(keyList.get(current + 1));
    		}
    		if (carryList.size() > 0 && Collections.max(carryList) - Collections.min(carryList) >= threshold) {
    			resultKeyList.addAll(carryList);
    		}
    		
    		current += 1;
    		carryList = new ArrayList<>(Arrays.asList(keyList.get(current)));
    	}
	}
	
	private long getOffsetInSeconds(String offset) {
        MetricReader.TimeUnit timeunit = null;
        Long backwards = 1L;
        try {
            if (offset.startsWith("-")) {
                backwards = -1L;
                offset = offset.substring(1);
            }
            if (offset.startsWith("+")) {
                offset = offset.substring(1);
            }
            timeunit = MetricReader.TimeUnit.fromString(offset.substring(offset.length() - 1));
            long timeDigits = Long.parseLong(offset.substring(0, offset.length() - 1));
            return backwards * timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            throw new IllegalArgumentException("Fail to parse offset!");
        }
    }

	@Override
	public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
		throw new UnsupportedOperationException("Consective Transform needs a threshold and type.");
	}
	
	@Override
	public Map<Long, Double> mappingScanner(MetricScanner scanner) {
		throw new UnsupportedOperationException("Consective Transform needs a threshold and type.");
	}
	
	@Override
	public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
		throw new UnsupportedOperationException("Consecutive Transform needs a threshold and type.");
	}
	
	@Override
	public String name() {
		return TransformFactory.Function.CONSECUTIVE.name();
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */