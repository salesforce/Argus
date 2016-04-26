package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

@SuppressWarnings("serial")
public class SuspensionLevel extends com.salesforce.dva.warden.dto.Entity{

	BigInteger policy_id;
	int level_number;
	int infraction_count;
	BigInteger suspension_time;
	
	public BigInteger getPolicy_id() {
		return policy_id;
	}



	public void setPolicy_id(BigInteger policy_id) {
		this.policy_id = policy_id;
	}



	public int getLevel_number() {
		return level_number;
	}



	public void setLevel_number(int level_number) {
		this.level_number = level_number;
	}



	public int getInfraction_count() {
		return infraction_count;
	}



	public void setInfraction_count(int infraction_count) {
		this.infraction_count = infraction_count;
	}



	public BigInteger getSuspension_time() {
		return suspension_time;
	}



	public void setSuspension_time(BigInteger suspension_time) {
		this.suspension_time = suspension_time;
	}



	@Override
	public Object createExample() {

		SuspensionLevel result = new SuspensionLevel();
		
		result.setPolicy_id(BigInteger.ONE);
		result.setLevel_number(1);
		result.setInfraction_count(4);
		result.setSuspension_time(BigInteger.TEN);
		
		return null;
	}

}
