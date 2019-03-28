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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.salesforce.dva.argus.system.SystemAssert;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
import javax.persistence.criteria.*;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.persistence.internal.jpa.querydef.PredicateImpl;

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
           @NamedQuery(name = "Dashboard.findByNameAndOwner", query = "SELECT d FROM Dashboard d WHERE d.name = :name AND d.owner = :owner"
        ), @NamedQuery(name = "Dashboard.getSharedDashboards", query = "SELECT d FROM Dashboard d WHERE d.shared = true AND d.version IS NULL"
        ), @NamedQuery(name = "Dashboard.getSharedDashboardsByVersion", query = "SELECT d FROM Dashboard d WHERE d.shared = true AND d.version = :version"
        ), @NamedQuery(name = "Dashboard.getDashboardsByOwner", query = "SELECT d FROM Dashboard d WHERE d.owner = :owner AND d.version IS NULL"
        ), @NamedQuery(name = "Dashboard.getDashboardsByOwnerAndByVersion", query = "SELECT d FROM Dashboard d WHERE d.owner = :owner AND d.version = :version"
        ), @NamedQuery(name = "Dashboard.getDashboards", query = "SELECT d FROM Dashboard d WHERE d.version IS NULL ORDER BY d.owner.userName,d.name ASC"
        ), @NamedQuery(name = "Dashboard.getDashboardsByVersion", query = "SELECT d FROM Dashboard d WHERE d.version = :version ORDER BY d.owner.userName,d.name ASC"
        ), @NamedQuery(name = "Dashboard.getSharedDashboardsByOwner",query = "SELECT d FROM Dashboard d WHERE d.owner = :owner AND d.shared = true AND d.version IS NULL"
		), @NamedQuery(name = "Dashboard.getSharedDashboardsByOwnerAndByVersion",query = "SELECT d FROM Dashboard d WHERE d.owner = :owner AND d.shared = true AND d.version = :version"
        )
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
    
    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private LayoutType layout = LayoutType.SMALL;

    @Metadata
    private String version;
    
    @ElementCollection
    @Embedded
    @Column(nullable = true)
    private List<TemplateVar> templateVars = new ArrayList<>(0);

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Dashboard object.
     *
     * @param  creator        The creator of this dashboard.
     * @param  dashboardName  The name for the dashboard. Cannot be null or empty.
     * @param  owner          The owner of this dashboard. This need not be the same as the creator. Cannot be null.
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

    //~ Static Methods **************************************************************************************************************************************

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
     * @param   em       The entity manager to use.
     * @param   owner    The owner of shared dashboards to filter on 
     * @param   limit    The maximum number of rows to return.
     * @param   version  The version of the dashboard to retrieve. It is either null or not empty
     *
     * @return  Dashboards that are shared/global within the system. Or empty list if no such dashboards exist.
     */
    public static List<Dashboard> findSharedDashboards(EntityManager em,  PrincipalUser owner, Integer limit, String version) {
    	requireArgument(em != null, "Entity manager can not be null.");

		TypedQuery<Dashboard> query;
		if(owner == null){
		    if (version==null) {
			    query = em.createNamedQuery("Dashboard.getSharedDashboards", Dashboard.class);
            }
            else {
                query = em.createNamedQuery("Dashboard.getSharedDashboardsByVersion", Dashboard.class);
                query.setParameter("version",version);
            }
		} else {
		    if(version==null) {
                query = em.createNamedQuery("Dashboard.getSharedDashboardsByOwner", Dashboard.class);
                query.setParameter("owner", owner);
            }
            else {
                query = em.createNamedQuery("Dashboard.getSharedDashboardsByOwnerAndByVersion", Dashboard.class);
                query.setParameter("owner", owner);
                query.setParameter("version",version);
            }

		}
		
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");

		if(limit!= null){
			query.setMaxResults(limit);
		}
		
        try {
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
 
    /**
     * Gets all meta information of shared dashboards with filtering.
     *
     * @param   em       The entity manager to use.
     * @param   owner    The owner of shared dashboards to filter on 
     * @param   limit    The maximum number of rows to return.
     * @param   version  The version of the dashboard to retrieve. It is either null or not empty
     *
     * @return  The list of all shared dashboards with meta information only. Will never be null but may be empty.
     */    
    public static List<Dashboard> findSharedDashboardsMeta(EntityManager em, PrincipalUser owner, Integer limit, String version) {
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
			if(owner != null){
				cq.where(cb.equal(e.get("shared"), true), cb.equal(e.get("owner"), owner), version==null?cb.isNull(e.get("version")):cb.equal(e.get("version"), version));
			} else{
	        	cq.where(cb.equal(e.get("shared"), true),version==null?cb.isNull(e.get("version")):cb.equal(e.get("version"), version));
			}

        	return _readDashboards(em, cq, limit);

        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns the list of dashboards owned by the user.
     *
     * @param   em    The entity manager to use. Cannot be null.
     * @param   user  The user to retrieve dashboards for. Cannot be null.
     * @param   version The version of the dashboard to retrieve. It is either null or not empty
     *
     * @return  The list of owned dashboards. Will not be null, but may be empty.
     */
    public static List<Dashboard> findDashboardsByOwner(EntityManager em, PrincipalUser user, String version) {
    	requireArgument(em != null, "Entity manager can not be null.");
        TypedQuery<Dashboard> query;
        if(version==null) {
            query = em.createNamedQuery("Dashboard.getDashboardsByOwner", Dashboard.class);
        }
        else
        {
            query = em.createNamedQuery("Dashboard.getDashboardsByOwnerAndByVersion", Dashboard.class);
            query.setParameter("version",version);
        }

        try {
            query.setParameter("owner", user);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
    public static List<Dashboard> findDashboardsByOwnerMeta(EntityManager em, PrincipalUser user, String version) {
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
        	cq.where(cb.equal(e.get("owner"), user),version==null?cb.isNull(e.get("version")):cb.equal(e.get("version"), version));
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
     * @param   version The version of the dashboard to retrieve. It is either null or not empty
     *
     * @return  The list of dashboards.  Will never be null but may be empty.
     */
    public static List<Dashboard> findDashboards(EntityManager em, Integer limit, String version) {
    	requireArgument(em != null, "Entity manager can not be null.");
        TypedQuery<Dashboard> query;

        if(version==null) {
            query= em.createNamedQuery("Dashboard.getDashboards", Dashboard.class);
        }
        else
        {
            query = em.createNamedQuery("Dashboard.getDashboardsByVersion", Dashboard.class);
            query.setParameter("version",version);
        }
        try {
            if (limit != null) {
                query.setMaxResults(limit);
            }
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
    public static List<Dashboard> findDashboardsMeta(EntityManager em, Integer limit, String version) {
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
        	if(version==null) {
                cq.where(cb.isNull(e.get("version")));
            }
        	else {
                cq.where(cb.equal(e.get("version"), version));
            }
        	return _readDashboards(em, cq, limit);
        	
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
	private static List<Dashboard> _readDashboards(EntityManager em, CriteriaQuery<Tuple> cq, Integer limit) {
		
		List<Dashboard> dashboards = new ArrayList<>();
		
		TypedQuery<Tuple> query = em.createQuery(cq);
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		
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
			d.version = String.class.cast(tuple.get("version"));

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

    /**
     * Returns the layout of this dashboard. It can be either LayoutType.SMALL, LayoutType.MEDIUM or LayoutType.LARGE.  
     * 
     * @return The dashboard layout
     */
    public LayoutType getLayout() {
		return layout;
	}

    /**
     * Sets the layout for this dashboard. It can be either LayoutType.SMALL, LayoutType.MEDIUM or LayoutType.LARGE.
     * 
     * @param layout  The layout for this dashboard.
     */
	public void setLayout(LayoutType layout) {
		this.layout = layout;
	}

    /**
     * Returns the version of the dashboard.
     *
     * @return The dashboard version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Sets the Dashboard Version
     *
     * @param version The dashboard version
     */

    public void setVersion(String version) {
        this.version = version;
    }

    /**
	 * Returns the template variables used in this dashboard.
	 * 
	 * @return  The template variables.
	 */
	public List<TemplateVar> getTemplateVars() {
		return Collections.unmodifiableList(this.templateVars);
	}

	/**
	 * Sets the template variables used in this dashboard.
	 * 
	 * @param templateVars  A list of template variables. If the list is null or empty then this is a no-op.
	 */
	public void setTemplateVars(List<TemplateVar> templateVars) {
		this.templateVars.clear();
		if(templateVars != null && !templateVars.isEmpty()) {
			this.templateVars.addAll(templateVars);
		}
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
        return "Dashboard{" + "name=" + name + ", owner=" + owner + ", content=" + content + ", description=" + description + ", shared=" + shared + ", version=" +version+
            '}';
    }
    
    //~ Nested Classes **************************************************************************************************************************************
    
    @Embeddable
    public static class TemplateVar implements Serializable {
    	
    	@Basic
    	@Column(name = "var_key")
    	private String key;
    	
    	@Basic
    	private String displayName;
    	
    	@Basic
    	private String defaultValue;
    	
    	@Basic
    	private String[] options;
    	
    	protected TemplateVar() {}
    	
    	public TemplateVar(String key, String defaultValue) {
    		setKey(key);
    		setDisplayName(key);
    		setDefaultValue(defaultValue);
    	}

		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			SystemAssert.requireArgument(key != null && !key.isEmpty(), "Template variable key cannot be null or empty.");
			this.key = key;
		}

		public String getDisplayName() {
			return displayName;
		}

		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}

		public String getDefaultValue() {
			return defaultValue;
		}

		public void setDefaultValue(String defaultValue) {
			this.defaultValue = defaultValue;
		}

		public String[] getOptions() {
			return options;
		}

		public void setOptions(List<String> options) {
			if(options != null && !options.isEmpty()) {
				this.options = new String[options.size()];
				for(int i=0; i<options.size(); i++) {
					this.options[i] = options.get(i);
				}
			}
		}
    }
    
    //~ Enuns **************************************************************************************************************************************
    
    public enum LayoutType {
    	
    	/** Denotes a dashboard with small charts */
    	SMALL,
    	/** Denotes a dashboard with medium sized charts */
    	MEDIUM,
    	/** Denotes a dashboard with large charts */
    	LARGE;
    	
    	@JsonCreator
        public static LayoutType fromString(String name) {
            for (LayoutType t : LayoutType.values()) {
                if (t.toString().equalsIgnoreCase(name)) {
                    return t;
                }
            }
            
            throw new IllegalArgumentException("LayoutType " + name + " does not exist. Allowed values are: " + Arrays.asList(LayoutType.values()));
        }

        /**
         * Returns the name of the layout type.
         *
         * @return  The name of the layout type.
         */
        @JsonValue
        public String value() {
            return this.toString();
        }
    }
    
}


/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
