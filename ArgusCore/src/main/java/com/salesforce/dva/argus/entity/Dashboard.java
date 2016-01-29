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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
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

/**
 * The entity which encapsulates information about a Dashboard.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>DASHBOARD_NAME</li>
 *   <li>OWNER</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>DASHBOARD_NAME</li>
 *   <li>OWNER</li>
 * </ul>
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "DASHBOARD", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "owner_id" }))
@NamedQueries(
    {
        @NamedQuery(name = "Dashboard.findByNameAndOwner", query = "SELECT d FROM Dashboard d WHERE d.name = :name AND d.owner = :owner"),
        @NamedQuery(
            name = "Dashboard.getSharedDashboards", query = "SELECT d FROM Dashboard d WHERE d.shared = true"
        ), @NamedQuery(
            name = "Dashboard.getDashboardsOwnedBy", query = "SELECT d FROM Dashboard d WHERE d.owner = :owner"
        ), @NamedQuery(name = "Dashboard.getDashboards", query = "SELECT d FROM Dashboard d ORDER BY d.owner.userName,d.name ASC")
    }
)
public class Dashboard extends JPAEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(nullable = false)
    private String name;
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private PrincipalUser owner;
    @Lob
    private String content;
    private String description;
    @Basic
    private boolean shared;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Dashboard object.
     *
     * @param  creator        The creator of this dashboard.
     * @param  dashboardName  The name for the dashboard. Cannot be null or empty.
     * @param  owner          The owner of this dashboard. This need not be the same as the creator. Cannoy be null.
     */
    public Dashboard(PrincipalUser creator, String dashboardName, PrincipalUser owner) {
        super(creator);
        setName(dashboardName);
        setOwner(owner);
    }

    /** Creates a new Dashboard object. */
    protected Dashboard() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Finds Dashboard in the database with the specified dashboard name and owned by the specified owner.
     *
     * @param   em             The entity manager to use.
     * @param   dashboardName  The name of the dashboard to retrieve. Cannot be null or empty.
     * @param   owner          The owner of the dashboard. Cannot be null.
     *
     * @return  The dashboard or null if no dashboard exists.
     */
    public static Dashboard findByNameAndOwner(EntityManager em, String dashboardName, PrincipalUser owner) {
        TypedQuery<Dashboard> query = em.createNamedQuery("Dashboard.findByNameAndOwner", Dashboard.class);

        try {
            query.setParameter("name", dashboardName);
            query.setParameter("owner", owner);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Finds the list of dashboards that are marked as globally shared.
     *
     * @param   em  The entity manager to use.
     *
     * @return  Dashboards that are shared/global within the system. Or empty list if no such dashboards exist.
     */
    public static List<Dashboard> findSharedDashboards(EntityManager em) {
        TypedQuery<Dashboard> query = em.createNamedQuery("Dashboard.getSharedDashboards", Dashboard.class);

        try {
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns the list of dashboards owned by the user.
     *
     * @param   em    The entity manager to use. Cannot be null.
     * @param   user  The user to retrieve dashboards for. Cannot be null.
     *
     * @return  The list of owned dashboards. Will not be null, but may be empty.
     */
    public static List<Dashboard> findDashboardsByOwner(EntityManager em, PrincipalUser user) {
        TypedQuery<Dashboard> query = em.createNamedQuery("Dashboard.getDashboardsOwnedBy", Dashboard.class);

        try {
            query.setParameter("owner", user);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns dashboards ordered by owner and dashboard name.
     *
     * @param   em     The entity manager to use.  Cannot be null.
     * @param   limit  The maximum number of dashboards to retrieve.  If null, all records will be returned, otherwise must be a positive non-zero number.
     *
     * @return  The list of dashboards.  Will never be null but may be empty.
     */
    public static List<Dashboard> findDashboards(EntityManager em, Integer limit) {
        TypedQuery<Dashboard> query = em.createNamedQuery("Dashboard.getDashboards", Dashboard.class);

        try {
            if (limit != null) {
                query.setMaxResults(limit);
            }
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the Dashboard name.
     *
     * @return  The dashboard name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name for this Dashboard.
     *
     * @param  name  The new name for the Dashboard. Cannot be null or empty.
     */
    public void setName(String name) {
        SystemAssert.requireArgument(name != null && !name.isEmpty(), "Dashboard Name cannot be null or empty");
        this.name = name;
    }

    /**
     * Returns the description of the dashboard.
     *
     * @return  The dashboard description. Can be null.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the dashboard.
     *
     * @param  description  The dashboard description. Can be null.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the dashboard content conforming to the Argus dashboard schema.
     *
     * @return  The dashboard content.
     */
    public String getContent() {
        return content;
    }

    /**
     * Sets the dashboard content which must conform to the Argus dashboard schema.
     *
     * @param  content  The dashboard content.
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Returns the Owner of this Dashboard.
     *
     * @return  The PrincipalUser that is the owner of this Dashboard.
     */
    public PrincipalUser getOwner() {
        return owner;
    }

    /**
     * Sets the owner of this Dashboard. Cannot be null.
     *
     * @param  owner  The PrincipalUser to set as owner of this Dashboard.
     */
    public void setOwner(PrincipalUser owner) {
        SystemAssert.requireArgument(owner != null, "Owner cannot be null");
        this.owner = owner;
    }

    /**
     * Returns true or false depending on whether the Dashboard is shared globally or not.
     *
     * @return  A boolean depending on whether the Dashboard is shared globally or not.
     */
    public boolean isShared() {
        return shared;
    }

    /**
     * Sets the global flag for this Dashboard.
     *
     * @param  shared  The shared boolean flag.
     */
    public void setShared(boolean shared) {
        this.shared = shared;
    }

    @Override
    public int hashCode() {
        int hash = 3;

        hash = 53 * hash + Objects.hashCode(this.name);
        hash = 53 * hash + Objects.hashCode(this.owner);
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

        final Dashboard other = (Dashboard) obj;

        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.owner, other.owner)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Dashboard{" + "name=" + name + ", owner=" + owner + ", content=" + content + ", description=" + description + ", shared=" + shared +
            '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
