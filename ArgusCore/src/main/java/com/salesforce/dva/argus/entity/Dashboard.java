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
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
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
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;

import org.apache.commons.lang3.reflect.FieldUtils;

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
    @Metadata
    private String name;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @Metadata
    private PrincipalUser owner;
    
    @Lob
    private String content;
    
    @Metadata
    private String description;
    
    @Basic
    @Metadata
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
    	requireArgument(em != null, "Entity manager can not be null.");
    	
    	TypedQuery<Dashboard> query = em.createNamedQuery("Dashboard.getSharedDashboards", Dashboard.class);

        try {
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
    public static List<Dashboard> findSharedDashboardsMeta(EntityManager em) {
    	requireArgument(em != null, "Entity manager can not be null.");
    	
    	try {
        	CriteriaBuilder cb = em.getCriteriaBuilder();
        	CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        	Root<Dashboard> e = cq.from(Dashboard.class);
        	
        	List<Selection<?>> fieldsToSelect = new ArrayList<>();
        	for(Field field : FieldUtils.getFieldsListWithAnnotation(Dashboard.class, Metadata.class)) {
        		fieldsToSelect.add(e.get(field.getName()).alias(field.getName()));
        	}
        	cq.multiselect(fieldsToSelect);
        	cq.where(cb.equal(e.get("shared"), true));
        	
        	return _readDashboards(em, cq, null);
        	
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
    	requireArgument(em != null, "Entity manager can not be null.");
    	
        TypedQuery<Dashboard> query = em.createNamedQuery("Dashboard.getDashboardsOwnedBy", Dashboard.class);

        try {
            query.setParameter("owner", user);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
    public static List<Dashboard> findDashboardsByOwnerMeta(EntityManager em, PrincipalUser user) {
    	requireArgument(em != null, "Entity manager can not be null.");
    	
    	try {
        	CriteriaBuilder cb = em.getCriteriaBuilder();
        	CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        	Root<Dashboard> e = cq.from(Dashboard.class);
        	
        	List<Selection<?>> fieldsToSelect = new ArrayList<>();
        	for(Field field : FieldUtils.getFieldsListWithAnnotation(Dashboard.class, Metadata.class)) {
        		fieldsToSelect.add(e.get(field.getName()).alias(field.getName()));
        	}
        	cq.multiselect(fieldsToSelect);
        	cq.where(cb.equal(e.get("owner"), user));
        	
        	return _readDashboards(em, cq, null);
        	
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
    	requireArgument(em != null, "Entity manager can not be null.");
    	
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
    
    public static List<Dashboard> findDashboardsMeta(EntityManager em, Integer limit) {
    	requireArgument(em != null, "Entity manager can not be null.");
        
        try {
        	CriteriaBuilder cb = em.getCriteriaBuilder();
        	CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        	Root<Dashboard> e = cq.from(Dashboard.class);
        	
        	List<Selection<?>> fieldsToSelect = new ArrayList<>();
        	for(Field field : FieldUtils.getFieldsListWithAnnotation(Dashboard.class, Metadata.class)) {
        		fieldsToSelect.add(e.get(field.getName()).alias(field.getName()));
        	}
        	cq.multiselect(fieldsToSelect);
        	
        	return _readDashboards(em, cq, limit);
        	
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
	private static List<Dashboard> _readDashboards(EntityManager em, CriteriaQuery<Tuple> cq, Integer limit) {
		
		List<Dashboard> dashboards = new ArrayList<>();
		
		TypedQuery<Tuple> query = em.createQuery(cq);
		if (limit != null) {
            query.setMaxResults(limit);
        }
		
		List<Tuple> result = query.getResultList();
		for(Tuple tuple : result) {
			Dashboard d = new Dashboard(PrincipalUser.class.cast(tuple.get("createdBy")), 
										String.class.cast(tuple.get("name")), 
										PrincipalUser.class.cast(tuple.get("owner")));
			
			d.id = BigInteger.class.cast(tuple.get("id"));
			if(tuple.get("description") != null) {
				d.description = String.class.cast(tuple.get("description"));
			}
			d.createdDate = Date.class.cast(tuple.get("createdDate"));
			d.modifiedDate = Date.class.cast(tuple.get("modifiedDate"));
			d.shared = Boolean.class.cast(tuple.get("shared"));
			d.modifiedBy = PrincipalUser.class.cast(tuple.get("modifiedBy"));
			
			dashboards.add(d);
		}
		
		return dashboards;
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
