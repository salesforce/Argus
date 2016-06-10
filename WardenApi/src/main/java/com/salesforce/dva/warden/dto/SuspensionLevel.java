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
