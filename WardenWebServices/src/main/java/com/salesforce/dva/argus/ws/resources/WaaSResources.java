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
	 
package com.salesforce.dva.argus.ws.resources;


import com.salesforce.dva.argus.entity.PrincipalUser;
//import com.salesforce.dva.argus.entity.SuspensionLevel;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WaaSMonitorService;
import com.salesforce.dva.argus.service.WaaSService;
import com.salesforce.dva.argus.util.WaaSObjectConverter;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.warden.dto.Infraction;
import com.salesforce.dva.warden.dto.Metric;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.SuspensionLevel;
import com.salesforce.dva.warden.dto.WardenUser;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Web services for WaaS.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@Path("/warden")
@Description("Provides methods to manipulate warden entities.")
public class WaaSResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private WaaSService waaSService = system.getServiceFactory().getWaaSService();
    private UserService userService = system.getServiceFactory().getUserService();
    private WaaSMonitorService waaSMonitorService = system.getServiceFactory().getWaaSMonitorService();
   
    //~ Methods **************************************************************************************************************************************
    //========================policies start from here============================
    /**
    * Returns the list of policies owned by the owner.
    *
    * @param   req        The HttpServlet request object. Cannot be null.
    * @param   username   User name used for filtering the policies. It is optional.
    *
    * @return  The list of policies filtered by user and owner.
    */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy")
    @Description("Returns all policies.")
    public List<Policy> getPolicies(@Context HttpServletRequest req,
        @QueryParam("username") String username) {
        List<Policy> result = new ArrayList<Policy>();
        List<com.salesforce.dva.argus.entity.Policy> policies = null;
        PrincipalUser remoteUser = getRemoteUser(req);
        
        //query policy from waasService
        if (username != null && !username.isEmpty()){
        	policies = waaSService.getPoliciesForUser(username);
        } else {
        	policies = waaSService.getPolicies();
        }        
               
        //filter policy based on ownership if remoteUser is not admin
        if(policies != null && !policies.isEmpty() && !remoteUser.isPrivileged()){
        	policies = policies.stream().filter(p ->p.getOwners().contains(remoteUser.getUserName())).collect(Collectors.toList());
        }
        
        if(policies == null || policies.isEmpty())
       	 	throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
               
        for(com.salesforce.dva.argus.entity.Policy p : policies){
        	result.add(WaaSObjectConverter.convertToPolicyDto(p));
        }
       
        return result;
    } 
    
    /**
     * Creates  new policies.
     *
     * @param   req       	The HttpServlet request object. Cannot be null.
     * @param   policies  	The policy objects. Cannot be null.
     *
     * @return  Created policy objects.
     *
     * @throws  WebApplicationException  The exception with 400 status will be thrown if the policy object is null.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/policy")
    @Description("Creates policies.")
    public List<Policy> createPolicies(@Context HttpServletRequest req, List<Policy> policies) {
    	if(policies == null || policies.isEmpty()){
    		throw new WebApplicationException("Null policy objects cannot be created.", Status.BAD_REQUEST);
    	}
    	
    	List<Policy> result = new ArrayList<Policy>();
    	
        PrincipalUser remoteUser = getRemoteUser(req);   
                
        //filter uploaded policy based on ownership if remoteUser is not admin
        if(!remoteUser.isPrivileged()){
        	policies = policies.stream().filter(p ->p.getOwners().contains(remoteUser.getUserName())).collect(Collectors.toList());
        }
        
        if(policies == null || policies.isEmpty())
       	 	throw new WebApplicationException("Remote user doesn't own these policies and has no priviledge to create them.", Status.BAD_REQUEST);
        
        List<com.salesforce.dva.argus.entity.Policy> policyEntities = new ArrayList<com.salesforce.dva.argus.entity.Policy>();
        com.salesforce.dva.argus.entity.Policy entity = null;
		for (Policy p : policies) {

			entity = new com.salesforce.dva.argus.entity.Policy(remoteUser, p.getService(), p.getName(), p.getOwners(),
					p.getUsers(), p.getTriggerType(), p.getAggregator(), p.getThreshold(), p.getTimeUnit(),
					p.getDefaultValue(), p.getCronEntry());
			policyEntities.add(entity);
        }
		return WaaSObjectConverter.convertToPolicyDtos(waaSService.updatePolicies(policyEntities));
    }
    
    /**
     * Deletes policies.
     *
     * @param   req     The HttpServlet request object. Cannot be null.
     *
     * @return  REST response indicating whether the policies deletion was successful.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an policy does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy")
    @Description("Deletes the all the policies owned by this user.")
    public Response deletePolicies(@Context HttpServletRequest req) {
        List<com.salesforce.dva.argus.entity.Policy> existingPolicies = waaSService.getPolicies();
        PrincipalUser remoteUser = getRemoteUser(req);
        
        //filter policy based on ownership if remoteUser is not admin
        if(existingPolicies != null  && !existingPolicies.isEmpty() && !remoteUser.isPrivileged()){
        	existingPolicies = existingPolicies.stream().filter(p ->p.getOwners().contains(remoteUser.getUserName())).collect(Collectors.toList());
        }
        
        if(existingPolicies == null || existingPolicies.isEmpty())
       	 	throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
               
        waaSService.deletePolicies(existingPolicies);
        
        return Response.status(Status.OK).build();
    }
    
    /**
     * Updates existing policies.
     *
     * @param   req       The HttpServlet request object. Cannot be null.
     * @param   policies   Policies objects. Cannot be null.
     *
     * @return  Updated policy objects.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert does not exist.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/policy")
    @Description("Updates policy objects")
    public List<Policy> updatePolicies(@Context HttpServletRequest req, 
    		List<Policy> policies){
    	if(policies == null || policies.isEmpty()){
    		throw new WebApplicationException("Null policy objects cannot be updated.", Status.BAD_REQUEST);
    	}
    	
    	List<Policy> result = new ArrayList<Policy>();
    	
        PrincipalUser remoteUser = getRemoteUser(req);   
                
        //filter uploaded policy based on ownership if remoteUser is not admin
        if(!remoteUser.isPrivileged()){
        	policies = policies.stream().filter(p ->p.getOwners().contains(remoteUser.getUserName())).collect(Collectors.toList());
        }

        if(policies == null || policies.isEmpty())
       	 	throw new WebApplicationException("Remote user doesn't own these policies and has no priviledge to update them.", Status.BAD_REQUEST);
        
        for(Policy newPolicy : policies) {
            com.salesforce.dva.argus.entity.Policy oldPolicy = waaSService.getPolicy(newPolicy.getId());
            if(oldPolicy == null) {
            	throw new WebApplicationException("Policy for" +  newPolicy.getId() + "cannot be found, please create it first.", Response.Status.NOT_FOUND);
            }
            copyProperties(oldPolicy, newPolicy);
            oldPolicy.setModifiedBy(remoteUser);
            result.add(WaaSObjectConverter.convertToPolicyDto(waaSService.updatePolicy(oldPolicy)));
        }
        return result;
        
    }

    //=======================policy start from here====================================
    /**
     * Finds a policy by policy ID.
     *
     * @param   req      	The HttpServlet request object. Cannot be null.
     * @param   pid  		ID of a policy. Cannot be null and must be a positive non-zero number.
     *
     * @return  Policy
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if a policy does not exist.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy/{pid}")
    @Description("Returns a policy by its ID.")
    public Policy getPolicyByID(@Context HttpServletRequest req,
        @PathParam("pid") BigInteger pid) {
        if (pid == null || pid.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Policy result = null;
        com.salesforce.dva.argus.entity.Policy policy = null;
        PrincipalUser remoteUser = getRemoteUser(req);
        
        policy = waaSService.getPolicy(pid);
        
        if(policy == null)
       	 	throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        
        //check ownership
        if(!remoteUser.isPrivileged() && !policy.getOwners().contains(remoteUser.getUserName())){
        	throw new WebApplicationException("Remote user doesn't own this policy and has no priviledge to access it.", Status.BAD_REQUEST);
        }
        
        result = WaaSObjectConverter.convertToPolicyDto(policy);
        return result;
    }
    
    /**
     * Deletes the policy.
     *
     * @param   req     The HttpServlet request object. Cannot be null.
     * @param   pid  	The policy Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  REST response indicating whether the policy deletion was successful.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an policy does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy/{pid}")
    @Description("Deletes the policy having the given ID.")
    public Response deletePolicy(@Context HttpServletRequest req,
        @PathParam("pid") BigInteger pid) {
        if (pid == null || pid.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        
        com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(pid);
        PrincipalUser remoteUser = getRemoteUser(req);
  
        if(existingPolicy == null){
       	 	throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        if(existingPolicy!= null && !remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
        	throw new WebApplicationException("Remote user doesn't own this policy and has no priviledge to delete it.", Status.BAD_REQUEST);
        }
               
        if(waaSService.getLevels(existingPolicy) != null || !waaSService.getLevels(existingPolicy).isEmpty()){
        	throw new WebApplicationException("This policy has suspension levels associated with it, please delete the suspension levels first!", Status.BAD_REQUEST);
        }
        
        waaSService.deletePolicy(existingPolicy);
        return Response.status(Status.OK).build();
    }
    
    
    /**
     * Updates existing policy.
     *
     * @param   req      	The HttpServlet request object. Cannot be null.
     * @param   policyId   	The id of an policy. Cannot be null.
     * @param   policy  	The new policy object. Cannot be null.
     *
     * @return  Updated policy object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert does not exist.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{pid}")
    @Description("Updates an policy having the given ID.")
    public Policy updatePolicy(@Context HttpServletRequest req,
        @PathParam("pid") BigInteger pid, Policy policy) {
        if (pid == null || pid.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (policy == null) {
            throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
        }
        
        PrincipalUser remoteUser = getRemoteUser(req);
        
        //check ownership
        if(!remoteUser.isPrivileged() && !policy.getOwners().contains(remoteUser.getUserName())){
        	throw new WebApplicationException("Remote user doesn't own this policy and has no priviledge to access it.", Status.BAD_REQUEST);
        }
        
        com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(pid);
        
        if(existingPolicy == null)
       	 	throw new WebApplicationException("This policy doesn't exist, please create it first!", Response.Status.NOT_FOUND);
               
        copyProperties(existingPolicy, policy);
        existingPolicy.setModifiedBy(remoteUser);
        return WaaSObjectConverter.convertToPolicyDto(waaSService.updatePolicy(existingPolicy));
    }
 
    //==============levels start from here=================
    /**
     * Returns the list of suspension levels owned by the owner with given policy id.
     *
     * @param   req        The HttpServlet request object. Cannot be null.
     * @param   username   User name used for filtering the policies. It is optional.
     *
     * @return  The list of levels filtered by user and owner.
     */
     @GET
     @Produces(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level")
     @Description("Returns all levels with policy id.")
     public List<SuspensionLevel> getLevels(@Context HttpServletRequest req,
    		 @PathParam("pid") BigInteger policyId,
    		 @QueryParam("username") String username) {
    	 
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for querying levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to query suspension levels for this policy!", Status.BAD_REQUEST);
    	 }
    	 
    	 if(username!=null && !existingPolicy.getUsers().contains(username)){
    		 throw new WebApplicationException("Query user doesn't subject to this policy!", Status.BAD_REQUEST);
    	 }
    	 
         List<SuspensionLevel> result = new ArrayList<SuspensionLevel>();
         List<com.salesforce.dva.argus.entity.SuspensionLevel> levels = null;
         
         levels = waaSService.getLevels(existingPolicy);
         
         if(levels == null || levels.isEmpty())
        	 	throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);                
         
         result.addAll(WaaSObjectConverter.convertToLevelDtos(levels));
         return result;
     }
     
     
     
     /**
      * Creates  new levels for existing policy.
      *
      * @param   req       	The HttpServlet request object. Cannot be null.
      * @param   pid  		The policy id. Cannot be null.
      * @param 	 levels		New levels to be created. Cannot be null.
      *
      * @return  Created suspensionlevel objects.
      *
      * @throws  WebApplicationException  The exception with 400 status will be thrown if the policy object is null.
      */
     @PUT
     @Produces(MediaType.APPLICATION_JSON)
     @Consumes(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level")
     @Description("Creates levels for a specific policy.")
     public List<SuspensionLevel> createLevels(@Context HttpServletRequest req, 
    		 @PathParam("pid") BigInteger policyId,
    		 List<SuspensionLevel> levels) {
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 if(levels == null || levels.isEmpty()){
      		throw new WebApplicationException("Null suspension level objects cannot be created.", Status.BAD_REQUEST);
      	}
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for querying levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to query suspension levels for this policy!", Status.BAD_REQUEST);
    	 }    	 
    	 
         
         List<com.salesforce.dva.argus.entity.SuspensionLevel> levelEntities = new ArrayList<com.salesforce.dva.argus.entity.SuspensionLevel>();
		for (SuspensionLevel level : levels) {
			com.salesforce.dva.argus.entity.SuspensionLevel newLevel = new com.salesforce.dva.argus.entity.SuspensionLevel(
					remoteUser, existingPolicy, level.getLevelNumber(), level.getInfractionCount(),
					level.getSuspensionTime());
			levelEntities.add(newLevel);
		}
        
         return WaaSObjectConverter.convertToLevelDtos(waaSService.createLevels(levelEntities));     	
     }
     
     /**
      * Deletes levels.
      *
      * @param   req     The HttpServlet request object. Cannot be null.
      * @param   pid  	The policy Id. Cannot be null and must be a positive non-zero number.
      *
      * @return  REST response indicating whether the suspension levels deletion was successful.
      *
      * @throws  WebApplicationException  The exception with 404 status will be thrown if an policy does not exist.
      */
     @DELETE
     @Produces(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level")
     @Description("Deletes the all the policies owned by this user.")
     public Response deleteLevels(@Context HttpServletRequest req,
    		 @PathParam("pid") BigInteger policyId) {
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for deleting levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to delete suspension levels for this policy!", Status.BAD_REQUEST);
    	 }
    	 
         
         List<com.salesforce.dva.argus.entity.SuspensionLevel> levels = waaSService.getLevels(existingPolicy);
         
         if(levels == null || levels.isEmpty())
        	 	throw new WebApplicationException("No suspension levels for this policy, no need to delete!", Response.Status.NOT_FOUND);                
         
       
        waaSService.deleteLevels(levels);
        
        return Response.status(Status.OK).build();
     }
     
     /**
      * Updates existing levels for given policy id.
      *
      * @param   req       The HttpServlet request object. Cannot be null.
      * @param   policies   Policies objects. Cannot be null.
      *
      * @return  Updated policy objects.
      *
      * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert does not exist.
      */
     @POST
     @Produces(MediaType.APPLICATION_JSON)
     @Consumes(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level")
     @Description("Updates policy objects")
     public List<SuspensionLevel> updateLevels(@Context HttpServletRequest req, 
    		 @PathParam("pid") BigInteger policyId,
     		List<SuspensionLevel> levels){
    	 
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 if(levels == null || levels.isEmpty()){
      		throw new WebApplicationException("Null suspension level objects cannot be created.", Status.BAD_REQUEST);
      	}
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for updating levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to update suspension levels for this policy!", Status.BAD_REQUEST);
    	 }    	 
    	 
         List<SuspensionLevel> result = new ArrayList<SuspensionLevel>();
         
		for (SuspensionLevel newLevel : levels) {
			com.salesforce.dva.argus.entity.SuspensionLevel oldLevel = waaSService.getLevel(existingPolicy, newLevel.getId());
			if(oldLevel == null){
				throw new WebApplicationException("Suspension level for" +  newLevel.getId() + "doesn't exist. Please create it first!", Response.Status.NOT_FOUND);
			}
			copyProperties(oldLevel, newLevel);
			oldLevel.setModifiedBy(remoteUser);
			result.add(WaaSObjectConverter.convertToLevelDto(waaSService.updateLevel(oldLevel)));
		}
        
         return result;     	
     }

    //==========================level start from here================================
     /**
      * Finds a suspension level by policy ID and level id.
      *
      * @param   req      	The HttpServlet request object. Cannot be null.
      * @param   pid  	ID of a policy. Cannot be null and must be a positive non-zero number.
      *	@param   levelid   ID of suspension level. Cannot be null and must be a positive non-zero number.
      * @return  Policy
      *
      * @throws  WebApplicationException  The exception with 404 status will be thrown if a policy does not exist.
      */
     @GET
     @Produces(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level/{levelid}")
     @Description("Returns a policy by its ID.")
     public SuspensionLevel getLevel(@Context HttpServletRequest req,
         @PathParam("pid") BigInteger policyId,
         @PathParam("levelid") BigInteger levelId) {
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 if (levelId == null || levelId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Suspension Level Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for querying levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to query suspension levels for this policy!", Status.BAD_REQUEST);
    	 }
    	 
    	 SuspensionLevel result = null;
    	 com.salesforce.dva.argus.entity.SuspensionLevel level = waaSService.getLevel(existingPolicy, levelId);
    	 if(level == null) {
    		 throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    	 }
    	 
         result = WaaSObjectConverter.convertToLevelDto(level);
         return result;
     }
     
     /**
      * Deletes suspension level.
      *
      * @param   req     The HttpServlet request object. Cannot be null.
      * @param   pid  	The policy Id. Cannot be null and must be a positive non-zero number.
      *	@param   levelid	The suspension level id. Cannot be null and must be a positive non-zero number.
      *
      * @return  REST response indicating whether the suspension level deletion was successful.
      *
      * @throws  WebApplicationException  The exception with 404 status will be thrown if a suspension level does not exist.
      */
     @DELETE
     @Produces(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level/{levelid}")
     @Description("Deletes the suspension level with policy id and level id.")
     public Response deleteLevel(@Context HttpServletRequest req,
         @PathParam("pid") BigInteger policyId,
         @PathParam("levelid") BigInteger levelId) {
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 if (levelId == null || levelId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Suspension Level Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for deleting levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to delete suspension levels for this policy!", Status.BAD_REQUEST);
    	 }
    	 
    	 com.salesforce.dva.argus.entity.SuspensionLevel level = waaSService.getLevel(existingPolicy, levelId);
    	 if(level == null) {
    		 throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    	 }
    	 
        waaSService.deleteLevel(level);
        return Response.status(Status.OK).build();
     }
     
     
     /**
      * Updates existing suspension level with given policy id and level id.
      *
      * @param   req      	The HttpServlet request object. Cannot be null.
      * @param   policyId   	The id of an policy. Cannot be null.
      * @param 	 levelId	The id of suspension level. Cannot be null.
      * @param   policy  	The new suspension level object. Cannot be null.
      *
      * @return  Updated suspension level object.
      *
      * @throws  WebApplicationException  The exception with 404 status will be thrown if the suspension level does not exist.
      */
     @POST
     @Produces(MediaType.APPLICATION_JSON)
     @Consumes(MediaType.APPLICATION_JSON)
     @Path("/policy/{pid}/level/{levelid}")
     @Description("Updates an suspension level having the given policy ID and level ID.")
     public SuspensionLevel updateLevel(@Context HttpServletRequest req,
         @PathParam("pid") BigInteger policyId, 
         @PathParam("levelid") BigInteger levelId,
         SuspensionLevel level) {
    	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 if (levelId == null || levelId.compareTo(BigInteger.ZERO) < 1) {
             throw new WebApplicationException("Suspension Level Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
         }
    	 
    	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
    	 if(existingPolicy == null){
    		 throw new WebApplicationException("Policy doesn't exist for querying levels!", Status.BAD_REQUEST);
    	 }
    	 
    	 PrincipalUser remoteUser = getRemoteUser(req);
    	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
    		 throw new WebApplicationException("Remote user doesn't have priveilege to update suspension levels for this policy!", Status.BAD_REQUEST);
    	 }
    	 
    	 SuspensionLevel result = null;
    	 com.salesforce.dva.argus.entity.SuspensionLevel oldLevel = waaSService.getLevel(existingPolicy, levelId);
    	 if(oldLevel == null) {
    		 throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    	 }
    	 
    	 copyProperties(oldLevel, level);
    	 oldLevel.setModifiedBy(remoteUser);
         result = WaaSObjectConverter.convertToLevelDto(waaSService.updateLevel(oldLevel));
         return result;    	
     }
     //=================infraction(s) start from here==================
     
     /**
      * Returns all infractions with given policy id.
      *
      * @param   req        The HttpServlet request object. Cannot be null.
      * @param   username   User name used for filtering the infraction. It is optional.
      *
      * @return  The list of infraction filtered by policy and user.
      */
      @GET
      @Produces(MediaType.APPLICATION_JSON)
      @Path("/policy/{pid}/infraction")
      @Description("Returns all infractions with policy id.")
      public List<Infraction> getInfractions(@Context HttpServletRequest req,
     		 @PathParam("pid") BigInteger policyId,
     		 @QueryParam("username") String username) {
    	  if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
              throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
          }
     	 
     	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
     	 if(existingPolicy == null){
     		 throw new WebApplicationException("Policy doesn't exist for querying infrations!", Status.BAD_REQUEST);
     	 }    	 
   	 
     	 PrincipalUser remoteUser = getRemoteUser(req);
     	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
     		 throw new WebApplicationException("Remote user doesn't have priveilege to query infrations for this policy!", Status.BAD_REQUEST);
     	 }
     	 
     	 if(username!=null && !existingPolicy.getUsers().contains(username)){
     		 throw new WebApplicationException("Query user doesn't subject to this policy!", Status.BAD_REQUEST);
     	 }
     	 
          List<Infraction> result = new ArrayList<Infraction>();
          List<com.salesforce.dva.argus.entity.Infraction> infractions = null;
          
        
          if (username != null && !username.isEmpty()){
          	infractions = waaSService.getInfractionsByPolicyAndUserName(existingPolicy, username);
          } else {
          	infractions = waaSService.getInfractions(existingPolicy);
          }  
          
          if(infractions == null || infractions.isEmpty())
         	 	throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
                 
          
          result.addAll(WaaSObjectConverter.convertToInfractionDtos(infractions));
          return result;
      }
      /**
       * Finds a infraction by policy ID and infracction id.
       *
       * @param   req      	The HttpServlet request object. Cannot be null.
       * @param   pid  		ID of a policy. Cannot be null and must be a positive non-zero number.
       * @param   iid   	ID of an infraction. Cannot be null and must be a positive non-zero number.
       * @return  Infraction
       *
       * @throws  WebApplicationException  The exception with 404 status will be thrown if a policy does not exist.
       */
      @GET
      @Produces(MediaType.APPLICATION_JSON)
      @Path("/policy/{pid}/infraction/{iid}")
      @Description("Returns an infraction by policy ID and its ID.")
      public Infraction getInfraction(@Context HttpServletRequest req,
          @PathParam("pid") BigInteger policyId,
          @PathParam("iid") BigInteger infractionId) {
     	 if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
              throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
          }
     	 
     	 if (infractionId == null || infractionId.compareTo(BigInteger.ZERO) < 1) {
              throw new WebApplicationException("Infraction Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
          }
     	 
     	 com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);    	 
     	 if(existingPolicy == null){
     		 throw new WebApplicationException("Policy doesn't exist for querying infractions!", Status.BAD_REQUEST);
     	 }
     	 
     	 PrincipalUser remoteUser = getRemoteUser(req);
     	 if(!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())){
     		 throw new WebApplicationException("Remote user doesn't have priveilege to query any infraction for this policy!", Status.BAD_REQUEST);
     	 }
     	 
     	 Infraction result = null;
     	 com.salesforce.dva.argus.entity.Infraction infraction = waaSService.getInfraction(existingPolicy, infractionId);
     	 if(infraction == null) {
     		 throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
     	 }
     	 
          result = WaaSObjectConverter.convertToInfractionDto(infraction);
          return result;
      }
     //=============Suspension start from here=================
      
      /**
       * Returns all infractions with given policy id and user id if suspension happens.
       *
       * @param   req      	The HttpServlet request object. Cannot be null.
       * @param   pid  		ID of a policy. Cannot be null and must be a positive non-zero number.
       * @param   uid   	ID of an warden user. Cannot be null and must be a positive non-zero number.
       * @return  Infraction list
       *
       */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/policy/{pid}/user/{uid}/suspension")
	@Description("Returns all infractions with policy id and user id if suspension happens.")
	public List<Infraction> getSuspensionForUserAndPolicy(@Context HttpServletRequest req, 
			@PathParam("pid") BigInteger policyId,
			@PathParam("uid") BigInteger userId) {
		
		List<Infraction> result =this.getInfractionsForUserAndPolicy(req, userId, policyId);
		//filter infractions based on expiration time if it is set a value
        if(result != null && !result.isEmpty()){
        	result = result.stream().filter(i ->(i.getExpirationTimestamp() != null) && (!i.getExpirationTimestamp().equals(0L))).collect(Collectors.toList());
        }
		if (result == null || result.isEmpty()) {
			throw new WebApplicationException("This user doesn't has suspension for this policy.",
					Status.BAD_REQUEST);
		}
		
		return result;
	}
	
	 /**
     * Deletes suspension for user.
     *
     * @param   req     The HttpServlet request object. Cannot be null.
     * @param   pid  	The policy Id. Cannot be null and must be a positive non-zero number.
     * @param 	uid		The user Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  REST response indicating whether the suspension deletion was successful.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an policy does not exist.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy/{pid}/user/{uid}/suspension")
    @Description("Deletes all infractions with policy id and user id if suspension happens.")
    public Response deleteSuspensionForUser(@Context HttpServletRequest req,
   		 @PathParam("pid") BigInteger policyId,
   		 @PathParam("uid") BigInteger userId) {
   	
   	
	
	List<BigInteger> resultIds = new ArrayList<BigInteger>();
	
	List<Infraction> result =this.getInfractionsForUserAndPolicy(req, userId, policyId);
	//filter infractions based on expiration time if it is set a value
    if(result != null && !result.isEmpty()){
    	resultIds = result.stream().filter(i ->(i.getExpirationTimestamp() != null) && (!i.getExpirationTimestamp().equals(0L))).map(i -> i.getId()).collect(Collectors.toList());
    }
	if (resultIds != null && !resultIds.isEmpty()) {
		waaSService.deleteInfractionByIds(resultIds);
	}

	 return Response.status(Status.OK).build();
    }
    
	/**
     * Returns all infractions with given policy id if suspension happens.
     *
     * @param   req      	The HttpServlet request object. Cannot be null.
     * @param   pid  		ID of a policy. Cannot be null and must be a positive non-zero number.
     * @return  Infraction list
     *
     */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/policy/{pid}/suspension")
	@Description("Returns all infractions with policy id and user id if suspension happens.")
	public List<Infraction> getSuspension(@Context HttpServletRequest req, 
			@PathParam("pid") BigInteger policyId) {
		
		List<Infraction> result = this.getInfractions(req, policyId, null);
		//filter infractions based on expiration time if it is set a value
	    if(result != null && !result.isEmpty()){
	    	result = result.stream().filter(i ->(i.getExpirationTimestamp() != null) && (!i.getExpirationTimestamp().equals(0L))).collect(Collectors.toList());
	    }
		return result;
	}
	
	 /**
     * Deletes suspension for policy.
     *
     * @param   req     The HttpServlet request object. Cannot be null.
     * @param   pid  	The policy Id. Cannot be null and must be a positive non-zero number.
     *
     * @return  REST response indicating whether the suspension deletion was successful.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an policy does not exist.
     */
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/policy/{pid}/suspension")
	@Description("Deletes all infractions with policy id and user id if suspension happens.")
	public Response deleteSuspension(@Context HttpServletRequest req, @PathParam("pid") BigInteger policyId) {

		List<BigInteger> resultIds = new ArrayList<BigInteger>();
		List<Infraction> result = this.getInfractions(req, policyId, null);
		// filter infractions based on expiration time if it is set a value
		if (result != null && !result.isEmpty()) {
			resultIds = result.stream()
					.filter(i -> (i.getExpirationTimestamp() != null) && (!i.getExpirationTimestamp().equals(0L)))
					.map(i -> i.getId()).collect(Collectors.toList());
		}
		if (resultIds != null && !resultIds.isEmpty()) {
			waaSService.deleteInfractionByIds(resultIds);
		}

		return Response.status(Status.OK).build();
	}
    //================user get starts from here===============================
    /**
     * Returns all warden users, only admin have this privilege
     *
     * @param   req     The HTTP request.
     
     * @return  The corresponding warden user DTOs.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user")
    @Description("Returns the user having the given ID.")
    public List<WardenUser> getUsers(@Context HttpServletRequest req) {
        
    	PrincipalUser remoteUser = getRemoteUser(req);
      	 if(!remoteUser.isPrivileged() ){
      		 throw new WebApplicationException("Remote user doesn't have privilege to query all principal users!", Status.BAD_REQUEST);
      	 }
       
      	 List<WardenUser> result = new ArrayList<WardenUser>();
      	 List<PrincipalUser> users = userService.getPrincipalUsers();
      	 
      	 if(users == null || users.isEmpty()){
      		throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
      	 };
      	 
      	result.addAll(WaaSObjectConverter.convertToWardenUserDtos(users));
        return result;
    }
    /**
     * Returns the user having the given ID.
     *
     * @param   req     The HTTP request.
     * @param   userId  The user ID to retrieve
     *
     * @return  The corresponding warden user DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}")
    @Description("Returns the user having the given ID.")
    public WardenUser getUserById(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId) {
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        PrincipalUser remoteUser = getRemoteUser(req);
      	 if(!remoteUser.isPrivileged() && !remoteUser.getId().equals(userId)){
      		 throw new WebApplicationException("Remote user doesn't have priveilege to query this user with id!", Status.BAD_REQUEST);
      	 }
      	 
      	
     	PrincipalUser user = userService.findUserByPrimaryKey(userId);
     	if (user == null) {
     		throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);     	
        }
        
     	return WaaSObjectConverter.convertToWardenUserDto(user);
    }
    
    
    /**
     * Returns all the policies for a specific user based on user id.
     *
     * @param   req     The HTTP request.
     * @param   uid  	The user ID to retrieve policies
     *
     * @return  The policy list.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}/policy")
    @Description("Returns the policies for this user.")
    public List<Policy> getPoliciesForUser(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId) {
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        PrincipalUser remoteUser = getRemoteUser(req);
      	 if(!remoteUser.isPrivileged() && !remoteUser.getId().equals(userId)){
      		 throw new WebApplicationException("Remote user doesn't have priveilege to query policies with this user id!", Status.BAD_REQUEST);
      	 }
      	
      	PrincipalUser user = userService.findUserByPrimaryKey(userId);
     	if (user == null) {
     		throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);     	
        }
      	 
      	return this.getPolicies(req, user.getUserName());
    }
    
    /**
     * Returns all the infractions for a specific user with user id and policy.
     *
     * @param   req     The HTTP request.
     * @param   uid  	The user ID to retrieve infrations
     * @param 	pid		The policy ID to retrieve infractions
     *
     * @return  The infraction list.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}/policy/{pid}/infractions")
    @Description("Returns the policies for this user.")
    public List<Infraction> getInfractionsForUserAndPolicy(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId,
        @PathParam("pid") final BigInteger policyId) {
    	if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}

		if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}

		com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);
		if (existingPolicy == null) {
			throw new WebApplicationException("Policy doesn't exist for querying suspension!", Status.BAD_REQUEST);
		}

		PrincipalUser existingUser = userService.findUserByPrimaryKey(userId);
		
		if (existingUser == null) {
			throw new WebApplicationException("User doesn't exist for querying infractions!", Status.BAD_REQUEST);
		}

		

		PrincipalUser remoteUser = getRemoteUser(req);
     	 if(!remoteUser.isPrivileged() && !remoteUser.getId().equals(userId)){
     		 throw new WebApplicationException("Remote user doesn't have priveilege to query anything under this user!", Status.BAD_REQUEST);
     	 }else if (!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())) {
			throw new WebApplicationException("Remote user doesn't have priveilege to query infraction for this policy!", Status.BAD_REQUEST);
		}
     	
		
		if (existingUser.getUserName() != null && !existingPolicy.getUsers().contains(existingUser.getUserName())) {
			throw new WebApplicationException("Query user doesn't subject to this policy!", Status.BAD_REQUEST);
		}

		List<Infraction> result = new ArrayList<Infraction>();
		List<com.salesforce.dva.argus.entity.Infraction> infractions = waaSService.getInfractionsByPolicyAndUserName(existingPolicy, existingUser.getUserName());


		if (infractions != null && !infractions.isEmpty()) {
			result.addAll(WaaSObjectConverter.convertToInfractionDtos(infractions));
		}

		return result;
    }
    
    /**
     * Returns all the infractions for a specific user based on user id.
     *
     * @param   req     The HTTP request.
     * @param   uid  	The user ID to retrieve policies
     *
     * @return  The policy list.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}/infraction")
    @Description("Returns the infractions for this user.")
    public List<Infraction> getInfractionsForUser(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId) {
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        PrincipalUser remoteUser = getRemoteUser(req);
      	 if(!remoteUser.isPrivileged() && !remoteUser.getId().equals(userId)){
      		 throw new WebApplicationException("Remote user doesn't have priveilege to query policies with this user id!", Status.BAD_REQUEST);
      	 }
      	 
      	PrincipalUser user = userService.findUserByPrimaryKey(userId);
     	if (user == null) {
     		throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);     	
        }
      	 
     	List<Infraction> result = new ArrayList<Infraction>();
		List<com.salesforce.dva.argus.entity.Infraction> infractions = waaSService.getInfractionsByUser(user);


		if (infractions != null && !infractions.isEmpty()) {
			result.addAll(WaaSObjectConverter.convertToInfractionDtos(infractions));
		}

		return result;
    }
    //===============metrics starts here=============
    /**
     * Submits externally collected metric data.
     *
     * @param   req         The HTTP request.
     * @param   metrics  The metric DTOs to submit.
     *
     * @return  The metric dtos.
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy/{pid}/user/{uid}/metirc")
    @Description("Submits externally collected metric data.")
    public Map<String, Object> createMetrics(@Context HttpServletRequest req,
            @PathParam("pid") final BigInteger policyId,
            @PathParam("uid") final BigInteger userId,
            List<Metric> metricDtos) {
    	if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}

		if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}

		com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);
		if (existingPolicy == null) {
			throw new WebApplicationException("Policy doesn't exist for querying metrics!", Status.BAD_REQUEST);
		}

		PrincipalUser existingUser = userService.findUserByPrimaryKey(userId);
		if (existingUser == null) {
			throw new WebApplicationException("User doesn't exist for querying metrics!", Status.BAD_REQUEST);
		}

		PrincipalUser remoteUser = getRemoteUser(req);
     	 if(!remoteUser.isPrivileged() && !remoteUser.getId().equals(userId)){
     		 throw new WebApplicationException("Remote user doesn't have priveilege to query anything under this user!", Status.BAD_REQUEST);
     	 }else if (!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())) {
			throw new WebApplicationException("Remote user doesn't have privilege to query metrics for this policy!", Status.BAD_REQUEST);
		}
     	 
		if (existingUser != null && !existingPolicy.getUsers().contains(existingUser.getUserName())) {
			throw new WebApplicationException("Query user doesn't subject to this policy!", Status.BAD_REQUEST);
		}
		
        List<com.salesforce.dva.argus.entity.Metric> legalMetrics = new ArrayList<>();
        List<Metric> illegalMetrics = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (Metric metricDto : metricDtos) {
            try {
               com.salesforce.dva.argus.entity.Metric metric = new com.salesforce.dva.argus.entity.Metric(metricDto.getScope(), metricDto.getMetric());

                copyProperties(metric, metricDto);
                legalMetrics.add(metric);
            } catch (Exception e) {
                illegalMetrics.add(metricDto);
                errorMessages.add(e.getMessage());
            }
        }
        waaSService.creatMetrics(remoteUser, legalMetrics);

        Map<String, Object> result = new HashMap<>();

        result.put("Success", legalMetrics.size() + " metrics");
        result.put("Error", illegalMetrics.size() + " metrics");
        result.put("Error Messages", errorMessages);
        return result;
    }
  
    /**
     * Returns all the metrics for a specific user with user id and policy.
     *
     * @param   req     The HTTP request.
     * 
     * @param 	pid		The policy ID to retrieve infractions
     * @param   uid  	The user ID to retrieve infrations
     *
     * @return  The metric list.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/policy/{pid}/user/{uid}/metric")
    @Description("Returns the metric for this user and policy.")
    public List<Metric> getMetricsForPolicyAndUser(@Context HttpServletRequest req,
        @PathParam("pid") final BigInteger policyId,
        @PathParam("uid") final BigInteger userId) {
    	return this.getMetricsForUserAndPolicy(req, userId, policyId);
    }
    /**
     * Returns all the metrics for a specific user with user id and policy.
     *
     * @param   req     The HTTP request.
     * @param   uid  	The user ID to retrieve infrations
     * @param 	pid		The policy ID to retrieve infractions
     *
     * @return  The metric list.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}/policy/{pid}/metric")
    @Description("Returns the metric for this user and policy.")
    public List<Metric> getMetricsForUserAndPolicy(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId,
        @PathParam("pid") final BigInteger policyId) {
    	if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}

		if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}

		com.salesforce.dva.argus.entity.Policy existingPolicy = waaSService.getPolicy(policyId);
		if (existingPolicy == null) {
			throw new WebApplicationException("Policy doesn't exist for querying metrics!", Status.BAD_REQUEST);
		}

		PrincipalUser existingUser = userService.findUserByPrimaryKey(userId);
		if (existingUser == null) {
			throw new WebApplicationException("User doesn't exist for querying metrics!", Status.BAD_REQUEST);
		}

		PrincipalUser remoteUser = getRemoteUser(req);
     	 if(!remoteUser.isPrivileged() && !remoteUser.getId().equals(userId)){
     		 throw new WebApplicationException("Remote user doesn't have priveilege to query anything under this user!", Status.BAD_REQUEST);
     	 }else if (!remoteUser.isPrivileged() && !existingPolicy.getOwners().contains(remoteUser.getUserName())) {
			throw new WebApplicationException("Remote user doesn't have privilege to query metrics for this policy!", Status.BAD_REQUEST);
		}
     	 
		if (existingUser != null && !existingPolicy.getUsers().contains(existingUser.getUserName())) {
			throw new WebApplicationException("Query user doesn't subject to this policy!", Status.BAD_REQUEST);
		}

		List<Metric> result = new ArrayList<Metric>();
		List<com.salesforce.dva.argus.entity.Metric> metrics = waaSService.getMetrics(existingPolicy, existingUser);


		if (metrics != null && !metrics.isEmpty()) {
			result.addAll(WaaSObjectConverter.convertToMetricDtos(metrics));
		}

		return result;
    }
    
    /**
     * Returns all the suspensions for a specific user based on user id.
     *
     * @param   req     The HTTP request.
     * @param   uid  	The user ID to retrieve policies
     *
     * @return  The suspension list.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}/suspension")
    @Description("Returns the infractions for this user.")
    public List<Infraction> getSuspensionsForUser(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId) {
        
    	List<Infraction> result = this.getInfractionsForUser(req, userId);
    	//filter infractions based on expiration time if it is set a value
        if(result != null && !result.isEmpty()){
        	result = result.stream().filter(i ->(i.getExpirationTimestamp() != null) && (!i.getExpirationTimestamp().equals(0L))).collect(Collectors.toList());
        }
    	if (result == null || result.isEmpty()) {
    		throw new WebApplicationException("This user doesn't has suspension.",
    				Status.BAD_REQUEST);
    	}
		return result;
    }
    /**
     * Returns one suspension for a specific user based on user id and suspension id.
     *
     * @param   req     The HTTP request.
     * @param   uid  	The user ID to retrieve suspension 
     * @param 	sid		The suspension ID to retrieve suspension
     *
     * @return  The suspension .
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/user/{uid}/suspension/{sid}")
    @Description("Returns the suspension for this user and suspension id.")
    public Infraction getSuspensionForUser(@Context HttpServletRequest req,
        @PathParam("uid") final BigInteger userId,
        @PathParam("sid") final BigInteger suspensionId) {
        
        
        if (suspensionId == null || suspensionId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Suspension Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        
        Infraction result = null;
        List<Infraction> suspensions = this.getSuspensionsForUser(req, userId);
    	//filter infractions based on expiration time if it is set a value
        if(suspensions != null && !suspensions.isEmpty()){
        	result = suspensions.stream().filter(i -> i.getId().equals(suspensionId)).collect(Collectors.toList()).get(0);
        }
    	if (result == null) {
    		throw new WebApplicationException("This user doesn't has this suspension.",
    				Status.BAD_REQUEST);
    	}
		return result;
    }


  

    

    //===================new api ends here=====================
    /**
     * Returns the list of infractions for a given policy id and userName.
     * Internal use only, not customer-facing
     *
     * @param   req      	The HttpServlet request object. Cannot be null.
     * @param   policyId  	The policy Id for which infractions are requested. Cannot be null and must be a positive non-zero number.
     *
     * @return  List of infractions.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an policy does not exist.
     */
//    @GET
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/{policyId}/username/infractions")
//    @Description("Returns all infractions for the given policy ID and user name.")
//    public List<Infraction> getInfractionsByPolicyIdAndUser(@Context HttpServletRequest req,
//        @PathParam("policyId") BigInteger policyId,
//        @PathParam("userName") String userName) {
//        if (policyId == null || policyId.compareTo(BigInteger.ZERO) < 1) {
//            throw new WebApplicationException("Policy Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
//        }
//        if (userName == null || userName.isEmpty()) {
//			throw new WebApplicationException("User name cannot be null or an empty string.", Status.BAD_REQUEST);
//		}        
//        
//        com.salesforce.dva.argus.entity.Policy policy = waaSService.getPolicy(policyId);
//        if (policy != null) {
//        	List<com.salesforce.dva.argus.entity.Infraction> infractions = waaSService.getInfractions(userName, policy);
//            return com.salesforce.dva.argus.entity.Infraction.transformToDto(infractions);
//        } 
//       
//        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
//    }
    
//    /**
//     * Updates the threshold of existing policy.
//     *
//     * @param   req       	The HttpServlet request object. Cannot be null.
//     * @param   serviceName The service associated who owns the policy. Cannot be null.
//     * @param   policyName  The policy Name. Cannot be null.
//     *
//     * @return  Updated policy object.
//     *
//     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert does not exist.
//     */
//	@PUT
//	@Produces(MediaType.APPLICATION_JSON)
//	@Consumes(MediaType.APPLICATION_JSON)
//	@Path("/{serviceName}/{policyName}")
//	@Description("Updates an policy having the policy name for specific service.")
//	public Policy updatePolicy(@Context HttpServletRequest req,
//			@PathParam("serviceName") String serviceName, @PathParam("policyName") String policyName,
//			Policy policyDto) {
//		if (serviceName == null || serviceName.isEmpty()) {
//			throw new WebApplicationException("Service name cannot be null or an empty string.", Status.BAD_REQUEST);
//		}
//		if (policyName == null || policyName.isEmpty()) {
//			throw new WebApplicationException("Policy name cannot be null or an empty string.", Status.BAD_REQUEST);
//		}
//		if (policyDto == null) {
//			throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
//		}
//
//		PrincipalUser owner = validateAndGetOwner(req, getRemoteUser(req).getUserName());
//
//		com.salesforce.dva.argus.entity.Policy oldPolicy = waaSService.getPolicy(policyName, serviceName);
//
//		if (oldPolicy == null) {
//			// create a policy based on policy dto
//			oldPolicy = new com.salesforce.dva.argus.entity.Policy(owner, policyDto.getService(), policyDto.getName(),
//					policyDto.getOwner(), policyDto.getUser(),
//					TriggerType.fromString(policyDto.getTriggerType().name()), policyDto.getAggregator().name(),
//					policyDto.getThreshold(), policyDto.getTimeUnit(), policyDto.getDefaultValue(),
//					policyDto.getCronEntry());
//			
//		}
//
//		PrincipalUser policyOwner = userService.findUserByUsername(oldPolicy.getOwners().get(0));
//		validateResourceAuthorization(req, policyOwner, owner);
//		
//		oldPolicy.setModifiedBy(getRemoteUser(req));
//		String user = policyDto.getUser().get(0);
//		double value = policyDto.getThreshold().get(0);
//		
//		return com.salesforce.dva.argus.entity.Policy.transformToDto(waaSService.upsertPolicy(user, oldPolicy, value));		
//	}
//
//	/**
//	 * Updates the suspension level.
//	 *
//	 * @param req					The HttpServlet request object. Cannot be null.
//	 * @param policyName			The policy name. Cannot be null.
//	 * @param suspensionLevel		The suspension level number. Cannot be null.
//	 * @param suspensionLevelDto	New suspensionLevel object. Cannot be null.
//	 *
//	 * @return Updated suspensionLevel object.
//	 *
//	 * @throws WebApplicationException	The exception with 404 status will be thrown if either an policy or suspensionLevel do not exist.
//	 */
//	@PUT
//	@Produces(MediaType.APPLICATION_JSON)
//	@Consumes(MediaType.APPLICATION_JSON)
//	@Path("/{serviceName}/{policyName}/suspensionLevels/{suspensionLevelNumber}")////policyId
//	@Description("Updates a suspension level having the given suspensionLevelNumber if associated with the given policy name.")
//	public SuspensionLevel updateSuspensionLevelByLevel(@Context HttpServletRequest req,
//			@PathParam("serviceName") String serviceName, @PathParam("policyName") String policyName,
//			@PathParam("suspensionLevelNumber") int suspensionLevelNumber,
//			SuspensionLevel suspensionLevelDto) {
//		if (serviceName == null || serviceName.isEmpty()) {
//			throw new WebApplicationException("Service name cannot be null or an empty string.", Status.BAD_REQUEST);
//		}
//		if (policyName == null || policyName.length() == 0) {
//			throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.",
//					Status.BAD_REQUEST);
//		}
//		if (suspensionLevelNumber <= 0) {
//			throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.",
//					Status.BAD_REQUEST);
//		}
//		if (suspensionLevelDto == null) {
//			throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
//		}
//		if(suspensionLevelDto.getLevelNumber() != suspensionLevelNumber){
//			throw new WebApplicationException("The provided level numaber and suspension level dto object doesn't match!", Status.BAD_REQUEST);
//		}
//
//		com.salesforce.dva.argus.entity.Policy oldPolicy = waaSService.getPolicy(policyName, serviceName);
//		
//		//different from handling put policy, cause suspensionlevel can't be created without an existing policy, so can't create a new policy here
//		if (oldPolicy == null) {
//			throw new WebApplicationException("No corresponding Policy found.", Response.Status.NOT_FOUND);
//		}
//				
//		return com.salesforce.dva.argus.entity.SuspensionLevel.transformToDto(
//				waaSService.updateSuspensionLevel(oldPolicy, suspensionLevelNumber, suspensionLevelDto.getInfractionCount(), suspensionLevelDto.getSuspensionTime().longValue()));
//	}
//	
//	/**
//	 * 
//	 * 
//	 * TODO
//     * Submits collected metric data from client.
//     *
//     * @param   req         The HTTP request.
//     * @param   metricDtos  The metric DTOs to submit.
//     *
//     * @return  The number of metrics that were submitted, and the number of errors encountered.
//     */
//    @POST
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/metrics")/////policyId + username->/policy/{policyId}/
//    @Description("Submits collected metric data from client.")
//    public Map<String, Object> submitMetrics(@Context HttpServletRequest req, final List<MetricDto> metricDtos) {
//        PrincipalUser remoteUser = getRemoteUser(req);
//
//        SystemAssert.requireArgument(metricDtos != null, "Cannot submit null timeseries metrics list.");
//
//        List<Metric> legalMetrics = new ArrayList<>();
//        List<MetricDto> illegalMetrics = new ArrayList<>();
//        List<String> errorMessages = new ArrayList<>();
//
//        for (MetricDto metricDto : metricDtos) {
//            try {
//                Metric metric = new Metric(metricDto.getScope(), metricDto.getMetric());
//
//                copyProperties(metric, metricDto);
//                legalMetrics.add(metric);
//            } catch (Exception e) {
//                illegalMetrics.add(metricDto);
//                errorMessages.add(e.getMessage());
//            }
//        }
//        
//        waaSMonitorService.submitMetrics(remoteUser, legalMetrics);
//
//        Map<String, Object> result = new HashMap<>();
//
//        result.put("Success", legalMetrics.size() + " metrics");
//        result.put("Error", illegalMetrics.size() + " metrics");
//        result.put("Error Messages", errorMessages);
//        return result;
//    }
//    /**
//     * Returns the warden user having the given username.
//     *
//     * @param   req       The HTTP request.
//     * @param   userName  The username to retrieve.
//     *
//     * @return  The warden user DTO.
//     *
//     * @throws  WebApplicationException  If an error occurs.
//     * 
//     */
//    @GET
//    @Produces(MediaType.APPLICATION_JSON)
//    @Path("/username/{username}")
//    @Description("Returns the user having the given username.")
//    public WardenUser getUserByUsername(@Context HttpServletRequest req,
//        @PathParam("username") final String userName) {
//        if (userName == null || userName.isEmpty()) {
//            throw new WebApplicationException("Username cannot be null or empty.", Status.BAD_REQUEST);
//        }
//
//        PrincipalUser remoteUser = validateAndGetOwner(req, null);
//        PrincipalUser user = userService.findUserByUsername(userName);
//
//        if (user != null) {
//            super.validateResourceAuthorization(req, user, remoteUser);
//            return PrincipalUser.transformToWardenUserDto(user);
//        } else if (!remoteUser.isPrivileged()) {
//            throw new WebApplicationException(Response.Status.FORBIDDEN.getReasonPhrase(), Response.Status.FORBIDDEN);
//        } else {
//            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
//        }
//    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
