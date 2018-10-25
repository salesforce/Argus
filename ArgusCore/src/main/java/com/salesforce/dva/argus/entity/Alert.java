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

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import javax.persistence.TemporalType;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.eclipse.persistence.config.HintValues;
import org.eclipse.persistence.config.QueryHints;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.salesforce.dva.argus.service.metric.MetricReader;

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
 * @author  Tom Valine (tvaline@salesforce.com)
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 * @author	Bhinav Sura (bhinav.sura@salesforce.com)
 * @author  Dongpu Jin (djin@salesforce.com)
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
					query = "SELECT a FROM Alert a where a.enabled= :enabled AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false and TYPE(jpa)= Alert) order by a.id asc"
					),
			@NamedQuery(
					name = "Alert.findByRangeAndStatus",
					query = "SELECT a FROM Alert a where a.id BETWEEN :fromId and :toId AND a.enabled= :enabled AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false and TYPE(jpa)= Alert) order by a.id asc"
					),			
			@NamedQuery(
					name = "Alert.findIDsByStatus",
					query = "SELECT a.id FROM Alert a where a.enabled= :enabled AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false and TYPE(jpa)= Alert) order by a.id asc"
					),
			@NamedQuery(
					name = "Alert.findAlertsModifiedAfterDate",
					query = "SELECT a FROM Alert a where a.id in (SELECT jpa.id from JPAEntity jpa where TYPE(jpa)= Alert and jpa.modifiedDate >= :modifiedDate) order by a.id asc"
					),
			@NamedQuery(
					name = "Alert.findByPrefix",
					query = "SELECT a FROM Alert a where a.name LIKE :name AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
					), @NamedQuery(name = "Alert.setEnabled", query = "UPDATE Alert a SET a.enabled=true WHERE a = :alert"),
			@NamedQuery(name = "Alert.setDisabled", query = "UPDATE Alert a SET a.enabled=false WHERE a = :alert"),
			@NamedQuery(name = "Alert.countByStatus", query = "SELECT count(a) from Alert a where a.enabled= :enabled"),
			@NamedQuery(
					name = "Alert.getSharedAlerts", 
					query = "SELECT a from Alert a where a.shared = true AND a.id not in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = true)"
					),
			@NamedQuery(
					name = "Alert.getSharedAlertsByOwner", 
					query = "SELECT a from Alert a where a.owner = :owner AND a.shared = true AND a.id not in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = true)"
					),
			// Count alert queries
			@NamedQuery(
					name = "Alert.countByOwner",
					query = "SELECT count(a) FROM Alert a WHERE a.owner = :owner AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
					),
			@NamedQuery(
					name = "Alert.countByOwnerWithSearchText",
					query = "SELECT count(a) FROM Alert a WHERE a.owner = :owner AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false) "
							+ "AND (FUNCTION('LOWER', a.name) LIKE :searchtext OR FUNCTION('LOWER', a.owner.userName) LIKE :searchtext)"
					),
			@NamedQuery(
					name = "Alert.countSharedAlerts",
					query = "SELECT count(a) from Alert a where a.shared = true AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
					),
			@NamedQuery(
					name = "Alert.countSharedAlertsWithSearchText",
					query = "SELECT count(a) FROM Alert a WHERE a.shared = true AND a.id IN (SELECT jpa.id FROM JPAEntity jpa WHERE jpa.deleted = false) "
							+ "AND (FUNCTION('LOWER', a.name) LIKE :searchtext OR FUNCTION('LOWER', a.owner.userName) LIKE :searchtext)"
					),
			@NamedQuery(
					name = "Alert.countPrivateAlertsForPrivilegedUser",
					query = "SELECT count(a) from Alert a where a.shared = false AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false)"
					),
			@NamedQuery(
					name = "Alert.countPrivateAlertsForPrivilegedUserWithSearchText",
					query = "SELECT count(a) from Alert a where a.shared = false AND a.id in (SELECT jpa.id from JPAEntity jpa where jpa.deleted = false) "
							+ "AND (FUNCTION('LOWER', a.name) LIKE :searchtext OR FUNCTION('LOWER', a.owner.userName) LIKE :searchtext)"
					),
		}
		)
public class Alert extends JPAEntity implements Serializable, CronJob {

	//~ Instance fields ******************************************************************************************************************************

	@Basic(optional = false)
	@Column(name = "name")
	@Metadata
	private String name;

	@Basic(optional = false)
	@Column(length = 2048)
	@Metadata
	private String expression;

	@Metadata
	@Basic(optional = false)
	private String cronEntry;

	@Metadata
	private boolean enabled = false;

	private boolean missingDataNotificationEnabled;

	@OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Notification> notifications = new ArrayList<>(0);

	@OneToMany(mappedBy = "alert", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Trigger> triggers = new ArrayList<>(0);

	@ManyToOne(optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	@Metadata
	private PrincipalUser owner;

	@Metadata
	private boolean shared;

	// Default values for page limit and page offset
	private static int DEFAULT_PAGE_LIMIT = 10;
	private static int DEFAULT_PAGE_OFFSET = 0;
	
	// WHERE condition key names used in criteria queries
	private static String DELETED_KEY = "deleted";
	private static String SHARED_KEY = "shared";
	private static String OWNER_KEY = "owner";
	private static String SEARCHTEXT_KEY = "searchtext";

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
		setShared(false); 
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
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
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
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
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
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		try {
			query.setParameter("owner", owner);
			return query.getResultList();
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	
	/**
	 * Count the number of alerts for the given owner.
	 *
	 * @param em
	 *            The entity manager to user. Cannot be null.
	 * @param owner
	 *            owner The owner to retrieve alerts for. Cannot be null.
	 * @param searchText
	 * 			  The text to filter the search results.
	 *
	 * @return The total number of alerts for the owner.
	 */
	public static int countByOwner(EntityManager em, PrincipalUser owner, String searchText) {
		requireArgument(em != null, "Entity manager can not be null.");
		requireArgument(owner != null, "Owner cannot be null.");
		
		if (searchText != null) {
			searchText.trim();
		}

		try {
			TypedQuery<Long> query = null;

			if (searchText == null || searchText.isEmpty()) {
				query = em.createNamedQuery("Alert.countByOwner", Long.class);
			} else {
				query = em.createNamedQuery("Alert.countByOwnerWithSearchText", Long.class);
				query.setParameter(SEARCHTEXT_KEY, _convertSearchTextWildCardForQuery(searchText));
			}

			query.setHint(QueryHints.REFRESH, HintValues.TRUE);
			query.setHint("javax.persistence.cache.storeMode", "REFRESH");
			query.setParameter(OWNER_KEY, owner);

			return query.getSingleResult().intValue();
		} catch (NoResultException ex) {
			return 0;
		}
	}

	/**
	 * Find alerts meta filtered by the owner.
	 * 
	 * @param em
	 *            The entity manager to user. Cannot be null.
	 * @param owner
	 *            The owner to retrieve alerts for. Cannot be null.
	 * @return A list of alerts owned by the owner.
	 */
	public static List<Alert> findByOwnerMeta(EntityManager em, PrincipalUser owner) {
		requireArgument(em != null, "Entity manager can not be null.");
		requireArgument(owner != null, "Owner can not be null.");

		try {
			Map<String, Object> whereParams = new HashMap<>();
			whereParams.put(DELETED_KEY, false);
			whereParams.put(OWNER_KEY, owner);

			// Get alerts meta
			return getAlertsMetaPaged(em, null, null, whereParams, null);
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	
	/**
	 * Finds all alerts' meta for the given owner with given limit and offset.
	 *
	 * @param em
	 *            The entity manager to user. Cannot be null.
	 * @param owner
	 *            The owner to retrieve alerts for. Cannot be null.
	 * @param limit
	 *            The limit of return to return.
	 * @param offset
	 *            The starting offset of the result.
	 * @param searchText
	 * 			  The text to filter the search results.
	 *
	 * @return The list of alerts for the owner.
	 */
	public static List<Alert> findByOwnerMetaPaged(EntityManager em, PrincipalUser owner, Integer limit,
			Integer offset, String searchText) {
		requireArgument(em != null, "Entity manager can not be null.");
		requireArgument(owner != null, "Owner cannot be null");
		
		if (searchText != null) {
			searchText.trim();
		}
		
		if (limit == null || limit <= 0) {
			limit = DEFAULT_PAGE_LIMIT;
		}
		if (offset == null || offset < 0) {
			offset = DEFAULT_PAGE_OFFSET;
		}

		try {
			Map<String, Object> whereParams = new HashMap<>();
			whereParams.put(DELETED_KEY, false);
			whereParams.put(OWNER_KEY, owner);

			// Get alerts meta
			return getAlertsMetaPaged(em, limit, offset, whereParams, searchText);
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
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		try {
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
	public static List<Alert> findAllMeta(EntityManager em) {
		requireArgument(em != null, "Entity manager can not be null.");

		try {
			Map<String, Object> whereParams = new HashMap<>();
			whereParams.put(DELETED_KEY, false);

			// Get alerts meta
			return getAlertsMetaPaged(em, null, null, whereParams, null);
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
		query.setHint("eclipselink.join-fetch", "a.triggers");
		query.setHint("eclipselink.join-fetch", "a.notifications");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.triggers");
		query.setHint("eclipselink.left-join-fetch", "a.triggers.notifications");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.metricsToAnnotate");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.subscriptions");
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
		
		try {
			query.setParameter("enabled", enabled);
			return query.getResultList();
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	

	public static List<Alert> findByRangeAndStatus(EntityManager em, BigInteger fromId, BigInteger toId, boolean enabled) {
		requireArgument(em != null, "Entity manager cannot be null.");
		requireArgument(fromId != null, "fromId cannot be null.");
		requireArgument(toId != null, "toId cannot be null.");
		
		TypedQuery<Alert> query = em.createNamedQuery("Alert.findByRangeAndStatus", Alert.class);

		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		query.setHint("eclipselink.join-fetch", "a.triggers");
		query.setHint("eclipselink.join-fetch", "a.notifications");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.triggers");
		query.setHint("eclipselink.left-join-fetch", "a.triggers.notifications");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.metricsToAnnotate");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.subscriptions");
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
		
		try {
			query.setParameter("fromId", fromId);
			query.setParameter("toId", toId);
			query.setParameter("enabled", enabled);
			return query.getResultList();
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	
	public static List<Alert> findAlertsModifiedAfterDate(EntityManager em, Date modifiedDate) {
		requireArgument(em != null, "Entity manager cannot be null.");
		requireArgument(modifiedDate != null, "modifiedDate cannot be null.");
		
		TypedQuery<Alert> query = em.createNamedQuery("Alert.findAlertsModifiedAfterDate", Alert.class);

		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		query.setHint("eclipselink.join-fetch", "a.triggers");
		query.setHint("eclipselink.join-fetch", "a.notifications");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.triggers");
		query.setHint("eclipselink.left-join-fetch", "a.triggers.notifications");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.metricsToAnnotate");
		query.setHint("eclipselink.left-join-fetch", "a.notifications.subscriptions");
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
		
		try {
			query.setParameter("modifiedDate",modifiedDate,TemporalType.TIMESTAMP);
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

		TypedQuery<BigInteger> query = em.createNamedQuery("Alert.findIDsByStatus", BigInteger.class);
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
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
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
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
	 * @param limit      The results limit
	 * @param offset     The results offset
	 * @param   enabled  Alert status (true for enabled jobs and false for disabled jobs).
	 *
	 * @return  The list of alerts for the given status. Will never be null but may be empty.
	 */
	public static List<Alert> findByLimitOffsetStatus(EntityManager em, int limit, int offset, boolean enabled) {
		requireArgument(em != null, "Entity manager can not be null.");

		TypedQuery<Alert> query = em.createNamedQuery("Alert.findByStatus", Alert.class);
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
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
	 * Finds all shared alerts with filtering.
	 *
	 * @param   em     The entity manager to user. Cannot be null.
	 * @param   owner  The owner of shared alerts to filter on 
	 * @param   limit  The maximum number of rows to return.
	 *
	 * @return  The list of all shared alerts. Will never be null but may be empty.
	 */
	public static List<Alert> findSharedAlerts(EntityManager em, PrincipalUser owner, Integer limit) {
		requireArgument(em != null, "Entity manager can not be null.");

		TypedQuery<Alert> query;
		if(owner == null){
			query = em.createNamedQuery("Alert.getSharedAlerts", Alert.class);
		} else {
			query = em.createNamedQuery("Alert.getSharedAlertsByOwner", Alert.class);
			query.setParameter("owner", owner);
		}
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
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
	 * Count the total number of all shared alerts.
	 *
	 * @param em
	 *            The entity manager to user. Cannot be null.
	 * @param searchText
	 * 			  The text to filter the search results.
	 *
	 * @return The count of all shared alerts.
	 */
	public static int countSharedAlerts(EntityManager em, String searchText) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		if (searchText != null) {
			searchText.trim();
		}

		try {
			TypedQuery<Long> query = null;

			if (searchText == null || searchText.isEmpty()) {
				query = em.createNamedQuery("Alert.countSharedAlerts", Long.class);
			} else {
				query = em.createNamedQuery("Alert.countSharedAlertsWithSearchText", Long.class);
				query.setParameter(SEARCHTEXT_KEY, _convertSearchTextWildCardForQuery(searchText));
			}

			query.setHint(QueryHints.REFRESH, HintValues.TRUE);
			query.setHint("javax.persistence.cache.storeMode", "REFRESH");

			return query.getSingleResult().intValue();
		} catch (NoResultException ex) {
			return 0;
		}
	}

	/**
	 * Gets all meta information of shared alerts with filtering.
	 *
	 * @param   em     The entity manager to user. Cannot be null.
	 * @param   owner  The owner to filter on 
	 * @param   limit  The maximum number of rows to return.
	 *
	 * @return  The list of all shared alerts with meta information only. Will never be null but may be empty.
	 */	
	public static List<Alert> findSharedAlertsMeta(EntityManager em, PrincipalUser owner, Integer limit) {
		requireArgument(em != null, "Entity manager can not be null.");

		try {
			Map<String, Object> whereParams = new HashMap<>();
			whereParams.put(DELETED_KEY, false);
			whereParams.put(SHARED_KEY, true);
			
			if (owner != null) {
				whereParams.put(OWNER_KEY, owner);
			}

			// Get alerts meta
			return getAlertsMetaPaged(em, limit, null, whereParams, null);
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	
	/**
	 * Find all shared alerts meta with given limit and offset.
	 * 
	 * @param em
	 *            The entity manager to user. Cannot be null.
	 * @param limit
	 *            The maximum number of rows to return.
	 * @param offset
	 *            The starting offset of the result.
	 * @param searchText
	 * 			  The text to filter the search results.
	 * 
	 * @return The list of shared alerts with given limit and offset.
	 */
	public static List<Alert> findSharedAlertsMetaPaged(EntityManager em, Integer limit, Integer offset, String searchText) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		if (searchText != null) {
			searchText.trim();
		}
		
		if (limit == null || limit <= 0) {
			limit = DEFAULT_PAGE_LIMIT;
		}
		if (offset == null || offset < 0) {
			offset = DEFAULT_PAGE_OFFSET;
		}

		try {
			// Get shared alerts
			Map<String, Object> whereParams = new HashMap<>();
			whereParams.put(DELETED_KEY, false);
			whereParams.put(SHARED_KEY, true);

			// Get alerts meta
			return getAlertsMetaPaged(em, limit, offset, whereParams, searchText);
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	
	/**
	 * Find all private alerts (non-shared alerts) meta for given privileged user with given limit and offset.
	 *
	 * @param   em     The entity manager to user. Cannot be null.
	 * @param   owner  The owner to filter on 
	 * @param   limit  The maximum number of rows to return.
	 * @param 	offset The starting offset of the result.
	 * @param searchText
	 * 			  The text to filter the search results.
	 *
	 * @return The list of private alerts' meta with given limit and offset.
	 */
	public static List<Alert> findPrivateAlertsForPrivilegedUserMetaPaged(EntityManager em, PrincipalUser owner, Integer limit, Integer offset, String searchText) {
		requireArgument(em != null, "Entity manager can not be null.");
		
		if (searchText != null) {
			searchText.trim();
		}
		
		if (limit == null || limit <= 0) {
			limit = DEFAULT_PAGE_LIMIT;
		}
		if (offset == null || offset < 0) {
			offset = DEFAULT_PAGE_OFFSET;
		}
		
		// Invalid user nor non-privileged user shall not view other's non-shared alerts, thus immediately return empty list
		if (owner == null || !owner.isPrivileged()) {
			return new ArrayList<>(0); 
		}

		try {
			// Query for alerts that are not marked as deleted, non-shared, owned by others
			Map<String, Object> whereParams = new HashMap<>();
			whereParams.put(DELETED_KEY, false);
			whereParams.put(SHARED_KEY, false);

			// Get alerts meta
			return getAlertsMetaPaged(em, limit, offset, whereParams, searchText);
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}
	
	/**
	 * Count the total number of private alerts (non-shared alerts) for
	 * privileged user.
	 *
	 * @param em
	 *            The entity manager to user. Cannot be null.
	 * @param owner
	 *            The owner to filter on.
	 * @param searchText
	 * 			  The text to filter the search results.
	 * 
	 * @return The total number of private alerts for privileged user.
	 */
	public static int countPrivateAlertsForPrivilegedUser(EntityManager em, PrincipalUser owner, String searchText) {
		requireArgument(em != null, "Entity manager can not be null.");
		requireArgument(owner != null, "Owner cannot be null.");
		
		if (searchText != null) {
			searchText.trim();
		}

		if (!owner.isPrivileged()) {
			return 0;
		}
		
		try {
			TypedQuery<Long> query = null;

			if (searchText == null || searchText.isEmpty()) {
				query = em.createNamedQuery("Alert.countPrivateAlertsForPrivilegedUser", Long.class);
			} else {
				query = em.createNamedQuery("Alert.countPrivateAlertsForPrivilegedUserWithSearchText", Long.class);
				query.setParameter(SEARCHTEXT_KEY, _convertSearchTextWildCardForQuery(searchText));
			}

			query.setHint(QueryHints.REFRESH, HintValues.TRUE);
			query.setHint("javax.persistence.cache.storeMode", "REFRESH");

			return query.getSingleResult().intValue();
		} catch (NoResultException ex) {
			return 0;
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
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		try {
			query.setParameter("name", prefix + "%");
			return query.getResultList();
		} catch (NoResultException ex) {
			return new ArrayList<>(0);
		}
	}

	//~ Methods **************************************************************************************************************************************
	
	/*
	 * A helper method executes the criteria query to get alerts meta with given
	 * limit and offset.
	 */
	private static List<Alert> getAlertsMetaPaged(EntityManager em, Integer limit, Integer offset,
			Map<String, Object> whereParams, String searchText) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> cq = cb.createTupleQuery();
		Root<Alert> e = cq.from(Alert.class);

		// Set fields to select
		List<Selection<?>> fieldsToSelect = new ArrayList<>();
		for (Field field : FieldUtils.getFieldsListWithAnnotation(Alert.class, Metadata.class)) {
			fieldsToSelect.add(e.get(field.getName()).alias(field.getName()));
		}
		cq.multiselect(fieldsToSelect);

		List<Predicate> predicates = new ArrayList<>();
		
		// Set WHERE conditions, so far we only use boolean and PrincipalUser
		// type conditions. New types can be easily added here on demand.
		if (whereParams != null && whereParams.size() > 0) {
			for (String key : whereParams.keySet()) {
				Object value = whereParams.get(key);
				if (value instanceof Boolean) {
					// Boolean type condition
					boolean val = (boolean) value;
					predicates.add(cb.equal(e.get(key), val));
				} else if (value instanceof PrincipalUser) {
					// PrincipalUser type condition
					predicates.add(cb.equal(e.get(key), (PrincipalUser) value));
				}
			}
		}
		
		// Filter on alert name and owner name if not empty. All values are
		// normalized to lower case for case insensitive search.
		if (searchText != null && !searchText.isEmpty()) {
			String searchPattern = _convertSearchTextWildCardForQuery(searchText);
			Expression<String> alertName = e.get("name");
			Expression<String> ownerName = e.join("owner").get("userName");
			predicates.add(cb.or(cb.like(cb.function("LOWER", String.class, alertName), searchPattern),
					cb.like(cb.function("LOWER", String.class, ownerName), searchPattern)));
		}
		
		if (predicates.size() > 0) {
			cq.where(predicates.toArray(new Predicate[predicates.size()]));
		}

		// Sort result by alert id
		cq.orderBy(cb.asc(e.get("id")));

		TypedQuery<Tuple> query = em.createQuery(cq);
		query.setHint("javax.persistence.cache.storeMode", "REFRESH");
		query.setHint(QueryHints.REFRESH, HintValues.TRUE);

		// Set limit for pagination
		if (limit != null && limit > 0) {
			query.setMaxResults(limit);
		}

		// Set offset for pagination
		if (offset != null && offset >= 0) {
			query.setFirstResult(offset);
		}

		List<Tuple> result = query.getResultList();
		List<Alert> alerts = new ArrayList<>();

		for (Tuple tuple : result) {

			Alert a = new Alert(PrincipalUser.class.cast(tuple.get("createdBy")),
					PrincipalUser.class.cast(tuple.get("owner")), String.class.cast(tuple.get("name")),
					String.class.cast(tuple.get("expression")), String.class.cast(tuple.get("cronEntry")));

			a.id = BigInteger.class.cast(tuple.get("id"));
			a.enabled = Boolean.class.cast(tuple.get("enabled"));
			a.createdDate = Date.class.cast(tuple.get("createdDate"));
			a.modifiedDate = Date.class.cast(tuple.get("modifiedDate"));
			a.shared = Boolean.class.cast(tuple.get("shared"));
			a.modifiedBy = PrincipalUser.class.cast(tuple.get("modifiedBy"));

			alerts.add(a);
		}
		
		// Trim excessive items more then limit in the end
		if (limit != null && limit > 0) {
			alerts = alerts.subList(0, Math.min(alerts.size(), limit));
		}

		return alerts;
	}

	private static String _convertSearchTextWildCardForQuery(String searchText) {
		return "%" + searchText.toLowerCase().replace("*", "%").replace("?","_") + "%";
	}
	
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
	
	/**
	 * Adds a notification to the list of notifications associated with the alert.
	 * @param notification	The notification to add.
	 */
	public void addNotification(Notification notification) {
		if(notification != null) {
			this.notifications.add(notification);
		}
	}

	/**
	 * Indicates if the alert is shared.
	 *
	 * @return  True if the alert is shared.
	 */

	public boolean isShared() {
		return shared;
	}

	/**
	 * Indicates whether or not the alert is shared.
	 *
	 * @param  shared  True if the alert is shared.
	 **/
	public void setShared(boolean shared) {
		this.shared = shared;
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
				", owner=" + owner + ", shared=" + shared + '}';
	}

	public static class Serializer extends JsonSerializer<Alert> {

		@Override
		public void serialize(Alert alert, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {

			jgen.writeStartObject();

			jgen.writeStringField("id", alert.getId().toString());
			jgen.writeStringField("name", alert.getName());
			jgen.writeStringField("expression", alert.getExpression());
			jgen.writeStringField("cronEntry", alert.getCronEntry());
			jgen.writeBooleanField("enabled", alert.isEnabled());
			jgen.writeBooleanField("missingDataNotificationEnabled", alert.isMissingDataNotificationEnabled());
			jgen.writeObjectField("owner", alert.getOwner());

			jgen.writeArrayFieldStart("triggers");
			for(Trigger trigger : alert.getTriggers()) {
				jgen.writeObject(trigger);
			}
			jgen.writeEndArray();

			jgen.writeArrayFieldStart("notifications");
			for(Notification notification : alert.getNotifications()) {
				jgen.writeObject(notification);
			}
			jgen.writeEndArray();

			jgen.writeEndObject();
		}
	}

	public static class Deserializer extends JsonDeserializer<Alert> {

		@Override
		public Alert deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

			SimpleModule module = new SimpleModule();
			module.addDeserializer(Trigger.class, new Trigger.Deserializer());
			module.addDeserializer(Notification.class, new Notification.Deserializer());
			module.addDeserializer(PrincipalUser.class, new Alert.PrincipalUserDeserializer());

			ObjectMapper mapper = new ObjectMapper();
			mapper.registerModule(module);

			Alert alert = new Alert();
			JsonNode rootNode = jp.getCodec().readTree(jp);

			BigInteger id = new BigInteger(rootNode.get("id").asText());
			alert.id = id;

			String name = rootNode.get("name").asText();
			alert.setName(name);

			String expression = rootNode.get("expression").asText();
			alert.setExpression(expression);

			String cronEntry = rootNode.get("cronEntry").asText();
			alert.setCronEntry(cronEntry);

			boolean enabled = rootNode.get("enabled").asBoolean();
			alert.setEnabled(enabled);

			boolean missingDataNotificationEnabled = rootNode.get("missingDataNotificationEnabled").asBoolean();
			alert.setMissingDataNotificationEnabled(missingDataNotificationEnabled);

			JsonNode onwerNode = rootNode.get("owner");
			PrincipalUser owner = mapper.treeToValue(onwerNode, PrincipalUser.class);
			alert.setOwner(owner);

			List<Trigger> triggers = new ArrayList<>();
			JsonNode triggersArrayNode = rootNode.get("triggers");
			if(triggersArrayNode.isArray()) {
				for(JsonNode triggerNode : triggersArrayNode) {
					Trigger trigger = mapper.treeToValue(triggerNode, Trigger.class);
					trigger.setAlert(alert);
					triggers.add(trigger);
				}
			}
			alert.setTriggers(triggers);

			List<Notification> notifications = new ArrayList<>();
			JsonNode notificationsArrayNode = rootNode.get("notifications");
			if(notificationsArrayNode.isArray()) {
				for(JsonNode notificationNode : notificationsArrayNode) {
					Notification notification  = mapper.treeToValue(notificationNode, Notification.class);
					notification.setAlert(alert);
					_replaceTriggerObjectsContainingOnlyIDsWithActualObjects(notification, notification.getTriggers(), triggers);
					notifications.add(notification);
				}
			}
			alert.setNotifications(notifications);

			return alert;
		}

		private void _replaceTriggerObjectsContainingOnlyIDsWithActualObjects(Notification notification, List<Trigger> triggersWithIDsOnly, 
				List<Trigger> triggers) {

			List<Trigger> triggersToAdd = new ArrayList<>(triggersWithIDsOnly.size());
			for(Trigger trigger : triggers) {
				if(_contains(trigger.getId(), triggersWithIDsOnly)) {
					triggersToAdd.add(trigger);
				}
			}

			notification.setTriggers(triggersToAdd);

		}

		private boolean _contains(BigInteger triggerID, List<Trigger> triggersWithIDsOnly) {

			for(Trigger trigger : triggersWithIDsOnly) {
				if(trigger.getId().equals(triggerID)) {
					return true;
				}
			}

			return false;
		}

	}

	public static class PrincipalUserSerializer extends JsonSerializer<PrincipalUser> {

		@Override
		public void serialize(PrincipalUser value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
			jgen.writeStartObject();
			jgen.writeStringField("id", value.getId().toString());
			jgen.writeStringField("username", value.getUserName());
			jgen.writeStringField("email", value.getEmail());
			jgen.writeEndObject();
		}
	}

	public static class PrincipalUserDeserializer extends JsonDeserializer<PrincipalUser> {

		@Override
		public PrincipalUser deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {

			PrincipalUser user = new PrincipalUser();
			JsonNode rootNode = jp.getCodec().readTree(jp);

			BigInteger id = new BigInteger(rootNode.get("id").asText());
			user.id = id;

			String username = rootNode.get("username").asText();
			user.setUserName(username);

			String email = rootNode.get("email").asText();
			user.setEmail(email);

			return user;
		}

	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */