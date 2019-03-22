/* Copyright Salesforce 2002,2015 All Rights Reserved. **********************************************************************************************/
package com.salesforce.perfeng.akc;

import com.salesforce.perfeng.akc.exceptions.AKCException;

/**
 * Provides functionality to assert that certain conditions are met.
 * 
 * @author Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class AKCAssert {

	// ~ Constructors *********************************************************************************************************************************
	
	/* Private constructor to prevent instantiation. */
	private AKCAssert() {
		assert (false) : "This class should never be instantiated.";
	}

	// ~ Methods **************************************************************************************************************************************
	
	/**
	 * 
	 * Throws an IllegalArgumentException if the condition is not met.
	 * 
	 * @param condition	The condition to evaluate,
	 * @param message	The exception message.
	 */
	public static void requireArgument(boolean condition, String message) {
		require(condition, message, IllegalArgumentException.class);
	}
	
	/**
	 * 
	 * Throws an IllegalStateException if the condition is not met.
	 *
	 * @param condition	The condition to evaluate,
	 * @param message	The exception message.
	 */
	public static void requireState(boolean condition, String message) {
		require(condition, message, IllegalStateException.class);
	}

	private static <T extends RuntimeException> void require(boolean condition,
			String message, Class<T> type) {
		if (!condition) {
			RuntimeException result;
			try {
				result = type.getConstructor(String.class).newInstance(message);
			} catch (Exception ex) {
				throw new AKCException(ex);
			}
			throw result;
		}
	}
	
}

/*
 * Copyright Salesforce 2002,2015 All Rights Reserved.
 * **************************
 * *******************************************************************
 */