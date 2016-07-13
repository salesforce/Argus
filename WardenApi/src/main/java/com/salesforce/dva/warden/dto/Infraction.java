package com.salesforce.dva.warden.dto;

import java.math.BigInteger;

/**
 * Infraction History Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
@SuppressWarnings( "serial" )
public class Infraction
    extends Entity
{
	private BigInteger policy_id;
    private BigInteger user_id;
    private Long infraction_timestamp;
    private Long expiration_timestamp;

    public BigInteger getPolicyId(  )
    {
        return policy_id;
    }

    public void setPolicyId( BigInteger policy_id )
    {
        this.policy_id = policy_id;
    }

    public BigInteger getUserId(  )
    {
        return user_id;
    }

    public void setUserId( BigInteger user_id )
    {
        this.user_id = user_id;
    }

    public Long getInfractionTimestamp(  )
    {
        return infraction_timestamp;
    }

    public void setInfractionTimestamp( Long infraction_timestamp )
    {
        this.infraction_timestamp = infraction_timestamp;
    }

    public Long getExpirationTimestamp(  )
    {
        return expiration_timestamp;
    }

    public void setExpirationTimestamp( Long expiration_timestamp )
    {
        this.expiration_timestamp = expiration_timestamp;
    }
    
    @Override
   	public int hashCode() {
   		final int prime = 31;
   		int result = 1;
   		result = prime * result + ((expiration_timestamp == null) ? 0 : expiration_timestamp.hashCode());
   		result = prime * result + ((infraction_timestamp == null) ? 0 : infraction_timestamp.hashCode());
   		result = prime * result + ((policy_id == null) ? 0 : policy_id.hashCode());
   		result = prime * result + ((user_id == null) ? 0 : user_id.hashCode());
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
   		Infraction other = (Infraction) obj;
   		if (expiration_timestamp == null) {
   			if (other.expiration_timestamp != null)
   				return false;
   		} else if (!expiration_timestamp.equals(other.expiration_timestamp))
   			return false;
   		if (infraction_timestamp == null) {
   			if (other.infraction_timestamp != null)
   				return false;
   		} else if (!infraction_timestamp.equals(other.infraction_timestamp))
   			return false;
   		if (policy_id == null) {
   			if (other.policy_id != null)
   				return false;
   		} else if (!policy_id.equals(other.policy_id))
   			return false;
   		if (user_id == null) {
   			if (other.user_id != null)
   				return false;
   		} else if (!user_id.equals(other.user_id))
   			return false;
   		return true;
   	}


    @Override
    public Object createExample(  )
    {
        Infraction result = new Infraction(  );

        result.setPolicyId( BigInteger.ONE );
        result.setUserId( BigInteger.ONE );
        result.setInfractionTimestamp( (long) 1 );
        result.setExpirationTimestamp( (long) 10 );

        return null;
    }
}
