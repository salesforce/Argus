package com.salesforce.dva.argus.entity;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.text.MessageFormat;
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
	            name = "Policy.findByNameAndService", query = "SELECT r FROM Policy r WHERE r.name = :name AND r.service = :service"
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
    private List<String> owners;
	
	@Basic(optional = false)
	@Column(nullable = false)
    @ElementCollection
    private List<String> users;
	
	@Basic(optional = true)
	@Column(name = "sub_system")
    private String subSystem;
	
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
	public Policy(PrincipalUser creator, String service, String name, List<String> owners, List<String> users,
			 TriggerType triggerType, String aggregator, List<Double> threshold, String timeUnit,
			double defaultValue, String cronEntry) {
		super(creator);
		setService(service);
		setName(name);
		setOwners(owners);
		setUsers(users);
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
    public static Policy findByNameAndService(EntityManager em, String name, String service) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(name != null, "Policy name cannot be null or empty.");

        TypedQuery<Policy> query = em.createNamedQuery("Policy.findByNameAndService", Policy.class);
        
        try {
            query.setParameter("name", name);
            query.setParameter("service", service);
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
	public List<String> getOwners() {
		return owners;
	}
	public void setOwners(List<String> owners) {
		this.owners = owners;
	}
	public List<String> getUsers() {
		return users;
	}
	public void setUsers(List<String> users) {
		this.users = users;
	}
	public String getSubSystem() {
		return subSystem;
	}
	public void setSubSystem(String subSystem) {
		this.subSystem = subSystem;
	}
	public String getMetricName() {
		String scope = getSubSystem() == null ? getService() : getService() + "." + getSubSystem(); 
		
		Object[] params = {getTimeUnit(), scope, getName(), getUsers().get(0), getAggregator()};        
		String format = "{0}:{1}:{2}'{'user={3}'}':{4}";
        return MessageFormat.format(format, params);
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
			throw new IllegalArgumentException("Please provide a valid cron entry string.");
		}		
	}

	public List<SuspensionLevel> getSuspensionLevels() {
		suspensionLevelList.sort((s1, s2) -> s1.getLevelNumber() - s2.getLevelNumber());
		return suspensionLevelList;
	}

	public void setSuspensionLevels(List<SuspensionLevel> suspensionLevelList) {
		this.suspensionLevelList = suspensionLevelList;
	}
    
    
}