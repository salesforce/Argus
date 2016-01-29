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

import com.salesforce.dva.argus.system.SystemAssert;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

/**
 * Namespace used to categorize groups of metrics to enforce access authority and name collision avoidance.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>QUALIFIER</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>QUALIFIER</li>
 *   <li>OWNER</li>
 *   <li>CREATED_BY</li>
 * </ul>
 * 
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@NamedQueries(
    {
        @NamedQuery(name = "Namespace.findByQualifier", query = "SELECT n FROM Namespace n WHERE n.qualifier = :qualifier"),
        @NamedQuery(name = "Namespace.findByOwner", query = "SELECT n FROM Namespace n WHERE n.owner = :owner")
    }
)
public class Namespace extends JPAEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(nullable = false, unique = true)
    private String qualifier;
    private Set<PrincipalUser> users = new HashSet<>();
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private PrincipalUser owner;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Namespace object.  The creator of the namespace will also be the owner.
     *
     * @param  qualifier  The unique name of the namespace.  Cannot be null.
     * @param  creator    The creator of the namespace.  Cannot be null.
     */
    public Namespace(String qualifier, PrincipalUser creator) {
        this(creator, qualifier, creator, new HashSet<PrincipalUser>());
    }

    /**
     * Creates a new Namespace object.
     *
     * @param  creator    The creator of the namespace.  Cannot be null.
     * @param  qualifier  The unique name of the namespace.  Cannot be null.
     * @param  owner      The owner of the namespace.  Cannot be null.
     * @param  users      The set of users allowed access to the namespace.  May be null or empty.
     */
    public Namespace(PrincipalUser creator, String qualifier, PrincipalUser owner, Set<PrincipalUser> users) {
        super(creator);
        setQualifier(qualifier);
        setOwner(owner);
        if (users != null && !users.isEmpty()) {
            setUsers(users);
        }
        addUser(owner);
    }

    /** Creates a new Namespace object. */
    protected Namespace() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns a namespace entity using the provided namespace qualifier.
     *
     * @param   em         The entity manager.  Cannot be null.
     * @param   qualifier  The qualifier.  Cannot be null or empty.
     *
     * @return  The namespace or null if no corresponding namespace is found.
     */
    public static Namespace findByQualifier(EntityManager em, String qualifier) {
        SystemAssert.requireArgument(em != null, "EntityManager cannot be null.");
        SystemAssert.requireArgument(qualifier != null && !qualifier.isEmpty(), "Namespace qualifier cannot be null or empty.");

        TypedQuery<Namespace> query = em.createNamedQuery("Namespace.findByQualifier", Namespace.class);

        try {
            return query.setParameter("qualifier", qualifier).getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Finds all namespaces for a given owner.
     *
     * @param   em     The entity manager to use.  Cannot be null.
     * @param   owner  The owner for which to retrieve dashboards.  Cannot be null.
     *
     * @return  The dashboards for the owner.  Will never be null, but may be empty.
     */
    public static List<Namespace> findByOwner(EntityManager em, PrincipalUser owner) {
        SystemAssert.requireArgument(em != null, "EntityManager cannot be null.");
        SystemAssert.requireArgument(owner != null, "Owner cannot be null or empty.");

        TypedQuery<Namespace> query = em.createNamedQuery("Namespace.findByOwner", Namespace.class);

        try {
            return query.setParameter("owner", owner).getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespace qualifier.
     *
     * @return  The namespace qualifier.  Will never return null.
     */
    public String getQualifier() {
        return qualifier;
    }

    /**
     * Sets the namespace qualifier.
     *
     * @param  qualifier  The namespace qualifier.  Cannot be null or empty.
     */
    public void setQualifier(String qualifier) {
        SystemAssert.requireArgument(qualifier != null && !qualifier.isEmpty(), "Namespace qualifier cannot be null or empty.");
        this.qualifier = qualifier;
    }

    /**
     * Returns the users associated with the namespace.
     *
     * @return  The users associated with the namespace.  Will never return null, but may be empty.
     */
    public Set<PrincipalUser> getUsers() {
        return users;
    }

    /**
     * Sets the users associated with the namespace.
     * @todo Review if users can be empty.  What if only the owner should have access?
     *
     * @param  users  The set of users associated with the namespace.  Cannot be null or empty.
     */
    public void setUsers(Set<PrincipalUser> users) {
        SystemAssert.requireArgument(users != null && !users.isEmpty(), "Users associated with a namespace cannot be null or empty.");
        this.users.clear();
        if (users != null && !users.isEmpty()) {
            this.users.addAll(users);
        }
    }

    /**
     * Adds an authorized user of the namespace.
     *
     * @param  user  The authorized user.  Cannot be null.
     */
    public void addUser(PrincipalUser user) {
        SystemAssert.requireArgument(user != null, "Null user can not be added.");
        users.add(user);
    }

    /**
     * Sets the owner of the namespace.
     *
     * @return  The namespace owner.  Will never return null.
     */
    public PrincipalUser getOwner() {
        return owner;
    }

    /**
     * Sets the owner of the namespace.
     *
     * @param  owner  The owner of the namespace.  Cannot be null.
     */
    public void setOwner(PrincipalUser owner) {
        requireArgument(owner != null, "The owner cannot be null.");
        this.owner = owner;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();

        result = prime * result + ((qualifier == null) ? 0 : qualifier.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Namespace other = (Namespace) obj;

        if (qualifier == null) {
            if (other.qualifier != null) {
                return false;
            }
        } else if (!qualifier.equals(other.qualifier)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return MessageFormat.format("Namesapce'{'qualifier={0}, users={1}'}'", this.qualifier, this.users);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
