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

package com.salesforce.dva.argus.service.alert;

import com.salesforce.dva.argus.entity.PrincipalUser;

/**
 * A class represents the context of counting alerts.
 * 
 * @author Dongpu Jin (djin@salesforce.com)
 */
public class AlertsCountContext {

	// Boolean flag of whether counting user alerts
	private boolean countUserAlerts;

	// Boolean flag of whether counting shared alerts
	private boolean countSharedAlerts;

	// Boolean flag of whether counting private (non-shared) alerts for
	// privileged user
	private boolean countPrivateAlerts;

	// Current owner
	private PrincipalUser owner;

	// Search text
	private String searchText;

	// Constructor
	private AlertsCountContext(AlertsCountContextBuilder builder) {
		this.countUserAlerts = builder.userAlerts;
		this.countSharedAlerts = builder.sharedAlerts;
		this.countPrivateAlerts = builder.privateAlerts;
		this.owner = builder.owner;
		this.searchText = builder.searchText;
	}

	public boolean isCountUserAlerts() {
		boolean isValid = owner != null && !countSharedAlerts && !countPrivateAlerts;
		return isValid && countUserAlerts;
	}

	public boolean isCountSharedAlerts() {
		boolean isValid = !countUserAlerts && !countPrivateAlerts;
		return isValid && countSharedAlerts;
	}

	public boolean isCountPrivateAlerts() {
		boolean isValid = owner != null && !countUserAlerts && !countSharedAlerts;
		return isValid && countPrivateAlerts;
	}

	public PrincipalUser getPrincipalUser() {
		return owner;
	}

	public String getSearchText() {
		return searchText;
	}

	// Builder for the context.
	public static class AlertsCountContextBuilder {
		private boolean userAlerts = false;
		private boolean sharedAlerts = false;
		private boolean privateAlerts = false;
		private PrincipalUser owner = null;
		private String searchText = null;

		public AlertsCountContextBuilder countUserAlerts() {
			this.userAlerts = true;
			return this;
		}

		public AlertsCountContextBuilder countSharedAlerts() {
			this.sharedAlerts = true;
			return this;
		}

		public AlertsCountContextBuilder countPrivateAlerts() {
			this.privateAlerts = true;
			return this;
		}

		public AlertsCountContextBuilder setPrincipalUser(PrincipalUser owner) {
			this.owner = owner;
			return this;
		}

		public AlertsCountContextBuilder setSearchText(String searchText) {
			this.searchText = searchText;
			return this;
		}

		public AlertsCountContext build() {
			return new AlertsCountContext(this);
		}
	}
}
