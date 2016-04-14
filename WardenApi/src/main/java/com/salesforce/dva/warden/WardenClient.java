/**
 * 
 */
package com.salesforce.dva.warden;

import java.util.List;

import com.salesforce.dva.warden.dto.Policy;

/**
 * @author jbhatt
 *
 */
public interface WardenClient {

	/**
	 * This method is responsible for establishing communication with the warden server.  It performs the following operations:
	 * Establish communication via the specified port by which the server can publish relevant events to.
	 * Compare policies provided as parameters and reconcile with whats on the server upserting as needed.
	 * Start the usage data push scheduling, so that usage data gets pushed to server on regular intervals.
	 * 
	 * @param policy
	 * @param port
	 */
	void register(List<Policy> policy, int port);
	void unregister();
	void updateMetric(Policy policy, String username, double value);
	void modifyMetric(Policy policy, String username, double delta);
	
}
