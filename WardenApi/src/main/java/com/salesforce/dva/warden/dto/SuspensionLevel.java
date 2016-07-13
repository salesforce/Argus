package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

@SuppressWarnings( "serial" )
public class SuspensionLevel
    extends Entity
{
    BigInteger policy_id;
    Integer level_number;
    Integer infraction_count;
    BigInteger suspension_time;

    public BigInteger getPolicy_id(  )
    {
        return policy_id;
    }

    public void setPolicyId( BigInteger policy_id )
    {
        this.policy_id = policy_id;
    }

    public Integer getLevelNumber(  )
    {
        return level_number;
    }

    public void setLevelNumber( Integer level_number )
    {
        this.level_number = level_number;
    }

    public Integer getInfractionCount(  )
    {
        return infraction_count;
    }

    public void setInfractionCount( Integer infraction_count )
    {
        this.infraction_count = infraction_count;
    }

    public BigInteger getSuspensionTime(  )
    {
        return suspension_time;
    }

    public void setSuspensionTime( BigInteger suspension_time )
    {
        this.suspension_time = suspension_time;
    }
    

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((infraction_count == null) ? 0 : infraction_count.hashCode());
		result = prime * result + ((level_number == null) ? 0 : level_number.hashCode());
		result = prime * result + ((policy_id == null) ? 0 : policy_id.hashCode());
		result = prime * result + ((suspension_time == null) ? 0 : suspension_time.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SuspensionLevel other = (SuspensionLevel) obj;
		if (infraction_count == null) {
			if (other.infraction_count != null)
				return false;
		} else if (!infraction_count.equals(other.infraction_count))
			return false;
		if (level_number == null) {
			if (other.level_number != null)
				return false;
		} else if (!level_number.equals(other.level_number))
			return false;
		if (policy_id == null) {
			if (other.policy_id != null)
				return false;
		} else if (!policy_id.equals(other.policy_id))
			return false;
		if (suspension_time == null) {
			if (other.suspension_time != null)
				return false;
		} else if (!suspension_time.equals(other.suspension_time))
			return false;
		return true;
	}

	@Override
    public Object createExample(  )
    {
        SuspensionLevel result = new SuspensionLevel(  );

        result.setPolicyId( BigInteger.ONE );
        result.setLevelNumber( 1 );
        result.setInfractionCount( 4 );
        result.setSuspensionTime( BigInteger.TEN );

        return null;
    }
}
