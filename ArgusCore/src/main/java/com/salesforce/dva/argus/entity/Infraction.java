package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.util.List;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.TypedQuery;

/**
 * The entity encapsulates information about the infraction.
 * There are no uniqueness constraints in this entity.
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>POLICY_ID</li>
 *   <li>USER_ID</li>
 *   <li>INFRACTION_TIMESTAMP</li>
 *   <li>EXPIRATION_TIMESTAMP</li>
 * </ul>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "INFRACTION")
@NamedQueries(
	    {
	        @NamedQuery(
	            name = "Infraction.findByPolicyAndUserName", 
	            query = "SELECT r FROM Infraction r, PrincipalUser u WHERE r.policy = :policy AND r.user = u AND u.userName = :userName"
	        )   
	    }
	)
 
public class Infraction extends JPAEntity {
	 	@ManyToOne(optional = false)
	    @JoinColumn(name = "policy_id", nullable = false)
	    private Policy policy;    
	 	
	 	@ManyToOne(optional = false)
	    @JoinColumn(name = "user_id", nullable = false)
	    private PrincipalUser user;	 	
	 	
	 	@Basic(optional = false)
		@Column(name = "infraction_timestamp", nullable=false)
	    private Long infractionTimestamp;

	 	@Basic(optional = false)
		@Column(name = "expiration_timestamp", nullable=false)
	 	private Long expirationTimestamp = 0L;

	 	//~ Constructors *********************************************************************************************************************************
		
		 /**
	     * Creates a new Infraction object.
	     *
	     * @param  creator      		The creator of this infraction. Cannot be null.
	     * @param  policy      			The policy associated with this infraction. Cannot be null.
	     * @param  user     			The user name associated with this infraction. Cannot be null.
	     * @param  infractionTimestamp 	The infraction timestamp of this infraction. Cannot be null.
	     * @param  expirationTimestamp 	The expiration timestamp of this infraction. Cannot be null.	    
	     */
	    public Infraction(PrincipalUser creator, Policy policy, PrincipalUser user, long infractionTimestamp , long expirationTimestamp) {
			super(creator);
			setPolicy(policy);
			setUser(user);
			setInfractionTimestamp(infractionTimestamp);
			setExpirationTimestamp(expirationTimestamp);
		}
	    
	    /** Creates a new Infraction object. */
	    protected Infraction() {
	        super(null);
	    }
	 	
	  //~ Methods **************************************************************************************************************************************

	    /**
	     * Finds the number of user infractions for a policy-user combination that have occurred since the given start time and end time.
	     *
	     * @param   em         The entity manager to use. Cannot be null.
	     * @param   userName   The user name. Cannot be null.
	     * @param   policy     The policy. Cannot be null.
	     * @param   startTime  The start time threshold.
	     *
	     * @return  The number of infractions.
	     */
	    public static int findInfractionCount(EntityManager em, Policy policy, String userName, long startTime, long endTime) {    

	        List<Infraction> records = findByPolicyAndUserName(em, policy, userName);	        

	        int count = 0;

	        for ( Infraction record : records) {
	            Long timestamp = record.getInfractionTimestamp();
	           
	                if (timestamp > startTime && timestamp < endTime) {
	                    count++;
	                }
	            
	        }
	        return count;
	    }
	    
	    /**
	     * Find the infractions for a given user-policy combination.
	     *
	     * @param   em         The EntityManager to use.
	     * @param   userName   The userName for which to retrieve record.
	     * @param   policy   The policy for which to retrieve record.
	     *
	     * @return  The infractions for the given user-policy combination. Null if no such record exists.
	     */
	    public static List<Infraction> findByPolicyAndUserName(EntityManager em, Policy policy, String userName) {
	        TypedQuery<Infraction> query = em.createNamedQuery("Infraction.findByPolicyAndUserName", Infraction.class);

	        try {	        	 
	            query.setParameter("userName", userName);
	            query.setParameter("policy", policy);
	            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
	            return query.getResultList();
	        } catch (NoResultException ex) {
	            return null;
	        }
	    }

	    /**
	     * Indicates whether a suspension is active.
	     *
	     * @return  True if a suspension is active.
	     */
	    public boolean isSuspended() {
	        return (System.currentTimeMillis() < getExpirationTimestamp()) || isSuspendedIndefinitely();
	    }
	    /**
	     * Indicates if the user is suspended indefinitely.
	     *
	     * @return  True if the user is suspended indefinitely.
	     */
	    public boolean isSuspendedIndefinitely() {
	        return expirationTimestamp == -1L;
	    }
	    
	    
	  
		public Policy getPolicy() {
			return policy;
		}

		public void setPolicy(Policy policy) {
			this.policy = policy;
		}
		
		

		public PrincipalUser getUser() {
			return user;
		}

		public void setUser(PrincipalUser user) {
			this.user = user;
		}

		public Long getInfractionTimestamp() {
			return infractionTimestamp;
		}


		public void setInfractionTimestamp(Long infractionTimestamp){
			this.infractionTimestamp = infractionTimestamp;
		}
		public Long getExpirationTimestamp() {
			return expirationTimestamp;
		}

		public void setExpirationTimestamp(Long expirationTimestamp) {
			this.expirationTimestamp = expirationTimestamp;
		}	 	
}