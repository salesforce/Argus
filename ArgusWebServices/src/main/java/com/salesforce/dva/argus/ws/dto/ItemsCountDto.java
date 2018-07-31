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

package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Items count DTO.
 *
 * @author Dongpu Jin (djin@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemsCountDto extends BaseDto implements Serializable {

	// ~ Instance fields
	// ******************************************************************************************************************************

	private int value;

	// ~ Methods
	// **************************************************************************************************************************************

	/**
	 * Converts an integer to ItemsCountDto instance.
	 *
	 * @param value
	 *            The items count.
	 *
	 * @return An itemsCountDto object.
	 *
	 * @throws WebApplicationException
	 *             If an error occurs.
	 */
	public static ItemsCountDto transformToDto(int value) {
		if (value < 0) {
			throw new WebApplicationException("Items count cannot be negative", Status.INTERNAL_SERVER_ERROR);
		}

		ItemsCountDto result = new ItemsCountDto();
		result.setValue(value);
		return result;
	}

	/**
	 * Returns the items count.
	 * 
	 * @return The items count.
	 */
	public int getValue() {
		return this.value;
	}

	/**
	 * Specifies the items count.
	 * 
	 * @param itemsCount
	 *            The items count.
	 */
	public void setValue(int value) {
		this.value = value;
	}

	@Override
	public Object createExample() {
		ItemsCountDto result = new ItemsCountDto();

		result.setValue(0);

		return result;
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc. All rights reserved. */
