
package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

/**
 * Infraction History Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */

@SuppressWarnings("serial")
public class Infraction extends Entity{
	
	private BigInteger policy_id;
	private BigInteger user_id;
	private Long infraction_timestamp;
	private Long expiration_timestamp;
	
	public BigInteger getPolicyId() {
		return policy_id;
	}
	public void setPolicyId(BigInteger policy_id) {
		this.policy_id = policy_id;
	}
	public BigInteger getUserId() {
		return user_id;
	}
	public void setUserId(BigInteger user_id) {
		this.user_id = user_id;
	}
	public Long getInfractionTimestamp() {
		return infraction_timestamp;
	}
	public void setInfractionTimestamp(Long infraction_timestamp) {
		this.infraction_timestamp = infraction_timestamp;
	}
	public Long getExpirationTimestamp() {
		return expiration_timestamp;
	}
	public void setExpirationTimestamp(Long expiration_timestamp) {
		this.expiration_timestamp = expiration_timestamp;
	}
	
	@Override
	public Object createExample() {
		
		Infraction result = new Infraction();
		
		result.setPolicyId(BigInteger.ONE);
		result.setUserId(BigInteger.ONE);
		result.setInfractionTimestamp((long) 1);
		result.setExpirationTimestamp((long) 10);
		
		return null;
	}
	
}