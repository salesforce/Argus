package com.salesforce.dva.argus.util;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.beanutils.BeanUtils;

import com.salesforce.dva.argus.entity.*;
import com.salesforce.dva.warden.dto.Entity;

import java.util.List;

public class WaaSObjectConverter implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private BigInteger id;
    private BigInteger createdById;
    private Date createdDate;
    private BigInteger modifiedById;
    private Date modifiedDate;

    //~ Methods **************************************************************************************************************************************

    /**
     * Creates BaseDto object and copies properties from entity object.
     *
     * @param   <D>     BaseDto object type.
     * @param   <E>     Entity type.
     * @param   clazz   BaseDto entity class.
     * @param   entity  entity object.
     *
     * @return  BaseDto object.
     *
     * @throws  WebApplicationException  The exception with 500 status will be thrown.
     */
    public static <D extends Entity, E extends JPAEntity> D createDtoObject(Class<D> clazz, E entity) {
        D result = null;

        try {
            result = clazz.newInstance();
            BeanUtils.copyProperties(result, entity);

            // Now set IDs of JPA entity
            result.setCreatedById(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null);
            result.setModifiedById(entity.getModifiedBy() != null ? entity.getModifiedBy().getId() : null);
        } catch (Exception ex) {
            throw new WebApplicationException("DTO transformation failed.", Status.INTERNAL_SERVER_ERROR);
        }
        return result;
    }
    /**
     * Convert from policy entity to policy dto,
     * reset field: metricName, suspensionLevels
     * 
     * @param policy
     * @return policy dto
     */
    public static com.salesforce.dva.warden.dto.Policy convertToPolicyDto(Policy policy) {
    	if (policy == null) {
            throw new WebApplicationException("Null policy object cannot be converted to Policy Dto object.", Status.INTERNAL_SERVER_ERROR);
        }
    	com.salesforce.dva.warden.dto.Policy result = createDtoObject(com.salesforce.dva.warden.dto.Policy.class, policy);
    	result.setMetricName(policy.getMetricName());
    	result.setSuspensionLevels(policy.getSuspensionLevels().stream().map(s -> s.getId()).collect(Collectors.toList()));
    	return result;
    }
    /**
     * Convert from policy entity list to policy dto list,
     * reset field: metricName, suspensionLevels
     * 
     * @param policy list
     * @return policy dto list
     */
    public static List<com.salesforce.dva.warden.dto.Policy> convertToPolicyDtos(List<Policy> policies){
    	if(policies == null || policies.isEmpty()){
    		throw new WebApplicationException("Null policy object cannot be converted to Policy Dto object.", Status.INTERNAL_SERVER_ERROR);
    	}
    	List<com.salesforce.dva.warden.dto.Policy>  result = new ArrayList<com.salesforce.dva.warden.dto.Policy>();
    	for(Policy p : policies){
    		result.add(convertToPolicyDto(p));
    	}
    	return result;
    }
    
    /*
     * map suspensionLevel entity to suspensionLevel dto
     */
    public static com.salesforce.dva.warden.dto.SuspensionLevel convertToLevelDto(SuspensionLevel suspensionLevel) {
    	if (suspensionLevel == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }
    	com.salesforce.dva.warden.dto.SuspensionLevel result = WaaSObjectConverter.createDtoObject(com.salesforce.dva.warden.dto.SuspensionLevel.class, suspensionLevel);
    	result.setPolicyId(suspensionLevel.getPolicy().getId());
        
        return result;
    }
    public static List<com.salesforce.dva.warden.dto.SuspensionLevel> convertToLevelDtos(
			List<com.salesforce.dva.argus.entity.SuspensionLevel> levels) {
    	if(levels == null || levels.isEmpty()){
    		throw new WebApplicationException("Null suspension level object cannot be converted to Policy Dto object.", Status.INTERNAL_SERVER_ERROR);
    	}
    	List<com.salesforce.dva.warden.dto.SuspensionLevel>  result = new ArrayList<com.salesforce.dva.warden.dto.SuspensionLevel>();
    	for(SuspensionLevel l : levels){
    		result.add(convertToLevelDto(l));
    	}
    	return result;
	}
    /*
     * map infraction entity to infraction dto
     */
    public static com.salesforce.dva.warden.dto.Infraction convertToInfractionDto(Infraction infraction) {
    	if (infraction == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }
    	com.salesforce.dva.warden.dto.Infraction result = WaaSObjectConverter.createDtoObject(com.salesforce.dva.warden.dto.Infraction.class, infraction);
    	result.setPolicyId(infraction.getPolicy().getId());
    	result.setUserId(infraction.getUser().getId());
        
        return result;
    }
    public static List<com.salesforce.dva.warden.dto.Infraction> convertToInfractionDtos(
			List<com.salesforce.dva.argus.entity.Infraction> infractions) {
    	if(infractions == null || infractions.isEmpty()){
    		throw new WebApplicationException("Null infraction object cannot be converted to infraction Dto object.", Status.INTERNAL_SERVER_ERROR);
    	}
    	List<com.salesforce.dva.warden.dto.Infraction>  result = new ArrayList<com.salesforce.dva.warden.dto.Infraction>();
    	for(Infraction i : infractions){
    		result.add(convertToInfractionDto(i));
    	}
    	return result;
	}
    
    /*
     * map infraction entity to infraction dto
     */
    public static com.salesforce.dva.warden.dto.WardenUser convertToWardenUserDto(PrincipalUser principalUser) {
    	if (principalUser == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }
    	com.salesforce.dva.warden.dto.WardenUser result = WaaSObjectConverter.createDtoObject(com.salesforce.dva.warden.dto.WardenUser.class, principalUser);
    	return result;
    }
    public static List<com.salesforce.dva.warden.dto.WardenUser> convertToWardenUserDtos(
			List<com.salesforce.dva.argus.entity.PrincipalUser> principalUsers) {
    	if(principalUsers == null || principalUsers.isEmpty()){
    		throw new WebApplicationException("Null principal Users object cannot be converted to infraction Dto object.", Status.INTERNAL_SERVER_ERROR);
    	}
    	List<com.salesforce.dva.warden.dto.WardenUser>  result = new ArrayList<com.salesforce.dva.warden.dto.WardenUser>();
    	for(PrincipalUser p : principalUsers){
    		result.add(convertToWardenUserDto(p));
    	}
    	return result;
	}
    
  /**
  * Converts a metric entity to a DTO. it is extend from tsdbEntity instead of jpaEntity, convert it seperately
  *
  * @param   metric  The metric to convert.
  *
  * @return  The corresponding DTO.
  *
  * @throws  WebApplicationException  If an error occurs.
  */

 public static com.salesforce.dva.warden.dto.Metric  createToMetricDto(Class<com.salesforce.dva.warden.dto.Metric> clazz, Metric entity) {
	 if (entity == null) {
         throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
     }
	 com.salesforce.dva.warden.dto.Metric result = null;

     try {
         result = clazz.newInstance();
         BeanUtils.copyProperties(result, entity);

     } catch (Exception ex) {
         throw new WebApplicationException("Metric DTO transformation failed.", Status.INTERNAL_SERVER_ERROR);
     }
     return result;
 }


 public static List<com.salesforce.dva.warden.dto.Metric> convertToMetricDtos(
			List<com.salesforce.dva.argus.entity.Metric> metrics) {
 	if(metrics == null || metrics.isEmpty()){
 		throw new WebApplicationException("Null metrics object cannot be converted to infraction Dto object.", Status.INTERNAL_SERVER_ERROR);
 	}
 	List<com.salesforce.dva.warden.dto.Metric>  result = new ArrayList<com.salesforce.dva.warden.dto.Metric>();
 	for(Metric m : metrics){
 		result.add(convertToMetricDto(com.salesforce.dva.warden.dto.Metric.class,m));
 	}
 	return result;
	}

    //~ Methods **************************************************************************************************************************************

    private static com.salesforce.dva.warden.dto.Metric convertToMetricDto(
		Class<com.salesforce.dva.warden.dto.Metric> class1, Metric m) {
	// TODO Auto-generated method stub
	return null;
}
	/**
     * Returns the entity ID.
     *
     * @return  The entity ID.
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Specifies the entity ID.
     *
     * @param  id  The entity ID.
     */
    public void setId(BigInteger id) {
        this.id = id;
    }

    /**
     * Returns the created date.
     *
     * @return  The created date.
     */
    public Date getCreatedDate() {
        return createdDate;
    }

    /**
     * Specifies the created date.
     *
     * @param  createdDate  The created date.
     */
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    /**
     * Returns the ID of the creator.
     *
     * @return  The ID of the creator.
     */
    public BigInteger getCreatedById() {
        return createdById;
    }

    /**
     * Specifies the ID of the creator.
     *
     * @param  createdById  The ID of the creator.
     */
    public void setCreatedById(BigInteger createdById) {
        this.createdById = createdById;
    }

    /**
     * Returns the ID of the last person who modified the entity.
     *
     * @return  The ID of the last person who modified the entity.
     */
    public BigInteger getModifiedById() {
        return modifiedById;
    }

    /**
     * Specifies the ID of the person who most recently modified the entity.
     *
     * @param  modifiedById  The ID of the person who most recently modified the entity.
     */
    public void setModifiedById(BigInteger modifiedById) {
        this.modifiedById = modifiedById;
    }

    /**
     * Returns the modified on date.
     *
     * @return  The modified on date.
     */
    public Date getModifiedDate() {
        return modifiedDate;
    }

    /**
     * Specifies the modified on date.
     *
     * @param  modifiedDate  The modified on date.
     */
    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
	
}
