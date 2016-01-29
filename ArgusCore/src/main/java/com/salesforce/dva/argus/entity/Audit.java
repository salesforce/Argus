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
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static org.joda.time.DateTimeConstants.MILLIS_PER_WEEK;

/**
 * It encapsulates the audit information.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@NamedQueries(
    {
        @NamedQuery(name = "Audit.findByJPAEntity", query = "SELECT a FROM Audit a WHERE a.entity = :jpaEntity order by a.createdDate DESC"),
        @NamedQuery(
            name = "Audit.findByHostName", query = "SELECT a FROM Audit a WHERE a.hostName = :hostName order by a.createdDate DESC"
        ), @NamedQuery(name = "Audit.findAll", query = "SELECT a FROM Audit a order by a.createdDate DESC"),
        @NamedQuery(
            name = "Audit.findByMessage", query = "SELECT a from Audit a where a.message LIKE :message order by a.createdDate DESC"
        ), @NamedQuery(name = "Audit.cullExpired", query = "DELETE FROM Audit AS a WHERE A.createdDate < :expirationDate"),
        @NamedQuery(name = "Audit.cullOrphans", query = "DELETE FROM Audit AS a WHERE A.id IS NULL")
    }
)
@Table(indexes = { @Index(columnList = "entity_id") })
public class Audit implements Serializable, Identifiable {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Id
    private BigInteger id;
    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    @Lob
    private String message;
    @Basic(optional = false)
    @Column(nullable = false)
    private String hostName;
    @ManyToOne(optional = true)
    @JoinColumn(nullable = true, name = "entity_id")
    private JPAEntity entity;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Audit object.
     *
     * @param  message   The exception message. Cannot be null or empty.
     * @param  hostname  The host name that encountered the exception. Cannot be null or empty.
     * @param  entity    The entity which caused the exception. Cannot be null.
     */
    public Audit(String message, String hostname, JPAEntity entity) {
        setMessage(message);
        setHostName(hostname);
        setEntity(entity);
    }

    /** Creates a new Audit object. */
    protected Audit() { }

    //~ Methods **************************************************************************************************************************************

    /**
     * Finds all alerts for the given entity.
     *
     * @param   em      Entity manager. Cannot be null.
     * @param   entity  The entity for which all audits will be returned. Cannot be null.
     *
     * @return  All alerts belongs to given entity.
     */
    public static List<Audit> findByEntity(EntityManager em, JPAEntity entity) {
        return findByEntity(em, entity, null);
    }

    /**
     * Finds all alerts for the given entity.
     *
     * @param   em      Entity manager. Cannot be null.
     * @param   entity  The entity for which all audits will be returned. Cannot be null.
     * @param   limit   The number of results to return.
     *
     * @return  All alerts belongs to given entity.
     */
    public static List<Audit> findByEntity(EntityManager em, JPAEntity entity, BigInteger limit) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(entity != null, "Entity cannot be null.");

        TypedQuery<Audit> query = em.createNamedQuery("Audit.findByJPAEntity", Audit.class);

        if (limit != null) {
            query.setMaxResults(limit.intValue());
        }
        try {
            query.setParameter("jpaEntity", entity);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<Audit>(0);
        }
    }

    /**
     * Finds all alerts created by given host name.
     *
     * @param   em        The entity manager. Cannot be null.
     * @param   hostName  The name of the host. . Cannot be null or empty.
     *
     * @return  List of audits created by given host name.
     */
    public static List<Audit> findByHostName(EntityManager em, String hostName) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(hostName != null && !hostName.isEmpty(), "Host name cannot be null or empty.");

        TypedQuery<Audit> query = em.createNamedQuery("Audit.findByHostName", Audit.class);

        try {
            query.setParameter("hostName", hostName);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<Audit>(0);
        }
    }

    /**
     * Finds all audits.
     *
     * @param   em  Entity manager. Cannot be null.
     *
     * @return  All audits
     */
    public static List<Audit> findAll(EntityManager em) {
        requireArgument(em != null, "Entity manager cannot be null.");

        TypedQuery<Audit> query = em.createNamedQuery("Audit.findAll", Audit.class);

        try {
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<Audit>(0);
        }
    }

    /**
     * Finds all audits.
     *
     * @param   em  Entity manager. Cannot be null.
     *
     * @return  All audits
     */
    public static int deleteExpired(EntityManager em) {
        requireArgument(em != null, "Entity manager cannot be null.");

        Query query = em.createNamedQuery("Audit.cullExpired");

        query.setParameter("expirationDate", new Date(System.currentTimeMillis() - (2 * MILLIS_PER_WEEK)));
        return query.executeUpdate();
    }

    /**
     * Finds all audits.
     *
     * @param   em  Entity manager. Cannot be null.
     *
     * @return  All audits
     */
    public static int deleteOrphans(EntityManager em) {
        requireArgument(em != null, "Entity manager cannot be null.");

        Query query = em.createNamedQuery("Audit.cullOrphans");

        return query.executeUpdate();
    }

    /**
     * Finds audit history based on the entity, host name and message fragment.
     *
     * @param   em        The entity manager. Cannot be null.
     * @param   entity    The entity. If null, result will not be filtered by entity.
     * @param   hostName  The host name. If null, result will not be filtered by host name.
     * @param   message   The message fragment. If not null, result will be filtered by messages containing the fragment.
     *
     * @return  The audit history. Will not be null, but may be empty.
     */
    @SuppressWarnings("unchecked")
    public static List<Audit> findByEntityHostnameMessage(EntityManager em, JPAEntity entity, String hostName, String message) {
        boolean isAndrequired = false;
        StringBuilder queryString = new StringBuilder("SELECT a FROM Audit a ");

        if (entity != null) {
            isAndrequired = true;
            queryString.append(" WHERE a.entity=:entity ");
        }
        if (hostName != null && !hostName.isEmpty()) {
            if (isAndrequired) {
                queryString.append(" AND ");
            } else {
                isAndrequired = true;
                queryString.append(" WHERE ");
            }
            queryString.append(" a.hostName = :hostName ");
        }
        if (message != null && !message.isEmpty()) {
            if (isAndrequired) {
                queryString.append(" AND ");
            } else {
                isAndrequired = true;
                queryString.append(" WHERE ");
            }
            queryString.append(" a.message LIKE :message ");
        }

        Query query = em.createQuery(queryString.toString(), Audit.class);

        if (entity != null) {
            query.setParameter("entity", entity);
        }
        if (hostName != null && !hostName.isEmpty()) {
            query.setParameter("hostName", hostName);
        }
        if (message != null && !message.isEmpty()) {
            query.setParameter("message", "%" + message + "%");
        }
        return query.getResultList();
    }

    /**
     * Finds audits by exception message.
     *
     * @param   em       The entity manager. Cannot be null.
     * @param   message  The exception/error message.
     *
     * @return  All audits whose error message matches with given error message.
     */
    public static List<Audit> findByMessage(EntityManager em, String message) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(message != null && !message.isEmpty(), "Message cannot be null or empty.");

        TypedQuery<Audit> query = em.createNamedQuery("Audit.findByMessage", Audit.class);

        try {
            query.setParameter("message", "%" + message + "%");
            return query.getResultList();
        } catch (Exception ex) {
            return new ArrayList<Audit>(0);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /** Sets the audit record creation date. */
    @PrePersist
    @PreUpdate
    protected void preUpdate() {
        if (this.createdDate == null) {
            this.createdDate = new Date();
        }
    }

    /**
     * Returns the Id.
     *
     * @return  The Id.
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Returns the audit record creation time.
     *
     * @return  The timestamp when audit record was created
     */
    public Date getCreatedDate() {
        return createdDate == null ? null : new Date(createdDate.getTime());
    }

    /**
     * Returns the exception message.
     *
     * @return  The exception message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the exception message.
     *
     * @param  message  The exception message. Cannot be null or empty.
     */
    public void setMessage(String message) {
        requireArgument(message != null && !message.isEmpty(), "Message cannot be null or empty.");
        this.message = message;
    }

    /**
     * returns the host name which encountered exception/error.
     *
     * @return  The host name.
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the host name.
     *
     * @param  hostName  The host name. Cannot be null or empty.
     */
    public void setHostName(String hostName) {
        requireArgument(hostName != null && !hostName.isEmpty(), "Hostname cannot be null or empty.");
        this.hostName = hostName;
    }

    /**
     * Returns the JPA entity.
     *
     * @return  The JPA entity which caused exception. Cannot be null or empty.
     */
    public JPAEntity getEntity() {
        return entity;
    }

    /**
     * Sets the JPA entity.
     *
     * @param  entity  The JPA entity. Cannot be null or empty.
     */
    public void setEntity(JPAEntity entity) {
        this.entity = entity;
    }

    @Override
    public int hashCode() {
        int hash = 3;

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

        final Audit other = (Audit) obj;

        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Audit{" + "id=" + id + ", createdDate=" + createdDate + ", message=" + message + ", hostName=" + hostName + ", object=" +
            (entity == null ? null : entity.id) + '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
