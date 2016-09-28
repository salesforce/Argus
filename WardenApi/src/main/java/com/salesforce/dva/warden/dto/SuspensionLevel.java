
package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

@SuppressWarnings("serial")
public class SuspensionLevel extends Entity{

	BigInteger policyId;
	int levelNumber;
	int infractionCount;
	long suspensionTime;
	
	public BigInteger getPolicyId() {
		return policyId;
	}



	public void setPolicyId(BigInteger policyId) {
		this.policyId = policyId;
	}



	public int getLevelNumber() {
		return levelNumber;
	}



	public void setLevelNumber(int levelNumber) {
		this.levelNumber = levelNumber;
	}



	public int getInfractionCount() {
		return infractionCount;
	}



	public void setInfractionCount(int infractionCount) {
		this.infractionCount = infractionCount;
	}



	public long getSuspensionTime() {
		return suspensionTime;
	}



	public void setSuspensionTime(long suspensionTime) {
		this.suspensionTime = suspensionTime;
	}



	@Override
	public Object createExample() {

		SuspensionLevel result = new SuspensionLevel();
		
		result.setPolicyId(BigInteger.ONE);
		result.setLevelNumber(1);
		result.setInfractionCount(4);
		result.setSuspensionTime(10L);
		
		return null;
	}

}