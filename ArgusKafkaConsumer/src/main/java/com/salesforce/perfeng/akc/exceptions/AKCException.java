package com.salesforce.perfeng.akc.exceptions;

@SuppressWarnings("serial")
public class AKCException extends RuntimeException {

	public AKCException(String msg) {
		super(msg);
	}

	public AKCException(Throwable cause) {
		super(cause);
	}

	public AKCException(String msg, Throwable cause) {
		super(msg, cause);
	}

}
