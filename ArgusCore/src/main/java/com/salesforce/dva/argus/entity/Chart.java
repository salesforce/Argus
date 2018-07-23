package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.*;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.MapKeyColumn;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.salesforce.dva.argus.system.SystemAssert;

/**
 * The entity which encapsulates information about a Chart.
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>CHART_TYPE</li>
 *   <li>QUERIES</li>
 * </ul>
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "CHART")
@NamedQueries(
	    {
	        @NamedQuery(name = "Chart.getChartsByOwner", query = "SELECT c FROM Chart c WHERE c.owner = :owner"),
	        @NamedQuery(name = "Chart.getChartsForEntity", query = "SELECT c FROM Chart c WHERE c.entity.id = :entityId"),
	        @NamedQuery(name = "Chart.getChartsByOwnerForEntity", query = "SELECT c FROM Chart c WHERE c.entity.id = :entityId AND c.owner = :owner")
	    }
	)
public class Chart extends JPAEntity implements Serializable {

	//~ Instance Fields **************************************************************************************************************************************
	
	@Basic(optional = true)
	@Column(nullable = true)
	private String title;

    @Column(nullable = true)
    private String description;
	
	@Enumerated(EnumType.STRING)
	private ChartType type;
	
	@ElementCollection(fetch = FetchType.LAZY)
	@Embedded
	private List<ChartQuery> queries = new ArrayList<>(0);

    @ElementCollection(fetch = FetchType.LAZY)
	@MapKeyColumn(name="name")
	@Column(name="value")
	Map<String, String> preferences = new HashMap<>();
	
	@ManyToOne(optional = true, fetch = FetchType.LAZY)
	@JoinColumn(name="entity_id", nullable = true)
	private JPAEntity entity;
	
	@ManyToOne(optional = false)
	@JoinColumn(name="owner_id", nullable = false)
	private PrincipalUser owner;
	
	//~ Constructors **************************************************************************************************************************************
	
	protected Chart() {
		super(null);
	}
	
	public Chart(PrincipalUser creator, PrincipalUser owner) {
		super(creator);
		setOwner(owner);
	}
	
	public Chart(PrincipalUser creator, PrincipalUser owner, ChartType type, List<ChartQuery> queries) {
		super(creator);
		setOwner(owner);
		setType(type);
		setQueries(queries);
	}
	
	//~ Static Methods **************************************************************************************************************************************
	
	public static List<Chart> getChartsByOwner(EntityManager em, PrincipalUser user) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		TypedQuery<Chart> query = em.createNamedQuery("Chart.getChartsByOwner", Chart.class);
		
		try {
            query.setParameter("owner", user);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
	}
	
	public static List<Chart> getChartsForEntity(EntityManager em, BigInteger entityId) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		TypedQuery<Chart> query = em.createNamedQuery("Chart.getChartsForEntity", Chart.class);
		
		try {
            query.setParameter("entityId", entityId);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
		
	}
	
	public static List<Chart> getChartsByOwnerForEntity(EntityManager em, PrincipalUser user, BigInteger entityId) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		TypedQuery<Chart> query = em.createNamedQuery("Chart.getChartsByOwnerForEntity", Chart.class);
		
		try {
			query.setParameter("owner", user);
            query.setParameter("entityId", entityId);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
		
	}

	//~ Methods **************************************************************************************************************************************
	
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public ChartType getType() {
		return type;
	}

	public void setType(ChartType type) {
		SystemAssert.requireArgument(type != null, "Chart type cannot be null");
		this.type = type;
	}

    /**
     * Returns the description of the Chart. It can be null.
     *
     * @return chart description
     */

    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the chart.
     *
     * @param description The chart description
     */

    public void setDescription(String description) {
        this.description = description;
    }

    public List<ChartQuery> getQueries() {
		return Collections.unmodifiableList(queries);
	}

	public void setQueries(List<ChartQuery> queries) {
		SystemAssert.requireArgument(queries != null && !queries.isEmpty(), "Chart queries cannot be null or empty.");
		this.queries.clear();
		this.queries.addAll(queries);
	}
	
	public void addQuery(ChartQuery query) {
		SystemAssert.requireArgument(query != null, "Cannot add a null query for a chart.");
		this.queries.add(query);
	}

	public JPAEntity getEntity() {
		return entity;
	}

	public void setEntity(JPAEntity entity) {
		this.entity = entity;
	}

	public PrincipalUser getOwner() {
		return owner;
	}

	public void setOwner(PrincipalUser owner) {
		SystemAssert.requireArgument(owner != null, "Owner cannot be null");
		this.owner = owner;
	}

    public Map<String, String> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, String> preferences) {
        this.preferences = preferences;
    }


    @Override
    public String toString() {
        return "Chart{" + "title=" + title + ", description=" + description + ", owner=" + owner + ", type=" + type + ", queries=" + queries +
        		", entity=" + entity + "}";
    }
	
	//~ Nested Classes **************************************************************************************************************************************
	
	@Embeddable
	public static class ChartQuery implements Serializable {
		
		@Enumerated(EnumType.STRING)
		private ChartQueryType type;
		
		@Basic
		private String query;
		
		protected ChartQuery() {}
		
		public ChartQuery(ChartQueryType type, String query) {
			setType(type);
			setQuery(query);
		}

		public ChartQueryType getType() {
			return type;
		}

		public void setType(ChartQueryType type) {
			this.type = type;
		}

		public String getQuery() {
			return query;
		}

		public void setQuery(String query) {
			this.query = query;
		}
		
		@Override
		public String toString() {
			return "ChartQuery{" + "type=" + type + ", query=" + query + "}";
		}
	}
	
	//~ Enuns **************************************************************************************************************************************
	
	public enum ChartType {

		LINE,
		AREA,
		HEATMAP,
		STACKED_AREA,
		SANKEY,
		BAR,
		STACKED_BAR,
		INDICATOR,
		SCATTER_PLOT,
		DISTRIBUTION_LIST,
		TOP_LIST;
		
		/**
		 * 
		 * @param name 	The chart type name
		 * @return      The chart type
		 */
		@JsonCreator
		public static ChartType fromName(String name) {
			for(ChartType t : ChartType.values()) {
				if(t.name().equalsIgnoreCase(name)) {
					return t;
				}
			}
			
			throw new IllegalArgumentException("ChartType " + name + " does not exist. Allowed values are: " + Arrays.asList(ChartType.values()));
		}
		
		/**
         * Returns the name of the chart type.
         *
         * @return  The name of the chart type.
         */
        @JsonValue
        public String value() {
            return this.toString();
        }
		
	}
	
	public enum ChartQueryType {
		
		METRIC,
		ANNOTATION,
		LOG;
		
		/**
		 * 
		 * @param name 	The chart query type name
		 * @return      The chart query type
		 */
		@JsonCreator
		public static ChartQueryType fromName(String name) {
			for(ChartQueryType t : ChartQueryType.values()) {
				if(t.name().equalsIgnoreCase(name)) {
					return t;
				}
			}
			
			throw new IllegalArgumentException("ChartQueryType " + name + " does not exist. Allowed values are: " + Arrays.asList(ChartQueryType.values()));
		}
		
		/**
		 * Returns the name of chart query type.
		 * 
		 * @return  The name of chart query type.
		 */
		@JsonValue
        public String value() {
            return this.toString();
        }
	}
	
}
