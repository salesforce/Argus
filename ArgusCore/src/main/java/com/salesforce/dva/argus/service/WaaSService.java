package com.salesforce.dva.argus.service;

import java.math.BigInteger;
import java.util.List;

import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Infraction;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Policy;
import com.salesforce.dva.argus.entity.PrincipalUser;
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
    Policy updatePolicy(String user, Policy policy, List<Double> values);

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
    Policy updateSuspensionLevel(Policy policy, int level, int infractionCount, long suspensionTime);
    
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
	 * @param    The policy used for suspending
	 * 
	 * @return 	 infraction
	 */
	Infraction suspendUser(String user, Policy policy);
	
	/**
	 * Reinstate a user for the service based on policy.
	 * 
	 * @param    user The user name of this policy
	 * @param    The policy used for suspending
	 */
	void reinstateUser(String user, Policy policy);
	
	/**
	 * Return suspension levels with policy.
	 * 
	 * @param    policy  	The policy associate with suspension levels
	 */
	List<SuspensionLevel> getSuspensionLevels(Policy policy);

	/**
	 * Return policy based on policy name and service.
	 * 
	 * @param   name	  	The policy name
	 * @param	service		The service associated
	 * 
	 * @return policy
	 */
	Policy getPolicy(String name, String service);
	
	/**
	 * Return policies based on service.
	 * 
	 * @param    service  	The service associated
	 * 
	 * @return	 list of policy
	 */
	List<Policy> getPoliciesForService(String service);
	
	/**
	 * Return policies based on service.
	 * 
	 * @param    name  	The policy name
	 * 
	 * @return	list of policy
	 */
	List<Policy> getPoliciesForName(String name);
	
	/**
	 * ====================new REST API start from here=========================================
	 */
	
	/**
	 * Return all policies, called by /policy GET username is an optional
	 * parameter.
	 * 
	 * @return The list of policies
	 */
	List<Policy> getPolicies();

	List<Policy> getPoliciesForUser(String username);
	
	/**
	 * Delete policies, called by /policy DELETE
	 * 
	 * @param   policies    policies to be deleted
	 */
	void deletePolicies(List<Policy> policies);

	/**
	 * Update policies, called by /policy POST and PUT
	 * this is an upsert operation
	 * 
	 * @param 	policies	policies for update
	 * 
	 * @return updated policies
	 */
	List<Policy> updatePolicies(List<Policy> policies);
	
	/**
	 * Return a policy based on policy Id. 
	 * called by /policy/{pid} GET
	 * 
	 * @param    policyId    policy id used for query a policy.
	 * @return	 policy
	 */	
	Policy getPolicy(BigInteger policyId);
	
	/**
	 * Delete a policy, called by /policy/{pid} DELETE
	 * 
	 * @param policy
	 *            policy used for deletion.
	 */
	void deletePolicy(Policy policy);
	
	/**
	 * Update a policy,called by /policy/{pid} POST
	 * 
	 * @param policy
	 *            policy used for update.
	 * @return Policy updated policy
	 */
	Policy updatePolicy(Policy policy);

	/**
	 * Return all suspension levels based on policy id, called by
	 * /policy/{pid}/level GET
	 * 
	 * @param policy	policy used for query levels
	 * 
	 * @return list of suspension levels
	 */
	List<SuspensionLevel> getLevels(Policy existingPolicy);
	
	/**
	 * Create new suspension levels for a policy based on policy id, called by
	 * /policy/{pid}/level PUT
	 * 
	 * @param	levels	list of suspension levels to be created
	 * 
	 * @return	created suspension levels
	 */
	List<SuspensionLevel> createLevels(List<SuspensionLevel> levels);
	
	/**
	 * Delete suspension Levels for a policy based on policy id, called by
	 * /policy/{pid}/level DELETE
	 * 
	 * @param	levels	list of suspension levels be deleted
	 */
	void deleteLevels(List<SuspensionLevel> levels);
	
	/**
	 * Return a suspension level of a policy based on suspension level Id.
	 * called by /policy/{pid}/level/{levelid} GET
	 * 
	 * @param policyId
	 *            policy id used for query a level.
	 * @param levelId
	 *            suspension level id used for query a level.
	 * 
	 * @return suspension level
	 */
	SuspensionLevel getLevel(Policy policy, BigInteger levelId);
	
	/**
	 * Delete a suspension level of a policy based on suspension level Id.
	 * called by /policy/{pid}/level/{levelid} DELETE
	 * 
	 * @param policyId
	 *            policy id used for query a suspension level.
	 * @param levelId
	 *            suspension level id used for query a suspension level.
	 */
	void deleteLevel(SuspensionLevel level);
	
	/**
	 * Update a suspension level of a policy based on suspension level Id.
	 * called by /policy/{pid}/level/{levelid} POST Also called by
	 * /policy/{pid}/level POST
	 * 
	 * @param policyId
	 *            policy id used for query a suspension level.
	 * @param levelId
	 *            suspension level id used for query a suspension level.
	 *            
	 * @return 	updated suspension level           
	 */
	SuspensionLevel updateLevel(SuspensionLevel level);
	
	/**
	 * Return all infractions based on policy id, called by
	 * /policy/{pid}/infraction GET
	 * Also called by /policy/{pid}/suspension
	 * 
	 * @param	policy 	policy used for query infractions
	 * 
	 * @return	list of infractions
	 */
	List<Infraction> getInfractions(Policy policy);
	
	/**
	 * Return all infractions based on policy id and username called by
	 * /policy/{pid}/infraction GET 
	 * Also called by /policy/{pid}/user/{uid}/suspension
	 * 
	 * @param	policy		policy used for query infractions
	 * @param	username	username used for query infractions
	 * 
	 * @return 	list of infractions
	 */
	List<Infraction> getInfractionsByPolicyAndUserName(Policy policy, String username);
	
	/**
	 * Return an infraction of a policy based on infraction Id. called by
	 * /policy/{pid}/infracton/{iid} GET
	 * 
	 * @param policyId
	 *            policy id used for query a level.
	 * @param InfractionId
	 *            infraction id used for query a level.
	 *            
	 * @return infraction
	 */
	Infraction getInfraction(Policy policy, BigInteger infractionId);
	
	/**
	 * Delete a infraction.
	 * 
	 * @param infractionId	deleted infraction
	 */
	void deleteInfractionById(BigInteger infractionId);
	
	/**
	 * Delete infractionss called by /policy/{pid}/user/{uid}/suspension DELETE
	 * Also called by /policy/{pid/suspension DELETE
	 */
	void deleteInfractionByIds(List<BigInteger> infractionIds);
	
	/**
	 * get metrics for a specific user of a policy, called by
	 * /policy/{pid}/user/{uid}/metric GET 
	 * Also called by /user/{uid}/policy/{pid}/metric GET
	 * 
	 * @param policyId
	 *            policy id used to query metrics
	 * @param userId
	 *            user id used to query metrics
	 *            
	 * @return	list of metrics
	 */
	List<com.salesforce.dva.argus.entity.Metric> getMetrics(Policy policy, PrincipalUser user);
	
	/**
	 * create metrics for a specific user of a policy, called by
	 * /policy/{pid}/user/{uid}/metirc PUT
	 * 
	 * @param policyId
	 *            policy id used to create metrics
	 * @param userId
	 *            user id used to create metrics
	 */
	void creatMetrics(PrincipalUser remoteUser, List<Metric> metrics);

	/**
	 * Delete an infraction
	 * @param infraction	infraction to be deleted
	 */
	void deleteInfraction(Infraction infraction);
	
	/**
	 * get infractions for a specific user called by /user/{uid}/infraction
	 * Also called by /user/{uid}/suspension
	 * 
	 * @param	principalUser	user for query infractons
	 * 
	 * @return	list of infraction
	 */
	List<Infraction> getInfractionsByUser(PrincipalUser principalUser);
	
	
	
	
	
	
		
	
	
	
	
	
	
	
	
	
	
	
	

}

