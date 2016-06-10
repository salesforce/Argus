package com.salesforce.dva.warden.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Credentials implements Serializable {

	// ~ Instance fields
	// ******************************************************************************************************************************

	private String username;
	private String password;

	// ~ Methods
	// **************************************************************************************************************************************

	/**
	 * DOCUMENT ME!
	 * 
	 * @return DOCUMENT ME!
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * DOCUMENT ME!
	 * 
	 * @param username
	 *            DOCUMENT ME!
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * DOCUMENT ME!
	 * 
	 * @return DOCUMENT ME!
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * DOCUMENT ME!
	 * 
	 * @param password
	 *            DOCUMENT ME!
	 */
	public void setPassword(String password) {
		this.password = password;
	}

}
