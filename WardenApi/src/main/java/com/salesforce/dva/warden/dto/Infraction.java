
package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

/**
 * Infraction History Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */

@SuppressWarnings("serial")
public class Infraction extends Entity{
	
	private BigInteger policyId;
	private BigInteger userId;
	private Long infractionTimestamp;
	private Long expirationTimestamp =0L;
	
	public BigInteger getPolicyId() {
		return policyId;
	}
	public void setPolicyId(BigInteger policyId) {
		this.policyId = policyId;
	}
	public BigInteger getUserId() {
		return userId;
	}
	public void setUserId(BigInteger userId) {
		this.userId = userId;
	}
	public Long getInfractionTimestamp() {
		return infractionTimestamp;
	}
	public void setInfractionTimestamp(Long infractionTimestamp) {
		this.infractionTimestamp = infractionTimestamp;
	}
	public Long getExpirationTimestamp() {
		return expirationTimestamp;
	}
	public void setExpirationTimestamp(Long expirationTimestamp) {
		this.expirationTimestamp = expirationTimestamp;
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