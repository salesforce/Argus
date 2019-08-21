/*
 *
 *  * Copyright (c) 2016, Salesforce.com, Inc.
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  *
 *  * 1. Redistributions of source code must retain the above copyright notice,
 *  * this list of conditions and the following disclaimer.
 *  *
 *  * 2. Redistributions in binary form must reproduce the above copyright notice,
 *  * this list of conditions and the following disclaimer in the documentation
 *  * and/or other materials provided with the distribution.
 *  *
 *  * 3. Neither the name of Salesforce.com nor the names of its contributors may
 *  * be used to endorse or promote products derived from this software without
 *  * specific prior written permission.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *  * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  * POSSIBILITY OF SUCH DAMAGE.
 *
 */
package com.salesforce.dva.argus.service.monitor;

import com.salesforce.dva.argus.entity.Metric;

import java.util.Map;
import java.util.Set;

import java.util.List;
/**
 * Interface to check for data lag based on various approaches.
 * @author sudhanshu.bahety
 */
public interface DataLagService extends Runnable {
	/**
	 *
	 * @param dcSet List of dc for which we need to make query
	 * @param startTime start time for the query
	 * @return Mapping of metric per dc that is to be used to compute data lag
	 */
	public Map<String, List<Metric>> queryMetricsForDC(Set<String> dcSet, Long startTime);

	/**
	 *
	 * @param dc name of the data centre
	 * @param metricList List of metrics for the specific data centre
	 * @return Status based on hypothesis whether data is lagging in dc or not
	 */
	public Boolean computeDataLag(String dc, List<Metric> metricList);

	/**
	 *
	 * @param dc name of the data centre
	 * @return if data is lagging in the dc
	 */
	public Boolean isDataLagging(String dc);

	/**
	 *
	 * @param time time when the lag metric is pushed
	 * @param value value of the lag metric
	 * @param dc dc corresponding to the lag metric
	 */
	public void pushMetric(Long time, Double value, String dc);

	/**
	 * The set of implementation specific configuration properties.
	 *
	 */
	public enum Property {

		/** Flag to enable/disable monitoring */
		DATA_LAG_MONITOR_ENABLED("system.property.monitor.data.lag", "false"),
		/** Whitelist scopes for which data lag always evaluates to false*/
		DATA_LAG_WHITE_LISTED_SCOPES("system.property.data.lag.whitelisted.scopes", "whiteListedScope"),
		/** Whitelist scope of user for which data lag always evaluates to false*/
		DATA_LAG_WHITE_LISTED_USERS("system.property.data.lag.whitelisted.username", "default"),
		/** List of DC for which data lag present should always evaluate to true*/
		DATA_LAG_ENFORCE_DC_LIST("system.property.data.lag.enforce.dc.list", "dcList");

		private final String _name;
		private final String _defaultValue;

		Property(String name, String defaultValue) {
			_name = name;
			_defaultValue = defaultValue;
		}

		/**
		 * Returns the property name.
		 *
		 * @return  The property name.
		 */
		public String getName() {
			return _name;
		}

		/**
		 * Returns the default value for the property.
		 *
		 * @return  The default value.
		 */
		public String getDefaultValue() {
			return _defaultValue;
		}
	}
}
