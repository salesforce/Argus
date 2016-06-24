package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.salesforce.dva.argus.util.WaaSObjectConverter;

/**
 * The entity encapsulates information about the infraction. There are no
 * uniqueness constraints in this entity.
 *
 * <p>
 * Fields that cannot be null are:
 * </p>
 *
 * <ul>
 * <li>POLICY_ID</li>
 * <li>USER_ID</li>
 * <li>INFRACTION_TIMESTAMP</li>
 * <li>EXPIRATION_TIMESTAMP</li>
 * </ul>
 *
 * @author Ruofan Zhang (rzhang@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "INFRACTION")
@NamedQueries({
		@NamedQuery(
				name = "Infraction.findByPolicyAndUserName", 
				query = "SELECT r FROM Infraction r, PrincipalUser u WHERE r.policy = :policy AND r.user = u AND u.userName = :userName"),
		@NamedQuery(
                name = "Infraction.findByPolicyAndInfraction", 
                query = "SELECT r FROM Infraction r WHERE r.policy = :policy and r.id = :id"
        ),
        @NamedQuery(
                name = "Infraction.findByPolicy", 
                query = "SELECT r FROM Infraction r WHERE r.policy = :policy"
        ),
        @NamedQuery(
                name = "Infraction.findByUser", 
                query = "SELECT r FROM Infraction r WHERE r.user = :user"
        )
		})

public class Infraction extends JPAEntity {
	@ManyToOne(optional = false)
	@JoinColumn(name = "policy_id", nullable = false)
	private Policy policy;

	@ManyToOne(optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private PrincipalUser user;

	@Basic(optional = false)
	@Column(name = "infraction_timestamp", nullable = false)
	private Long infractionTimestamp;

	@Basic(optional = false)
	@Column(name = "expiration_timestamp", nullable = false)
	private Long expirationTimestamp = 0L;

	// ~ Constructors
	// *********************************************************************************************************************************

	/**
	 * Creates a new Infraction object.
	 *
	 * @param creator
	 *            The creator of this infraction. Cannot be null.
	 * @param policy
	 *            The policy associated with this infraction. Cannot be null.
	 * @param user
	 *            The user name associated with this infraction. Cannot be null.
	 * @param infractionTimestamp
	 *            The infraction timestamp of this infraction. Cannot be null.
	 * @param expirationTimestamp
	 *            The expiration timestamp of this infraction. Cannot be null.
	 */
	public Infraction(PrincipalUser creator, Policy policy, PrincipalUser user, long infractionTimestamp,long expirationTimestamp) {
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

	// ~ Methods
	// **************************************************************************************************************************************

	/**
	 * Finds the number of user infractions for a policy-user combination that
	 * have occurred since the given start time and end time.
	 *
	 * @param em
	 *            The entity manager to use. Cannot be null.
	 * @param userName
	 *            The user name. Cannot be null.
	 * @param policy
	 *            The policy. Cannot be null.
	 * @param startTime
	 *            The start time threshold.
	 *
	 * @return The number of infractions.
	 */
	public static int findInfractionCount(EntityManager em, Policy policy, String userName, long startTime,long endTime) {

		List<Infraction> records = findByPolicyAndUserName(em, policy, userName);

		int count = 0;

		for (Infraction record : records) {
			Long timestamp = record.getInfractionTimestamp();

			if (timestamp > startTime && timestamp < endTime) {
				count++;
			}

		}
		return count;
	}

	/**
     * Finds infractions given its policy.
     *
     * @param   em        			The entity manager to use. Cannot be null.
     * @param   policy  			The policy associated with this infractions. Cannot be null.
     *
     * @return  The corresponding infractions or null if no infractions having the specified policy exist.
     */
    public static List<Infraction> findByPolicy(EntityManager em, Policy policy) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(policy != null , "Policy cannot be null");

        TypedQuery<Infraction> query = em.createNamedQuery("Infraction.findByPolicy", Infraction.class);

        
        try {
            query.setParameter("policy", policy);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getResultList();
        } catch (NoResultException ex) {
            return null;
        }
    }
    /**
     * Finds infractions given its user.
     *
     * @param   em        			The entity manager to use. Cannot be null.
     * @param   user	  			The principal users associated with this infractions. Cannot be null.
     *
     * @return  The corresponding infractions or null if no infractions having the specified policy exist.
     */
    public static List<Infraction> findByUser(EntityManager em, PrincipalUser user) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(user != null , "User cannot be null");

        TypedQuery<Infraction> query = em.createNamedQuery("Infraction.findByUser", Infraction.class);

        
        try {
            query.setParameter("user", user);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getResultList();
        } catch (NoResultException ex) {
            return null;
        }
    }
    /**
     * Finds an infraction given its policy and infraction id.
     *
     * @param   em        			The entity manager to use. Cannot be null.
     * @param   policy  			The policy associated with this suspension level. Cannot be null.
     * @param   infractionId					The infraction id. Cannot be null.
     *
     * @return  The corresponding infraction or null if no infraction having the specified policy and infraction id exist.
     */
    public static Infraction findByPolicyAndInfraction(EntityManager em, Policy policy, BigInteger infractionId) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(policy != null , "Policy cannot be null");
        requireArgument(infractionId.signum() == 1, "Infraction id must be greater than zero.");

        TypedQuery<Infraction> query = em.createNamedQuery("Infraction.findByPolicyAndInfraction", Infraction.class);

        
        try {
            query.setParameter("policy", policy);
            query.setParameter("id", infractionId);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }
	/**
	 * Find the infractions for a given user-policy combination.
	 *
	 * @param em
	 *            The EntityManager to use.
	 * @param userName
	 *            The userName for which to retrieve record.
	 * @param policy
	 *            The policy for which to retrieve record.
	 *
	 * @return The infractions for the given user-policy combination. Null if no
	 *         such record exists.
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
	 * @return True if a suspension is active.
	 */
	public boolean isSuspended() {
		return (System.currentTimeMillis() < getExpirationTimestamp()) || isSuspendedIndefinitely();
	}

	/**
	 * Indicates if the user is suspended indefinitely.
	 *
	 * @return True if the user is suspended indefinitely.
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

	public void setInfractionTimestamp(Long infractionTimestamp) {
		this.infractionTimestamp = infractionTimestamp;
	}

	public Long getExpirationTimestamp() {
		return expirationTimestamp;
	}

	public void setExpirationTimestamp(Long expirationTimestamp) {
		this.expirationTimestamp = expirationTimestamp;
	}

	public static List<com.salesforce.dva.warden.dto.Infraction> transformToDto(List<Infraction> infractions) {
		if (infractions == null) {
			throw new WebApplicationException("Null entity object cannot be converted to Dto object.",
					Status.INTERNAL_SERVER_ERROR);
		}

		return infractions.stream().map(Infraction::transformToDto).collect(Collectors.toList());
	}

	public static com.salesforce.dva.warden.dto.Infraction transformToDto(Infraction infraction) {
		if (infraction == null) {
			throw new WebApplicationException("Null entity object cannot be converted to Dto object.",
					Status.INTERNAL_SERVER_ERROR);
		}

		com.salesforce.dva.warden.dto.Infraction result = WaaSObjectConverter.createDtoObject(com.salesforce.dva.warden.dto.Infraction.class, infraction);
		result.setPolicyId(infraction.getId());
		result.setUserId(infraction.getUser().getId());
		result.setInfractionTimestamp(infraction.getInfractionTimestamp());
		result.setExpirationTimestamp(infraction.getExpirationTimestamp());
		
		return result;
	}
	
	@Override
    public int hashCode() {
        int hash = 5;

        hash = 29 * hash + Objects.hashCode(this.id);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final Notification other = (Notification) obj;

        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
    	Object[] params = { getPolicy(), getUser(), getInfractionTimestamp(), getExpirationTimestamp()};
    	String format = "Infraction{ policy = {0}, user = {1}, infractionTimestamp = {2,number,#}, expirationTimestamp = {3,number,#}";
    	
        return MessageFormat.format(format, params);
       
    }
}