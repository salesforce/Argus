package com.salesforce.dva.argus.service.tsdb;

public interface ScannerShiftFunction {

	/**
	 * Returns the shifting already associated with a scanner object
	 * when it is created (the difference between a the timestamp of
	 * a datapoint contained by the scanner and the timestamp that
	 * generated this point).
	 * 
	 * @param t The Long timestamp of the time that would create the scanner datapoint.
	 * @return The Long representing the shifted value of the input time.
	 */
	Long shift(Long t);
}
