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

package com.salesforce.dva.argus.entity;

import com.google.common.base.Objects;

import java.text.MessageFormat;

/**
 * Represents a search result row for scope names discovery queries.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class ScopeOnlySchemaRecord {

	//~ Instance fields ******************************************************************************************************************************

	private String scope;

	//~ Constructors *********************************************************************************************************************************

	/** Creates a new ScopeOnlySchemaRecord object. */
	public ScopeOnlySchemaRecord() { }


	/**
	 * Creates a new ScopeOnlySchemaRecord object.
	 *
	 * @param  scope      The metric schema scope.
	 */
	public ScopeOnlySchemaRecord(String scope) {
		setScope(scope);
	}

	/**
	 * Indicates the scope associated with the result.
	 *
	 * @return  The scope.  Can be null or empty.
	 */
	public String getScope() {
		return scope;
	}

	/**
	 * Specifies the scope associated with the result.
	 *
	 * @param  scope  The scope.  Can be null or empty.
	 */
	public void setScope(String scope) { 
		this.scope = scope;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;

		result = prime * result + ((scope == null) ? 0 : scope.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}

		ScopeOnlySchemaRecord other = (ScopeOnlySchemaRecord) obj;

		return Objects.equal(scope, other.scope);
	}

	@Override
	public String toString() {
		return MessageFormat.format("ScopeOnlySchemaRecord = (Scope = {0}", scope);
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
