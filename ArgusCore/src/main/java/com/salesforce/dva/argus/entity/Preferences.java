package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.Serializable;
import java.math.BigInteger;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;

import com.salesforce.dva.argus.system.SystemAssert;

/**
 * This encapsulates the preferences for a given <user, entity> combination. If the entity is null, then the preferences
 * are associated to a given user. 
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>USER</li>
 *   <li>ENTITY</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>USER</li>
 * </ul>
 *
 * @author	Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "PREFERENCES", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "entity_id" }))
@NamedQueries(
	    {
	        @NamedQuery(name = "Preferences.getPreferencesByUserAndEntity", query = "SELECT p FROM Preferences p WHERE p.user.id = :userId AND p.entity.id = :entityId"),
	        @NamedQuery(name = "Preferences.getPreferencesForUser", query = "SELECT p FROM Preferences p WHERE p.user.id = :userId")
	    }
	)
public class Preferences extends JPAEntity implements Serializable, Identifiable {
	
	//~ Instance fields ******************************************************************************************************************************
	
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "user_id", nullable = false)
	private PrincipalUser user;
	
	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "entity_id", nullable = true)
	private JPAEntity entity;
	
	@Lob
	private String preferences;
	
	//~ Constructors *********************************************************************************************************************************
	
	protected Preferences() {}
	
	public Preferences(PrincipalUser creator, PrincipalUser user, JPAEntity entity, String preferences) {
		super(creator);
		setUser(user);
		setEntity(entity);
		setPreferences(preferences);
	}
	
	//~ Static Methods **************************************************************************************************************************************
	
	public static Preferences getPreferencesByUserAndEntity(EntityManager em, BigInteger userId, BigInteger entityId) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		TypedQuery<Preferences> query = em.createNamedQuery("Preferences.getPreferencesByUserAndEntity", Preferences.class);
		
		try {
            query.setParameter("userId", userId);
            query.setParameter("entityId", entityId);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
	}
	
	public static Preferences getPreferencesForUser(EntityManager em, BigInteger userId) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		TypedQuery<Preferences> query = em.createNamedQuery("Preferences.getPreferencesForUser", Preferences.class);
		
		try {
            query.setParameter("userId", userId);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
	}

	//~ Methods **************************************************************************************************************************************
	
	@Override
	public BigInteger getId() {
		return id;
	}

	public PrincipalUser getUser() {
		return user;
	}

	public void setUser(PrincipalUser user) {
		SystemAssert.requireArgument(user != null, "User entity associated with a preferences object cannot be null.");
		
		this.user = user;
	}

	public JPAEntity getEntity() {
		return entity;
	}

	public void setEntity(JPAEntity entity) {
		this.entity = entity;
	}

	public String getPreferences() {
		return preferences;
	}

	public void setPreferences(String preferences) {
		this.preferences = preferences;
	}
	
}
