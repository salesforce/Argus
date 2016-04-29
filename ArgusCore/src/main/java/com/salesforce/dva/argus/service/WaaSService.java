package com.salesforce.dva.argus.service;

import java.math.BigInteger;
import java.util.List;

import com.salesforce.dva.argus.entity.Infraction;
import com.salesforce.dva.argus.entity.Policy;
import com.salesforce.dva.argus.entity.SuspensionLevel;

public interface WaaSService {
	/**
     * Updates the policy for a user.
     * 
     * If this policy already exists, the value will be updated.
     * If this policy doesn't exits, will create a new policy.
     *
     * @param  user     The user for which the policy will be updated. Cannot be null.
     * @param  policy  	The policy  for which the threshold value will be set. Cannot be null.
     * @param  value    The new value for the policy threshold for the user. Cannot be null.
     */
    Policy upsertPolicy(String user, Policy policy, double value);
	
    /**
     * Update the suspension level for a given policy.
     * 
     * If this level already exists, infractionCount and suspensionTimes
     * will be updated accordingly, otherwise, it will create suspension level.
     *
     * @param   policy  		The policy for the suspensionLevel. Cannot be null or empty.
     * 
     * @param   level   		The level for this suspensionLevel. Cannot be null or empty.
     * 
     * @param   infractionCount The infraction count of this suspensionLevel. Cannot be null or empty.
     * 
     * @param   suspensionTime  The suspension time for this suspensionLevel. Cannot be null or empty.
     *
     * @return  The updated / new suspensionLevel or null if given policy doesn't exist.
     */
    SuspensionLevel upsertSuspensionLevel(Policy policy, int level, int infractionCount, long suspensionTime);
    
    /**
     * Check if the user is suspended with given policy
     *
     * @param   user  	The user for checking suspension. Cannot be null or empty.
     * 
     * @param   policy 	The policy used for checking. Cannot be null or empty.
     *
     * @return  The boolean value to indicate if the user is suspended or not.
     */
    boolean isSuspended(String user, Policy policy);

	/**
	 * Suspends a user for the service based on policy.
	 * 
	 * @param    user The user name of this policy
	 * 
	 * @param    The policy used for suspending
	 */
	void suspendUser(String user, Policy policy);
	
	/**
	 * Reinstate a user for the service based on policy.
	 * 
	 * @param    user The user name of this policy
	 * 
	 * @param    The policy used for suspending
	 */
	void reinstateUser(String user, Policy policy);
	
	/**
	 * Return a policy based on policy Id.
	 * 
	 * @param    policyId    policy id used for query a policy.
	 */	
	Policy getPolicy(BigInteger policyId);
	
	/**
	 * Return a policy with name and service.
	 * 
	 * @param    name     The name of this policy
	 * 
	 * @param    service  The service associated with policy
	 */
	Policy getPolicy(String name, String service);
	
	/**
	 * Return policies based on given service.
	 * 
	 * @param    service    service name of this policy.
	 */	
	List<Policy> getPoliciesForService(String service);
	
	/**
	 * Return a policies with given policy name.
	 * 
	 * @param    user     The name of policy
	 */
	List<Policy> getPoliciesForName(String name);
	
	/**
	 * Return infractions with user name and policy.
	 * 
	 * @param    username	The name of user associated with infractions
	 * 
	 * @param    policy  	The policy associate with infractions
	 */
	List<Infraction> getInfractions(String userName, Policy policy);
	
	/**
	 * Return suspension levels with policy.
	 * 
	 * @param    policy  	The policy associate with suspension levels
	 */
	List<SuspensionLevel> getSuspensionLevels(Policy policy);

}
