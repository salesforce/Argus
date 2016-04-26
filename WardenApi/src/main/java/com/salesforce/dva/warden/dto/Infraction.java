package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

/**
 * Infraction History Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */

@SuppressWarnings("serial")
public class Infraction extends com.salesforce.dva.warden.dto.Entity{
	
	BigInteger policy_id;
	int user_id;
	BigInteger infraction_timestamp;
	BigInteger expiration_timestamp;
	
	public BigInteger getPolicy_id() {
		return policy_id;
	}
	public void setPolicy_id(BigInteger policy_id) {
		this.policy_id = policy_id;
	}
	public int getUser_id() {
		return user_id;
	}
	public void setUser_id(int user_id) {
		this.user_id = user_id;
	}
	public BigInteger getInfraction_timestamp() {
		return infraction_timestamp;
	}
	public void setInfraction_timestamp(BigInteger infraction_timestamp) {
		this.infraction_timestamp = infraction_timestamp;
	}
	public BigInteger getExpiration_timestamp() {
		return expiration_timestamp;
	}
	public void setExpiration_timestamp(BigInteger expiration_timestamp) {
		this.expiration_timestamp = expiration_timestamp;
	}
	
	@Override
	public Object createExample() {
		
		Infraction result = new Infraction();
		
		result.setPolicy_id(BigInteger.ONE);
		result.setUser_id(1);
		result.setInfraction_timestamp(BigInteger.ONE);
		result.setExpiration_timestamp(BigInteger.TEN);
		
		return null;
	}
	
}
