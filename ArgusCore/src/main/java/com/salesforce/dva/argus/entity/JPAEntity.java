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

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.OneToMany;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.TableGenerator;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;

import org.eclipse.persistence.annotations.Index;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Base entity class for JPA components. All subclasses must implement <tt>hashCode</tt> and <tt>equals</tt> such that they use the fields that
 * determine uniqueness for the subclass implementation.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>ID - If null will be generated starting at 100001.</li>
 * </ul>
 *
 * <p>Fields that are immutable are:</p>
 *
 * <ul>
 *   <li>CREATED_BY</li>
 *   <li>CREATED_DATE</li>
 *   <li></li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>CREATED_BY</li>
 *   <li>CREATED_DATE</li>
 *   <li></li>
 *   <li>MODIFIED_BY</li>
 *   <li>MODIFIED_DATE</li>
 *   <li></li>
 * </ul>
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@DiscriminatorColumn(name = "DTYPE", discriminatorType = DiscriminatorType.STRING)
@Entity
@Index(name="jpaEntity_modifiedDate_idx", columnNames={"modifiedDate"}, unique = false) 
@Inheritance(strategy = InheritanceType.JOINED)
@NamedQueries(
    {
        @NamedQuery(name = "JPAEntity.findByPrimaryKey", query = "SELECT e FROM JPAEntity e WHERE e.id = :id AND e.deleted = :deleted"),
        @NamedQuery(name = "JPAEntity.findByPrimaryKeys", query = "SELECT e FROM JPAEntity e WHERE e.id IN :ids AND e.deleted = :deleted"),
        @NamedQuery(
            name = "JPAEntity.findByDeleteMarker", query = "SELECT e FROM JPAEntity e WHERE e.deleted = :deleted"
        )
    }
)
public abstract class JPAEntity implements Serializable, Identifiable {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    @GeneratedValue(generator = "IDGEN")
    @Id
    @TableGenerator(name = "IDGEN", initialValue = 100001)
    @Metadata
    protected BigInteger id;
    
    @JoinColumn(updatable = false)
    @ManyToOne
    @Metadata
    protected PrincipalUser createdBy;
    
    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Metadata
    protected Date createdDate;
    
    @JoinColumn(updatable = true)
    @ManyToOne
    @Metadata
    protected PrincipalUser modifiedBy;
    
    @Basic(optional = false)
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    @Metadata
    protected Date modifiedDate;
    
    protected boolean deleted = false;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new Component object. */
    protected JPAEntity() {
        this(null);
    }

    /**
     * Creates a new Component object.
     *
     * @param  creator  The user creating the object.
     */
    protected JPAEntity(PrincipalUser creator) {
        this.id = null;
        this.createdBy = creator;
        this.modifiedBy = creator;
        this.createdDate = null;
        this.modifiedDate = null;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Finds a JPA entity by its primary key.
     *
     * @param   <E>   The JPA entity type.
     * @param   em    The entity manager to use.  Cannot be null.
     * @param   id    The ID of the entity to find.  Must be a positive, non-zero integer.
     * @param   type  The runtime type to cast the result value to.
     *
     * @return  The corresponding entity or null if no entity exists.
     */
    public static <E extends Identifiable> E findByPrimaryKey(EntityManager em, BigInteger id, Class<E> type) {
        requireArgument(em != null, "The entity manager cannot be null.");
        requireArgument(id != null && id.compareTo(ZERO) > 0, "ID cannot be null and must be positive and non-zero");
        requireArgument(type != null, "The entity type cannot be null.");

        TypedQuery<E> query = em.createNamedQuery("JPAEntity.findByPrimaryKey", type);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("id", id);
            query.setParameter("deleted", false);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }
    
    /**
     * Finds JPA entities by their primary keys.
     *
     * @param   <E>   The JPA entity type.
     * @param   em    The entity manager to use.  Cannot be null.
     * @param   ids    The list of IDs of the entities to find.  Must be a non-null non-empty list.
     * @param   type  The runtime type to cast the result value to.
     *
     * @return  The corresponding entity or null if no entity exists.
     */
    public static <E extends Identifiable> List<E> findByPrimaryKeys(EntityManager em, List<BigInteger> ids, Class<E> type) {
        requireArgument(em != null, "The entity manager cannot be null.");
        requireArgument(ids != null && !ids.isEmpty(), "IDs cannot be null or empty.");
        requireArgument(type != null, "The entity type cannot be null.");

        TypedQuery<E> query = em.createNamedQuery("JPAEntity.findByPrimaryKeys", type);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("ids", ids);
            query.setParameter("deleted", false);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Finds all entities that have been marked for deletion.
     *
     * @param   <E>    The JPA entity type.
     * @param   em     The entity manager to use.  Cannot be null.
     * @param   type   The runtime type to cast the result value to.
     * @param   limit  The number of entities to find. If -1, finds all such entities.
     *
     * @return  The list of matching entities. Will never be null, but may be empty.
     */
    public static <E extends Identifiable> List<E> findEntitiesMarkedForDeletion(EntityManager em, Class<E> type, final int limit) {
        requireArgument(em != null, "Entity Manager cannot be null");
        requireArgument(limit == -1 || limit > 0, "Limit if not -1, must be greater than 0.");

        TypedQuery<E> query = em.createNamedQuery("JPAEntity.findByDeleteMarker", type);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("deleted", true);
            if(limit > 0) {
            	query.setMaxResults(limit);
            }
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the user that created the entity.
     *
     * @return  The user that created the entity.
     */
    public PrincipalUser getCreatedBy() {
        return createdBy;
    }

    /**
     * Returns the date the entity was created.
     *
     * @return  The date the entity was created.
     */
    public Date getCreatedDate() {
        return (createdDate == null) ? null : Date.class.cast(createdDate.clone());
    }

    /**
     * Returns the primary key ID of the entity.
     *
     * @return  The primary key ID.
     */
    @Override
    public BigInteger getId() {
        return id;
    }

    /**
     * Returns the user that last modified the entity.
     *
     * @return  The user that last modified the entity.
     */
    public PrincipalUser getModifiedBy() {
        return modifiedBy;
    }

    /**
     * Returns the date the entity was last modified.
     *
     * @return  The date the entity was last modified.
     */
    public Date getModifiedDate() {
        return (modifiedDate == null) ? null : Date.class.cast(modifiedDate.clone());
    }
    
    /**
     * Sets the date the entity was last modified.
     *
     * @param  The date the entity was last modified.
     */
    public void setModifiedDate(Date modifiedDate) {
        if(modifiedDate!=null) {
        	    this.modifiedDate = modifiedDate;
        }
    }

    /**
     * Updates the user that created the entity.
     *
     * @param  createdBy  The user that created the object.
     */
    public void setCreatedBy(PrincipalUser createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * Updates the user that last modified the entity.
     *
     * @param  modifiedBy  Updates the user that last modified the entity.
     */
    public void setModifiedBy(PrincipalUser modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    /**
     * Indicates if an entity is marked for deletion.
     *
     * @return  True if marked for deletion.
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * Specifies whether an entity should be marked for deletion.
     *
     * @param  deleted  True if an entity should be marked for deletion.
     */
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    /** Automatically updates the created and modified dates for the entity. */
    @PrePersist
    @PreUpdate
    protected void preUpdate() {
        this.modifiedDate = new Date();
        if (this.createdDate == null) {
            this.createdDate = this.modifiedDate;
        }
    }

    @Override
    public int hashCode() {
        if (id == null) {
            return super.hashCode();
        } else {
            int hash = 7;

            hash = 83 * hash + id.hashCode();
            return hash;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (id == null) {
            return super.equals(obj);
        } else {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            final JPAEntity other = JPAEntity.class.cast(obj);

            return (id.equals(other.getId()));
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
