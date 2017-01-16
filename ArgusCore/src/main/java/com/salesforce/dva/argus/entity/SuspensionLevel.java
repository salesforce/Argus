package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

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
import javax.persistence.UniqueConstraint;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.salesforce.dva.argus.util.WaaSObjectConverter;

/**
 * The entity encapsulates information about the suspension levels for a given policy.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>POLICY_ID</li>
 *   <li>LEVEL_NUMBER</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>ID</li>
 *   <li>POLICY_ID</li>
 *   <li>LEVEL_NUMBER</li>
 *   <li>INFRACTION_COUNT</li>
 *   <li>SUSPENSION_TIME</li>
 * </ul>
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "SUSPENSION_LEVEL", uniqueConstraints = @UniqueConstraint(columnNames = { "policy_id", "level_number" }))
@NamedQueries(
    {
        @NamedQuery(
                name = "SuspensionLevel.findByPolicyAndLevel", 
                query = "SELECT r FROM SuspensionLevel r WHERE r.policy = :policy and r.id = :id"
        ),
        @NamedQuery(
                name = "SuspensionLevel.findByPolicy", 
                query = "SELECT r FROM SuspensionLevel r WHERE r.policy = :policy"
        )
    }
)
public class SuspensionLevel extends JPAEntity {
	//~ Instance fields ******************************************************************************************************************************

    @ManyToOne(optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;    
    
    @Basic(optional = false)
    @Column(name = "level_number", nullable = false)
    private int levelNumber;
    
    @Basic(optional = false)
    @Column(name = "infraction_count", nullable = false)
    private int infractionCount;
    
    @Column(name = "suspension_time", nullable = false)
    @Basic(optional = false)
    private long suspensionTime;   
    
    
    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new SubsystemSuspensionLevels object.
     *
     * @param  creator         The creator of this suspension level.
     * @param  policyId        The policy for which to set suspension levels. Cannot be null.
     * @param  levelNumber     The level number for this suspension level. Cannot be null.
     * @param  infractionCount The infraction count for this suspension level. Cannot be null.
     * @param  suspensionTime  The suspension time for this suspension level. Cannot be null.
     */
    public SuspensionLevel(PrincipalUser creator, Policy policy, int levelNumber, int infractionCount, Long suspensionTime) {
    	super(creator);
    	setPolicy(policy);
        setLevelNumber(levelNumber);
        setInfractionCount(infractionCount);
        setSuspensionTime(suspensionTime);
    }
    /**
     * Creates a new SubsystemSuspensionLevel object.     
     */
    protected SuspensionLevel(){
    	super(null);
    }
  //~ Methods **************************************************************************************************************************************
    /**
     * Finds an suspension given its policy and level number.
     *
     * @param   em        			The entity manager to use. Cannot be null.
     * @param   policy  			The policy associated with this suspension level. Cannot be null.
     * @param   levelNumber			The level number associated with this suspension level. Cannot be null.
     *
     * @return  The corresponding suspension or null if no suspension level having the specified policy and level number exist.
     */
    public static SuspensionLevel findByPolicyAndLevel(EntityManager em, Policy policy, BigInteger id) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(policy != null , "Policy cannot be null");
        requireArgument(id.signum() == 1, "Level must be greater than zero.");

        TypedQuery<SuspensionLevel> query = em.createNamedQuery("SuspensionLevel.findByPolicyAndLevel", SuspensionLevel.class);

        
        try {
            query.setParameter("policy", policy);
            query.setParameter("id", id);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }
    
    
    /**
     * Finds suspensions given its policy.
     *
     * @param   em        			The entity manager to use. Cannot be null.
     * @param   policy  			The policy associated with this suspension level. Cannot be null.
     *
     * @return  The corresponding suspensions or null if no suspension level having the specified policy exist.
     */
    public static List<SuspensionLevel> findByPolicy(EntityManager em, Policy policy) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(policy != null , "Policy cannot be null");

        TypedQuery<SuspensionLevel> query = em.createNamedQuery("SuspensionLevel.findByPolicy", SuspensionLevel.class);

        
        try {
            query.setParameter("policy", policy);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getResultList();
        } catch (NoResultException ex) {
            return null;
        }
    }
    
   
    public Policy getPolicy() {
		return policy;
	}
    
	public void setPolicy(Policy policy) {
		this.policy = policy;
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
    public int hashCode() {
        int hash = 7;

        hash = 97 * hash + Objects.hashCode(this.policy);
        hash = 97 * hash + Objects.hashCode(this.levelNumber);
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

        final SuspensionLevel other = (SuspensionLevel) obj;

        if (!Objects.equals(this.policy, other.policy)) {
            return false;
        }
        if (!Objects.equals(this.levelNumber, other.levelNumber)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
    	Object[] params = { getPolicy(), getLevelNumber(), getInfractionCount(), getSuspensionTime()};
    	String format = "SuspensionLevel{ policy = {0}, levelNumber = {1,number,#}, infractionCount = {2,number,#}, suspensionTime = {3,number,#}";
    	
        return MessageFormat.format(format, params);
    }
}