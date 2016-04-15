package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.util.List;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;

import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.util.Cron;

/**
 * The entity encapsulates information about the policy.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>NAME</li>
 *   <li>SERVICE</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>SERVICE</li>
 *   <li>NAME</li>
 *   <li>OWNER</li>
 *   <li>USER</li>
 *   <li>SUB_SYSTEM</li>
 *   <li>METRIC_NAME</li>
 *   <li>TRIGGER_TYPE</li>
 *   <li>AGGREGATOR</li>
 *   <li>THRESHOLD</li>
 *   <li>TIME_UNIT</li>
 *   <li>DEFAULT_VALUE</li>
 *   <li>CRON_ENTRY</li>
 * </ul>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "POLICY", uniqueConstraints = @UniqueConstraint(columnNames = { "name","service" }))
@NamedQueries(
	    {
	        @NamedQuery(
	            name = "Policy.findByName", query = "SELECT r FROM Policy r WHERE r.name = :name AND r.service = :service"
	        )
	    }
	)
public class Policy extends JPAEntity {
	//~ Instance fields ******************************************************************************************************************************

	@Basic(optional = false)
	@Column(nullable = false)
    private String service;
	
	@Basic(optional = false)
	@Column(nullable = false)
    private String name;
	
	@Basic(optional = false)
	@Column(nullable = false)
    @ElementCollection
    private List<String> listOfOwner;
	
	@Basic(optional = false)
	@Column(nullable = false)
    @ElementCollection
    private List<String> listOfUser;
	
	@Basic(optional = true)
	@Column(name = "sub_system", nullable = false)
    private String subSystem;
	
	@Basic(optional = false)
    @Column(name = "metric_name", nullable = false)
    private String metricName;
	
	@Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type",nullable = false)
    private TriggerType triggerType;
	
	@Basic(optional = false)
    @Column(nullable = false)
    private String aggregator;
    
	@Basic(optional = false)
    @Column(nullable = false)
    private List<Double> threshold;
    
	@Basic(optional = false)
    @Column(name = "time_unit", nullable = false)
    private String timeUnit;
    
	@Basic(optional = false)
    @Column(name = "default_value",nullable = false)
    private double defaultValue;
    
	@Basic(optional = false)
    @Column(name = "cron_entry", nullable = false)
    private String cronEntry;
    
    @OneToMany(mappedBy="policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SuspensionLevel> suspensionLevelList;
    
    @OneToMany(mappedBy="policy", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Infraction> infractionList;
    
  //~ Constructors *********************************************************************************************************************************

	/**
     * Creates a new Policy object.
     *
     * @param  creator      The creator of this policy.
     * @param  service      The service for this policy. Cannot be null.
     * @param  name     	The name for this policy. Cannot be null.
     * @param  owner 		The owner for this policy. Cannot be null.
     * @param  user  		The user for this policy. Cannot be null.
     * @param  subSystem  	The subSystem for this policy. Cannot be null.
     * @param  metricName  	The metric name for this policy. Cannot be null.
     * @param  triggerType  The trigger type for this policy. Cannot be null.
     * @param  aggregator  	The aggregator for this policy. Cannot be null.
     * @param  threshold  	The threshold for this policy. Cannot be null.
     * @param  timeUnit  	The time unit for this policy. Cannot be null.
     * @param  defaultValue The default value for this policy. Cannot be null.
     * @param  cronEntry  	The cron entry for this policy. Cannot be null.
     */
    public Policy(PrincipalUser creator, String service, String name, List<String> owner, List<String> user, String subSystem,
			String metricName, TriggerType triggerType, String aggregator, List<Double> threshold, String timeUnit,
			double defaultValue, String cronEntry) {
		super(creator);
		setService(service);
		setName(name);
		setOwner(owner);
		setUser(user);
		setSubSystem(subSystem);
		setMetricName(metricName);
		setTriggerType(triggerType);
		setAggregator(aggregator);
		setThreshold(threshold);
		setTimeUnit(timeUnit);
		setDefaultValue(defaultValue);
		setCronEntry(cronEntry);
	}
    
    /** Creates a new Policy object. */
    protected Policy() {
        super(null);
    }
  //~ Methods **************************************************************************************************************************************
    /**
     * Finds a policy given its name.
     *
     * @param   em      The entity manager to use. Cannot be null.
     * @param   name  	The name of the policy. Cannot be null or empty.
     *
     * @return  The corresponding policy or null if no policy having the specified name exists for this name.
     */
    public static Policy findByName(EntityManager em, String name) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(name != null, "Policy name cannot be null or empty.");

        TypedQuery<Policy> query = em.createNamedQuery("Policy.findByName", Policy.class);
        
        try {
            query.setParameter("name", name);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }
    
	public String getService() {
		return service;
	}
	public void setService(String service) {
		this.service = service;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public List<String> getOwner() {
		return listOfOwner;
	}
	public void setOwner(List<String> owner) {
		this.listOfOwner = owner;
	}
	public List<String> getUser() {
		return listOfUser;
	}
	public void setUser(List<String> user) {
		this.listOfUser = user;
	}
	public String getSubSystem() {
		return subSystem;
	}
	public void setSubSystem(String subSystem) {
		this.subSystem = subSystem;
	}
	public String getMetricName() {
		return metricName;
	}
	public void setMetricName(String metricName) {
		this.metricName = metricName;
	}
	public TriggerType getTriggerType() {
		return triggerType;
	}
	public void setTriggerType(TriggerType triggerType) {
		this.triggerType = triggerType;
	}
	public String getAggregator() {
		return aggregator;
	}
	public void setAggregator(String aggregator) {
		this.aggregator = aggregator;
	}
	public List<Double> getThreshold() {
		return threshold;
	}
	public void setThreshold(List<Double> threshold) {
		this.threshold = threshold;
	}
	public String getTimeUnit() {
		return timeUnit;
	}
	public void setTimeUnit(String timeUnit) {
		this.timeUnit = timeUnit;
	}
	public double getDefaultValue() {
		return defaultValue;
	}
	public void setDefaultValue(double defaultValue) {
		this.defaultValue = defaultValue;
	}
	public String getCronEntry() {
		return cronEntry;
	}
	public void setCronEntry(String cronEntry) {
		if(Cron.isValid(cronEntry)){
			this.cronEntry = cronEntry;
		}else{
			throw new RuntimeException("Please provide a valid cron entry string.");
		}		
	}

	public List<SuspensionLevel> getSuspensionLevelList() {
		return suspensionLevelList;
	}

	public void setSuspensionLevelList(List<SuspensionLevel> suspensionLevelList) {
		this.suspensionLevelList = suspensionLevelList;
	}
    
    
}