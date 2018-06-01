/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.persistence.*;

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
 * @author	Chandravyas Annakula (cannakula@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "PREFERENCES", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "entity_id" }))
@NamedQueries(
	    {
	        @NamedQuery(name = "Preferences.getPreferencesByUserAndEntity", query = "SELECT p FROM Preferences p WHERE p.user.id = :userId AND p.entity.id = :entityId"),
			@NamedQuery(name = "Preferences.getPreferencesByEntity", query = "SELECT p FROM Preferences p WHERE p.entity.id = :entityId")
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

	@ElementCollection(fetch = FetchType.LAZY)
	@MapKeyColumn(name="name")
	@Column(name="value")
	@CollectionTable(name="preferences_keyvaluepairs", joinColumns=@JoinColumn(name="preferences_id"))
	Map<String, String> preferences = new HashMap<>(); // maps from attribute name to value


	//~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Preferences object.
     */
	protected Preferences() {
	    super(null);
    }

    /**
     * Creates a new Preferences object.
     *
     * @param creator       The creator of this Preferences object
     * @param user          The owner of this Preferences. This need not be the same as the creator. Cannot be null.
     * @param entity        The entity for which the preferences is created
     * @param preferences   Preferences object
     */
	public Preferences(PrincipalUser creator, PrincipalUser user, JPAEntity entity, Map<String, String> preferences) {
		super(creator);
		setUser(user);
		setEntity(entity);
		setPreferences(preferences);
	}
	
	//~ Static Methods **************************************************************************************************************************************

    /**
     *
     * @param em        The entity manager to use.
     * @param userId    The userId of preferences to filter on
     * @param entityId  The entityId of the chart or the dashboard
     * @return          Returns the preferences object of the entityId created by userId
     */
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

    /**
     *
     * @param em        The entity manager to use.
     * @param entityId  The id of the chart or the dashboard
     * @return          Returns the preferences object of the entityId
     */
	public static Preferences getPreferencesByEntity(EntityManager em,BigInteger entityId) {
		requireArgument(em != null, "Entity manager can not be null.");

		TypedQuery<Preferences> query = em.createNamedQuery("Preferences.getPreferencesByEntity", Preferences.class);

		try {
			query.setParameter("entityId", entityId);
			return query.getSingleResult();
		} catch (NoResultException ex) {
			return null;
		}
	}


	//~ Methods **************************************************************************************************************************************

    /**
     * The Preferences Id
     *
     * @return Returns the Preferences Id
     */
	@Override
	public BigInteger getId() {
		return id;
	}

    /**
     * The PrincipalUser that is the owner of this Preferences.
     *
     * @return The PrincipalUser that is the owner of this Preferences.
     */
	public PrincipalUser getUser() {
		return user;
	}

    /**
     * Sets the PrincipalUser that is the owner of this Preferences.
     *
     * @param user The PrincipalUser that is the owner of this Preferences.
     */
	public void setUser(PrincipalUser user) {
		SystemAssert.requireArgument(user != null, "User entity associated with a preferences object cannot be null.");
		
		this.user = user;
	}

    /**
     * The entity to which these preferences belong
     *
     * @return  The entity to which these preferences belong
     */
	public JPAEntity getEntity() {
		return entity;
	}

    /**
     * Sets the entity to which these preferences belong to
     *
     * @param entity   The entity to which these preferences belong
     */
	public void setEntity(JPAEntity entity) {
		this.entity = entity;
	}

    /**
     * The Preferences of this user and entity
     *
     * @return  The Preferences of this user and entity
     */
	public Map<String, String> getPreferences() {
		return preferences;
	}

    /**
     * Sets the Preferences of this user and entity
     *
     * @param preferences The Preferences of this user and entity
     */
	public void setPreferences(Map<String, String> preferences) {
		this.preferences = preferences;
	}


    @Override
    public int hashCode() {
        int hash = 3;

        hash = 53 * hash + Objects.hashCode(this.user);
        hash = 53 * hash + Objects.hashCode(this.entity);
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

        final Preferences other = (Preferences) obj;

        if (!Objects.equals(this.user, other.user)) {
            return false;
        }
        if (!Objects.equals(this.entity, other.entity)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Preferences{" + "user=" + user + ", entity=" + entity + ", preferences="+ preferences + '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */