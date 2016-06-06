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

import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.util.Cron;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.OneToMany;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * The entity which encapsulates information about a Dashboard.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>ALERT_NAME</li>
 *   <li>OWNER</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>ALERT_NAME</li>
 *   <li>OWNER</li>
 * </ul>
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "ALERT", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "owner_id" }))
@NamedQueries(
    {
        @NamedQuery(
            name = "Alert.findByNameAndOwner",
            query =
                "SELECT a FROM Alert a WHERE a.name = :name AND a.owner = :owner AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
        ),
        @NamedQuery(
            name = "Alert.findByOwner",
            query = "SELECT a FROM Alert a WHERE a.owner = :owner AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
        ),
        @NamedQuery(
            name = "Alert.findAll", query = "SELECT a FROM Alert a WHERE a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
        ),
        @NamedQuery(
            name = "Alert.findByStatus",
            query = "SELECT a FROM Alert a where a.enabled= :enabled AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
        ),
        @NamedQuery(
        	name = "Alert.findIDsByStatus",
        	query = "SELECT a.id FROM Alert a where a.enabled= :enabled AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
        ),
        @NamedQuery(
            name = "Alert.findByPrefix",
            query = "SELECT a FROM Alert a where a.name LIKE :name AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
        ), @NamedQuery(name = "Alert.setEnabled", query = "UPDATE Alert a SET a.enabled=true WHERE a = :alert"),
        @NamedQuery(name = "Alert.setDisabled", query = "UPDATE Alert a SET a.enabled=false WHERE a = :alert"),
        @NamedQuery(name = "Alert.countByStatus", query = "SELECT count(a) from Alert a where a.enabled= :enabled")
    }
)
public class Alert extends JPAEntity implements Serializable, CronJob {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(name = "name")
    private String name;
    private String expression;
    private String cronEntry;
    private boolean enabled = false;
    private boolean missingDataNotificationEnabled;
    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Notification> notifications = new ArrayList<>(0);
    @OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Trigger> triggers = new ArrayList<>(0);
    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private PrincipalUser owner;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Alert object.
     *
     * @param  creator     The creator of the alert.
     * @param  owner       The owner of the alert. Cannot be null.
     * @param  name        The name of the alert. Cannot be null or empty.
     * @param  expression  The expression to evaluate triggers against. Cannot be null or empty and must be a valid metric expression as defined by
     *                     the <tt>MetricService</tt>.
     * @param  cronEntry   The CRON schedule for the alert. Cannot be null or empty and must be valid CRON entry syntax.
     */
    public Alert(PrincipalUser creator, PrincipalUser owner, String name, String expression, String cronEntry) {
        super(creator);
        setOwner(owner);
        setName(name);
        setExpression(expression);
        setCronEntry(cronEntry);
        setEnabled(false);
        setMissingDataNotificationEnabled(false);
    }

    /** Creates a new Alert object. */
    protected Alert() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Enables or disables an alert.
     *
     * @param  em       The entity manager to use. Cannot be null.
     * @param  alert    The alert to use. Cannot be null.
     * @param  enabled  True if enabled, false if disabled.
     */
    public static void setEnabled(EntityManager em, Alert alert, boolean enabled) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(alert != null, "Alert cannot be null.");

        Query query = em.createNamedQuery(enabled ? "Alert.setEnabled" : "Alert.setDisabled");

        query.setParameter("alert", alert);
        query.executeUpdate();
    }

    /**
     * Finds an alert given its name and owner.
     *
     * @param   em         The entity manager to use. Cannot be null.
     * @param   alertName  The name of the alert. Cannot be null or empty.
     * @param   owner      The owner of the alert. Cannot be null.
     *
     * @return  The corresponding alert or null if no alert having the specified name exists for the owner.
     */
    public static Alert findByNameAndOwner(EntityManager em, String alertName, PrincipalUser owner) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(alertName != null && !alertName.isEmpty(), "Alert name cannot be null or empty.");
        requireArgument(owner != null, "Owner cannot be null.");

        TypedQuery<Alert> query = em.createNamedQuery("Alert.findByNameAndOwner", Alert.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("name", alertName);
            query.setParameter("owner", owner);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Finds all alerts for the given owner.
     *
     * @param   em     The entity manager to user. Cannot be null.
     * @param   owner  The owner to retrieve alerts for. Cannot be null.
     *
     * @return  The list of alerts for the owner. Will never be null but may be empty.
     */
    public static List<Alert> findByOwner(EntityManager em, PrincipalUser owner) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(owner != null, "Owner cannot be null.");

        TypedQuery<Alert> query = em.createNamedQuery("Alert.findByOwner", Alert.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("owner", owner);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Finds all alerts.
     *
     * @param   em  The entity manager to user. Cannot be null.
     *
     * @return  The list of all alerts. Will never be null but may be empty.
     */
    public static List<Alert> findAll(EntityManager em) {
        requireArgument(em != null, "Entity manager can not be null.");

        TypedQuery<Alert> query = em.createNamedQuery("Alert.findAll", Alert.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Finds alerts by status (enabled/disabled).
     *
     * @param   em       The entity manager to user. Cannot be null.
     * @param   enabled  Alert status (true for enabled jobs and false for disabled jobs).
     *
     * @return  The list of alerts for the given status. Will never be null but may be empty.
     */
    public static List<Alert> findByStatus(EntityManager em, boolean enabled) {
        requireArgument(em != null, "Entity manager can not be null.");

        TypedQuery<Alert> query = em.createNamedQuery("Alert.findByStatus", Alert.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("enabled", enabled);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }
    
    /**
     * Finds alert ids by status (enabled/disabled).
     *
     * @param   em       The entity manager to user. Cannot be null.
     * @param   enabled  Alert status (true for enabled jobs and false for disabled jobs).
     *
     * @return  The list of alert ids for the given status. Will never be null but may be empty.
     */
    public static List<BigInteger> findIDsByStatus(EntityManager em, boolean enabled) {
       requireArgument(em != null, "Entity manager can not be null.");

       TypedQuery<BigInteger> query = em.createNamedQuery("Alert.findIDByStatus", BigInteger.class);
       query.setHint("javax.persistence.cache.storeMode", "REFRESH");
       try {
           query.setParameter("enabled", enabled);
           return query.getResultList();
       } catch (NoResultException ex) {
    	   return new ArrayList<>(0);
       }
    }
    
    /**
     * Finds alert count by status (enabled/disabled).
     *
     * @param   em       The entity manager to user. Cannot be null.
     * @param   enabled  Alert status (true for enabled jobs and false for disabled jobs).
     *
     * @return   alert count by status
     */
    public static int alertCountByStatus(EntityManager em, boolean enabled) {
        requireArgument(em != null, "Entity manager can not be null.");

        TypedQuery<Long> query = em.createNamedQuery("Alert.countByStatus", Long.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("enabled", enabled);
            return query.getSingleResult().intValue();
        } catch (NoResultException ex) {
            return 0;
        }
    }
    
    /**
     * Finds alerts by status (enabled/disabled).
     *
     * @param   em       The entity manager to user. Cannot be null.
     * @param   enabled  Alert status (true for enabled jobs and false for disabled jobs).
     *
     * @return  The list of alerts for the given status. Will never be null but may be empty.
     */
    public static List<Alert> findByLimitOffsetStatus(EntityManager em, int limit, int offset, boolean enabled) {
        requireArgument(em != null, "Entity manager can not be null.");

        TypedQuery<Alert> query = em.createNamedQuery("Alert.findByStatus", Alert.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("enabled", enabled);
            query.setFirstResult(offset);
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Finds all alerts whose name starts with the given prefix.
     *
     * @param   em      The entity manager to use. Cannot be null.
     * @param   prefix  The name prefix to match. Cannot be null or empty.
     *
     * @return  The list of matching alerts. Will never be null, but may be empty.
     */
    public static List<Alert> findByPrefix(EntityManager em, String prefix) {
        requireArgument(em != null, "Entity Manager cannot be null");
        requireArgument(prefix != null && !prefix.isEmpty(), "Cannot find alerts starting with null of empty prefix");

        TypedQuery<Alert> query = em.createNamedQuery("Alert.findByPrefix", Alert.class);

        query.setHint("javax.persistence.cache.storeMode", "REFRESH");
        try {
            query.setParameter("name", prefix + "%");
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the CRON entry for the alert.
     *
     * @return  The CRON entry for the alert.
     */
    @Override
    public String getCronEntry() {
        return cronEntry;
    }

    /**
     * Sets the CRON entry for the alert.
     *
     * @param  cronEntry  The new CRON entry. Cannot be null and must be valid CRON entry syntax.
     */
    public void setCronEntry(String cronEntry) {
        requireArgument(Cron.isValid(cronEntry), "Invalid CRON entry." + cronEntry);
        this.cronEntry = cronEntry;
    }

    /**
     * Returns the owner of the alert.
     *
     * @return  The alert owner.
     */
    public PrincipalUser getOwner() {
        return owner;
    }

    /**
     * Sets the owner of the alert.
     *
     * @param  owner  The alert owner. Cannot be null.
     */
    public void setOwner(PrincipalUser owner) {
        requireArgument(owner != null, "Owner cannot be null.");
        this.owner = owner;
    }

    /**
     * Returns the name of the alert.
     *
     * @return  The alert name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the alert.
     *
     * @param  name  The alert name. Cannot be null or empty.
     */
    public void setName(String name) {
        requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
        this.name = name;
    }

    /**
     * Sets the expression which triggers will evaluate against.
     *
     * @return  The alert expression.
     */
    public String getExpression() {
        return expression;
    }

    /**
     * Sets the expression against which triggers will evaluate.
     *
     * @param  expression  The alert expression. Cannot be null and must be valid metric expression syntax as defined in the <tt>MetricService</tt>
     */
    public void setExpression(String expression) {
        requireArgument(MetricReader.isValid(expression), "Invalid metric expression " + expression);
        this.expression = expression;
    }

    /**
     * Indicates if the alert is enabled.
     *
     * @return  True if the alert is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Indicates whether or not the alert is enabled.
     *
     * @param  enabled  True if the alert is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Indicates if an alert should trigger if no data is available to evaluate.
     *
     * @return  True if the alert should trigger when no data is available.
     */
    public boolean isMissingDataNotificationEnabled() {
        return missingDataNotificationEnabled;
    }

    /**
     * Indicates whether or not an alert should trigger if no data is available to evaluate.
     *
     * @param  missingDataNotificationEnabled  True if the alert should trigger when no data is available.
     */
    public void setMissingDataNotificationEnabled(boolean missingDataNotificationEnabled) {
        this.missingDataNotificationEnabled = missingDataNotificationEnabled;
    }

    /**
     * Returns the list of triggers associated with the alert.
     *
     * @return  The alerts triggers. Will not be null, but may be empty.
     */
    public List<Trigger> getTriggers() {
        return Collections.unmodifiableList(triggers);
    }

    /**
     * Replaces the triggers for the alert.
     *
     * @param  triggers  The new set of triggers.
     */
    public void setTriggers(List<Trigger> triggers) {
        this.triggers.clear();
        if (triggers != null && !triggers.isEmpty()) {
            this.triggers.addAll(triggers);
        }
    }

    /**
     * Returns the list of notifications associated with the alert.
     *
     * @return  The alerts notifications. Will not be null, but may be empty.
     */
    public List<Notification> getNotifications() {
        return Collections.unmodifiableList(notifications);
    }

    /**
     * Replaces the list of notifications associated with the alert.
     *
     * @param  notifications  The new set of notifications.
     */
    public void setNotifications(List<Notification> notifications) {
        this.notifications.clear();
        if (notifications != null && !notifications.isEmpty()) {
            this.notifications.addAll(notifications);
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 31 * hash + Objects.hashCode(this.name);
        hash = 31 * hash + Objects.hashCode(this.owner);
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

        final Alert other = (Alert) obj;

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
        return "Alert{" + "name=" + name + ", expression=" + expression + ", cronEntry=" + cronEntry + ", enabled=" + enabled +
            ", missingDataNotificationEnabled=" + missingDataNotificationEnabled + ", notifications=" + notifications + ", triggers=" + triggers +
            ", owner=" + owner + '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
