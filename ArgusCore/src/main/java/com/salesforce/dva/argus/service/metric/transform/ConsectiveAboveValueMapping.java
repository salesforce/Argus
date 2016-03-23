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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.salesforce.dva.argus.system.SystemAssert;

/**
 * Given a time series, capture the data points meets the requirement below, to form a new time series.
 * This class captures all the data points whose value is above threshold1, and continuesely for threshold2 consective data points.
 *
 * @author  Ethan Wang (ethan.wang@salesforce.com)
 */
public class ConsectiveAboveValueMapping implements ValueMapping{
 
	@Override
	public Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants) {
		SystemAssert.requireArgument(constants != null && !constants.equals(""), "Thresholds must be provided!");
		SystemAssert.requireArgument(constants.size()==2, "Please put in two parameter. Threshold and consective threshold");
		
		
		Double threshold=Double.valueOf(constants.get(0));
		Integer consective=Integer.valueOf(constants.get(1));
		SystemAssert.requireArgument(consective!=0, "Consective threshold can not be zero");
		
		
		Map<Long, String> resultMetric = new TreeMap<Long, String>();
		ArrayList<Long> keyList=new ArrayList<Long>();
		keyList.addAll(originalDatapoints.keySet());
		Collections.sort(keyList);
	
		int cum=0;
		for(int i=0;i<keyList.size();i++){
			Double value=Double.parseDouble(originalDatapoints.get(keyList.get(i)));
			if (i==keyList.size()-1){
				if(value>=threshold){cum+=1;i++;}
				if(cum>=consective){
					for(int j=i-1;j>(i-1-cum);j--){
						resultMetric.put(keyList.get(j), String.valueOf(originalDatapoints.get(keyList.get(j))));
					}
				}
				continue;
			}
			if (value>=threshold){
				cum+=1;
			}else{
				if(cum>=consective){
					for(int j=i-1;j>(i-1-cum);j--){
						resultMetric.put(keyList.get(j), String.valueOf(originalDatapoints.get(keyList.get(j))));
					}
				}
				cum=0;
			}
		}
		return resultMetric;
	}
	
	@Override
	public Map<Long, String> mapping(Map<Long, String> originalDatapoints) {
		throw new UnsupportedOperationException("This transform does requires an input!");
	}

	@Override
	public String name() {
		return TransformFactory.Function.CONSECTIVEABOVE.name();
	}
}


//Another implementation. Much more efficent but this stacks up in memory, so please don't use this one.
//private Map<Long, String> originalDatapoints;
//private ArrayList<Long> keyList;
//private Map<Long, String> resultMetric = new TreeMap<Long, String>();
//private int threshold=0;
//private int consective=0;
//
//private Object crawler(int i,int cum){
//	if(i==keyList.size()){	
//		if(cum>=consective){
//			for(long j:keyList.subList(i-cum, i)){
//				System.out.println(j);
//			}
//		}
//		return null;
//	}
//	Double value=Double.parseDouble(originalDatapoints.get(keyList.get(i)));
//	System.out.println("currently-"+value);
//	if (value>=threshold){//collect you
//		return crawler(i+1,cum+1);
//	}else{//not collecting
//		if(cum>=consective){
//			for(long j:keyList.subList(i-cum, i)){
//				System.out.println(j);
//			}
//		}
//		return crawler(i+1,0);
//	}
//}

/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */