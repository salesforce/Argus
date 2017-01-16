package com.salesforce.dva.argus.service.warden;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Infraction;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Policy;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.SuspensionLevel;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.warden.dto.Policy.TriggerType;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WaaSMonitorService;
//import com.salesforce.dva.argus.service.WaaSMonitorService;
import com.salesforce.dva.argus.service.WaaSService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

public class DefaultWaaSService extends DefaultJPAService implements WaaSService {

	// ~ Static fields/initializers
	// *******************************************************************************************************************

	private static final String WAAS_ALERT_NAME_PREFIX = "waas-";
	private static final String WAAS_NOTIFICATION_NAME = "WaaS Notification";
	private static final String WAAS_ANNOTATION_SOURCE = "ARGUS-WaaS";
	private static final String WAAS_ANNOTATION_TYPE = "WaaS";

	// ~ Instance fields
	// ******************************************************************************************************************************

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	@Inject
	private Provider<EntityManager> emf;
	private final AlertService _alertService;
	private final WaaSMonitorService _waaSMonitorService;
	private final UserService _userService;
	private final MetricService _metricService;
	private final ServiceManagementService _serviceManagementRecordService;
	private final DashboardService _dashboardService;
	private final AnnotationService _annotationService;
	private final TSDBService _tsdbService;
	private final PrincipalUser _adminUser;

	// ~ Constructors
	// *********************************************************************************************************************************

	/**
	 * Creates a new DefaultWardenService object.
	 *
	 * @param alertService
	 *            The alert service user to create warden alerts when necessary.
	 *            Cannot be null.
	 * @param waaSMonitorService
	 *            The WaaaS monitor service used to collect warden policy metric
	 *            policys. Cannot be null.
	 * @param userService
	 *            The user service. Cannot be null.
	 * @param metricService
	 *            The metric service. Cannot be null.
	 * @param serviceManagementService
	 *            The service management service. Cannot be null.
	 * @param dashboardService
	 *            The dashboard service. Cannot be null.
	 * @param auditService
	 *            The audit service. Cannot be null.
	 * @param annotationService
	 *            The annotation service. Cannot be null.
	 * @param tsdbService
	 *            The tsdb service. Cannot be null.
	 * @param _sysConfig
	 *            Service properties
	 */
	@Inject
	protected DefaultWaaSService(AlertService alertService, WaaSMonitorService waaSMonitorService,
			UserService userService, MetricService metricService, ServiceManagementService serviceManagementService,
			DashboardService dashboardService, AuditService auditService, AnnotationService annotationService,
			TSDBService tsdbService, SystemConfiguration _sysConfig) {
		super(auditService, _sysConfig);
		requireArgument(alertService != null, "Alert service cannot be null.");
		requireArgument(waaSMonitorService != null, "Monitor service cannot be null.");
		requireArgument(userService != null, "User service cannot be null.");
		requireArgument(metricService != null, "Metric service cannot be null.");
		requireArgument(serviceManagementService != null, "Service management service cannot be null.");
		requireArgument(dashboardService != null, "Dashboard service cannot be null.");
		requireArgument(annotationService != null, "Annotation service cannot be null.");
		requireArgument(tsdbService != null, "TSDB service cannot be null.");
		_alertService = alertService;
		_waaSMonitorService = waaSMonitorService;
		_userService = userService;
		_metricService = metricService;
		_serviceManagementRecordService = serviceManagementService;
		_dashboardService = dashboardService;
		_annotationService = annotationService;
		_tsdbService = tsdbService;
		_adminUser = _userService.findAdminUser();
                /* todo: move this out of the constructor */
//		_waaSMonitorService.startPushingMetrics();
	}

	// ~ Methods
	// **************************************************************************************************************************************

	@Override
	public void dispose() {
		super.dispose();
		_alertService.dispose();
		_waaSMonitorService.dispose();
		_userService.dispose();
		_metricService.dispose();
		_serviceManagementRecordService.dispose();
		_dashboardService.dispose();
	}

	@Override
	@Transactional
	public Policy updatePolicy(String user, Policy policy, List<Double> values) {
		requireNotDisposed();
		requireArgument(user != null, "Cannot update policy without user.");
		requireArgument(policy != null, "Cannot update policy without a policy.");

		EntityManager em = emf.get();
		Policy p = Policy.findByNameAndService(em, policy.getName(), policy.getService());

		if (p == null) {
			p = policy;
		}

		if (values.size() > 0) {
			p.setThreshold(values);
		}

		Policy result = mergeEntity(em, p);
		em.flush();

		_updateWardenAlertsForUser(user, policy);

		return result;
	}

	@Transactional
	public Policy _updatePolicyAfterUpsertSuspensionLevel(String user, Policy policy, List<SuspensionLevel> levels) {
		requireNotDisposed();
		requireArgument(user != null, "Cannot update policy without user.");
		requireArgument(policy != null, "Cannot update policy without a policy.");
		requireArgument(levels != null && levels.size() != 0, "Cannot update policy without levels.");

		EntityManager em = emf.get();
		Policy p = Policy.findByNameAndService(em, policy.getName(), policy.getService());

		if (p == null) {
			p = policy;
		}

		// update policy after upsert new suspensionLevel

		p.setSuspensionLevels(levels);

		Policy result = mergeEntity(em, p);
		em.flush();

		return result;
	}

	@Override
	@Transactional
	public Policy updateSuspensionLevel(Policy policy, int level, int infractionCount, long suspensionTime) {
		requireNotDisposed();
		requireArgument(policy != null, "Cannot update suspension level without policy.");
		requireArgument(level > 0, "Level must be greater than zero.");
		requireArgument(infractionCount > 0, "Infraction count must be greater than zero.");
		requireArgument(suspensionTime > 0L, "Suspension time must be greater than zero.");

		EntityManager em = emf.get();

		Policy queryPolicy = Policy.findByNameAndService(em, policy.getName(), policy.getService());
		boolean suspensionLevelFound = false;

		for (SuspensionLevel suspensionLevel : queryPolicy.getSuspensionLevels()) {
			if (suspensionLevel.getLevelNumber() == level) {
				suspensionLevel.setInfractionCount(infractionCount);
				suspensionLevel.setSuspensionTime(suspensionTime);
				suspensionLevelFound = true;
				break;
			}

		}
		if (!suspensionLevelFound) {
			PrincipalUser creator = _userService.findUserByUsername(policy.getOwners().get(0));
			SuspensionLevel newSuspensionLevel = new SuspensionLevel(creator, policy, level, infractionCount,
					suspensionTime);
			queryPolicy.getSuspensionLevels().add(newSuspensionLevel);
		}

		// update policy
		Policy result = mergeEntity(em, queryPolicy);
		em.flush();

		return result;
	}

	@Override
	public boolean isSuspended(String user, Policy policy) {

		requireNotDisposed();

		requireArgument(user != null, "User cannot be null while checking for user suspension.");
		requireArgument(policy != null, "Policy cannot be null while checking for user suspension.");

		EntityManager em = emf.get();

		List<Infraction> infractionList = Infraction.findByPolicyAndUserName(em, policy, user);

		// no infraction happens for this policy-user combination
		if (infractionList == null || infractionList.isEmpty()) {
			return false;
		}

		Comparator<Infraction> byInfractionTime = (i1, i2) -> i1.getInfractionTimestamp()
				.compareTo(i2.getInfractionTimestamp());
		Optional<Infraction> latestInfraction = infractionList.stream().sorted(byInfractionTime.reversed()).findFirst();
		return latestInfraction.get().isSuspended();
	}

	@Override
	@Transactional
	public void reinstateUser(String user, Policy policy) {

		requireNotDisposed();

		requireArgument(user != null, "User cannot be null while checking for user suspension.");
		requireArgument(policy != null, "Policy cannot be null while checking for user suspension.");

		EntityManager em = emf.get();

		List<Infraction> infractionList = Infraction.findByPolicyAndUserName(em, policy, user);

		// if reinstate is needed, delete all the infraction history
		if (_validReinstatable(infractionList)) {
			for (Infraction infraction : infractionList) {
				deleteEntity(em, infraction);
				em.flush();
			}

			// create annotation for reinstate
			// _createReinstateAnnotation(user, policy);
		}
	}
	//for future impl need, right now blocked by tsdbservice local test
	private void _createReinstateAnnotation(String user, Policy policy) {
		// for the scope => policy.getService(), is this correct?
		Annotation annotation = new Annotation(WAAS_ANNOTATION_SOURCE, user, WAAS_ANNOTATION_TYPE, policy.getService(),
				policy.getMetricName(), System.currentTimeMillis());
		Map<String, String> fields = new TreeMap<>();

		fields.put("Reinstated policy", policy.getName().toString());
		fields.put("Reinstated service", policy.getService().toString());
		fields.put("Reinstated metric", policy.getMetricName().toString());
		fields.put("Reinstated user", policy.getUsers().get(0).toString());

		annotation.setFields(fields);
		_annotationService.updateAnnotation(_userService.findUserByUsername(user), annotation);

	}

	private boolean _validReinstatable(List<Infraction> infractionList) {
		// no infraction happens for this policy-user combination
		if (infractionList == null || infractionList.isEmpty())
			return false;

		// check if the user is suspended indefinitely
		Comparator<Infraction> byInfractionTime = (i1, i2) -> i1.getInfractionTimestamp()
				.compareTo(i2.getInfractionTimestamp());
		Optional<Infraction> latestInfraction = infractionList.stream().sorted(byInfractionTime.reversed()).findFirst();
		if (!latestInfraction.isPresent() || latestInfraction.get().getExpirationTimestamp() != -1L)
			return false;

		return true;
	}

	@Override
	@Transactional
	public Infraction suspendUser(String user, Policy policy) {

		requireNotDisposed();

		requireArgument(user != null, "User cannot be null while checking for user suspension.");
		requireArgument(policy != null, "Policy cannot be null while checking for user suspension.");

		if (_userService.findUserByUsername(user).isPrivileged()) {
			throw new IllegalArgumentException("Admin user cannot be suspended!");
		}

		EntityManager em = emf.get();
		Long infractionTime = System.currentTimeMillis();
		Infraction infraction = new Infraction(_adminUser, policy, _userService.findUserByUsername(user),
				infractionTime, 0L);

		Long expirationTime = _calculateExpirationTime(user, policy, infractionTime);
		infraction.setExpirationTimestamp(expirationTime);

		Infraction mergedInfraction = mergeEntity(em, infraction);

		em.flush();
		return mergedInfraction;
	}

	private Long _calculateExpirationTime(String user, Policy policy, Long infractionTimestamp) {

		List<SuspensionLevel> suspensionLevels = policy.getSuspensionLevels();

		// if suspensionLevels is not defined, just set default value;
		if (suspensionLevels.isEmpty() || suspensionLevels == null)
			return 0L;

		// parse policy time unit
		Long timeunitInMillis = _parsePolicyTimeUnit(policy.getTimeUnit());

		// retrieve all infraction records
		EntityManager em = emf.get();
		List<Infraction> infractionList = Infraction.findByPolicyAndUserName(em, policy, user);

		return _calculateBasedOnCountAndLevel(suspensionLevels, infractionList, infractionTimestamp, timeunitInMillis);
	}

	private Long _calculateBasedOnCountAndLevel(List<SuspensionLevel> suspensionLevels, List<Infraction> infractionList,
			Long infractionTimestamp, Long timeunitInMillis) {

		// create a map <count, suspensionTimestamp> for looking up suspension
		// period
		Map<Integer, Long> levelMap = suspensionLevels.stream()
				.collect(Collectors.toMap(SuspensionLevel::getInfractionCount, SuspensionLevel::getSuspensionTime));

		// find out the count of infraction in the latest timeunit
		// ***Note***
		// Here, timeunit window includes past timestamp excludes start
		// timestamp,[past ts, current ts)
		// in this way if we have mutiple infractions happen at start timestamp,
		// the same logical guaranteed
		long latestCount = infractionList.stream()
				.sorted((i1, i2) -> i1.getInfractionTimestamp().compareTo(i2.getInfractionTimestamp()))
				.filter(i -> (i.getInfractionTimestamp() >= infractionTimestamp - timeunitInMillis)
						&& (i.getInfractionTimestamp() < infractionTimestamp))
				.count();

		// compare the latest count with the count in suspenion levels
		// if the updated count is bigger than the biggest level number, return
		// -1
		// if the updated count is smaller than the smallest level number ,
		// return 0
		// otherwise find out the first biggest count smaller than(or equal to)
		// updated count, and return the timestamp
		Comparator<Entry<Integer, Long>> byCount = (entry1, entry2) -> entry1.getKey().compareTo(entry2.getKey());
		Stream<Entry<Integer, Long>> levelStream = levelMap.entrySet().stream();

		if (levelMap.entrySet().stream().max(byCount).get().getKey() < latestCount + 1)
			return -1L;
		if (levelMap.entrySet().stream().min(byCount).get().getKey() > latestCount + 1)
			return 0L;

		Optional<Entry<Integer, Long>> val = levelStream.sorted(byCount.reversed())
				.filter(entry -> entry.getKey() <= latestCount + 1).findFirst();

		return val.isPresent() ? val.get().getValue() + infractionTimestamp : 0L;
	}

	private Long _parsePolicyTimeUnit(String policyTimeUnit) {
		try {
			TimeUnit unitValue = TimeUnit.fromString(policyTimeUnit.substring(policyTimeUnit.length() - 1));

			long digitValue = Long.parseLong(policyTimeUnit.substring(0, policyTimeUnit.length() - 1));

			return digitValue * unitValue.getValue() / 1000;
		} catch (Exception t) {
			return Long.parseLong(policyTimeUnit);
		}
	}

	private void _updateWardenAlertsForUser(String user, Policy policy) {
		/* Enable alert for this policy or create one if it doesn't exist. */
		Alert wardenAlert = _alertService.findAlertByNameAndOwner(_constructWardenAlertName(user, policy), _adminUser);

		if (wardenAlert == null) {
			wardenAlert = _constructWardenAlertForUser(user, policy);
		} else {
			Trigger trigger = wardenAlert.getTriggers().get(0);

			trigger.setThreshold(policy.getThreshold().get(0));
			if (policy.getThreshold().size() == 2 && policy.getThreshold().get(1) != null) {
				trigger.setSecondaryThreshold(policy.getThreshold().get(1));
			}
		}
		wardenAlert.setEnabled(true);
		_alertService.updateAlert(wardenAlert);
	}

	/**
	 * Create a warden alert which will annotate the corresponding warden metric
	 * with suspension events.
	 *
	 * @param user
	 *            The user for which the notification should be created. Cannot
	 *            be null.
	 * @param policy
	 *            The policy policy for which the notification should be
	 *            created. Cannot be null.
	 *
	 * @return The warden alert.
	 */
	private Alert _constructWardenAlertForUser(String user, Policy policy) {
		String alertName = _constructWardenAlertName(user, policy);
		Alert alert = new Alert(_adminUser, _adminUser, alertName, _constructMetricExpression(user, policy),
				policy.getCronEntry());
		List<Trigger> triggers = new ArrayList<>();

		double threshold = policy.getThreshold().get(0);

		Trigger trigger = new Trigger(alert, Trigger.TriggerType.fromString(policy.getTriggerType().value()),
				"policy-value-" + policy.getTriggerType().toString() + "-policy-threshold", threshold, 0.0, 0L);

		triggers.add(trigger);

		List<Notification> notifications = new ArrayList<>();
		Notification notification = new Notification(WAAS_NOTIFICATION_NAME, alert, WaaSNotifier.class.getName(),
				new ArrayList<String>(), 3600000);
		List<String> metricAnnotationList = new ArrayList<String>();

		String metricExpression = _constructMetricExpression(user, policy);

		String wardenMetricAnnotation = metricExpression.substring(metricExpression.indexOf(':') + 1);
		metricAnnotationList.add(wardenMetricAnnotation);
		notification.setMetricsToAnnotate(metricAnnotationList);
		notification.setTriggers(triggers);
		notifications.add(notification);
		alert.setTriggers(triggers);
		alert.setNotifications(notifications);
		return alert;
	}

	private String _constructMetricExpression(String user, Policy policy) {

		Object[] params = { policy.getTimeUnit(), policy.getMetricName(), user,
				policy.getAggregator().getDescription() };
		String format = "-{0}:{1}'{'user={2}'}':{3}";
		return MessageFormat.format(format, params);
	}

	private static String _constructWardenAlertName(String user, Policy policy) {
		assert (user != null) : "User cannot be null.";
		assert (policy != null) : "policy cannot be null.";

		return WAAS_ALERT_NAME_PREFIX + user + "-" + policy.getMetricName();
	}

	public enum TimeUnit {

		SECOND("s", 1000), MINUTE("m", 60 * SECOND.getValue()), HOUR("h", 60 * MINUTE.getValue()), DAY("d",
				24 * HOUR.getValue());

		private final String _unit;
		private final long _value;

		private TimeUnit(String unit, long value) {
			_unit = unit;
			_value = value;
		}

		public String getUnit() {
			return _unit;
		}

		public long getValue() {
			return _value;
		}

		public static TimeUnit fromString(String text) {
			if (text != null) {
				for (TimeUnit unit : TimeUnit.values()) {
					if (text.equalsIgnoreCase(unit.getUnit())) {
						return unit;
					}
				}
			}
			throw new SystemException(text + ": This time unit is not supported.", new UnsupportedOperationException());
		}
	}

	@Override
	@Transactional
	public Policy getPolicy(String name, String service) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(service != null && !service.isEmpty(), "Service cannot be null or empty.");
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		Policy result = Policy.findByNameAndService(em, name, service);

		return result;
	}

	@Override
	@Transactional
	public List<Policy> getPoliciesForService(String service) {
		requireNotDisposed();
		requireArgument(service != null && !service.isEmpty(), "Service cannot be null or empty.");
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		List<Policy> result = Policy.findByService(em, service);

		return result;
	}

	@Override
	@Transactional
	public List<Policy> getPoliciesForName(String name) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		List<Policy> result = Policy.findByName(em, name);

		return result;
	}

	@Override
	@Transactional
	public List<SuspensionLevel> getSuspensionLevels(Policy policy) {
		requireNotDisposed();
		requireArgument(policy != null, "policy cannot be null or empty.");

		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		List<SuspensionLevel> result = SuspensionLevel.findByPolicy(em, policy);

		return result;
	}

	/**
	 * ====================WS API start from here=========================================
	 */

	/**
	 * Return all policies, called by /policy GET username is an optional
	 * parameter.
	 * 
	 * @return The list of policies
	 */
	@Override
	@Transactional
	public List<Policy> getPolicies() {
		requireNotDisposed();
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		List<Policy> result = Policy.findAll(em);

		return result;
	}

	@Override
	@Transactional
	public List<Policy> getPoliciesForUser(String userName) {
		requireNotDisposed();
		requireArgument(userName != null && !userName.isEmpty(), "Name cannot be null or empty.");
		List<Policy> result = getPolicies();

		if (result != null && !result.isEmpty()) {
			result = result.stream().filter(p -> p.getUsers().contains(userName)).collect(Collectors.toList());
		}
		return result;
	}

	/**
	 * Delete policies, called by /policy DELETE
	 * 
	 */
	@Override
	@Transactional
	public void deletePolicies(List<Policy> policies) {
		requireNotDisposed();
		requireArgument(policies != null && !policies.isEmpty(), "Policies cannot be null.");

		for (Policy p : policies) {
			deletePolicy(p);
		}
	}

	/**
	 * Update policies, called by /policy POST and PUT(this is an upsert
	 * operation)
	 * 
	 */
	@Override
	@Transactional
	public List<Policy> updatePolicies(List<Policy> policies) {
		requireNotDisposed();
		requireArgument(policies != null && !policies.isEmpty(), "Policies cannot be null.");
		List<Policy> result = new ArrayList<Policy>();
		for (Policy p : policies) {
			result.add(updatePolicy(p));
		}
		return result;
	}

	/**
	 * Return a policy based on policy Id. called by /policy/{pid} GET
	 * 
	 * @param policyId
	 *            policy id used for query a policy.
	 */
	@Override
	@Transactional
	public Policy getPolicy(BigInteger policyId) {
		requireNotDisposed();
		requireArgument(policyId != null && policyId.signum() == 1, "Policy id cannot be null and must be positive.");

		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		Policy result = Policy.findByPrimaryKey(em, policyId, Policy.class);

		return result;
	}

	/**
	 * Delete a policy, called by /policy/{pid} DELETE
	 * 
	 * @param policy
	 *            policy used for deletion.
	 */
	@Override
	@Transactional
	public void deletePolicy(Policy policy) {
		requireNotDisposed();
		requireArgument(policy != null, "Policy cannot be null.");
		_logger.debug("Deleting a policy {}.", policy);

		EntityManager em = emf.get();

		deleteEntity(em, policy);
		em.flush();
	}

	/**
	 * Update a policy,called by /policy/{pid} POST
	 * 
	 * @param policy
	 *            policy used for update.
	 * @return Policy updated policy
	 */
	@Override
	@Transactional
	public Policy updatePolicy(Policy policy) {
		requireNotDisposed();
		requireArgument(policy != null, "Cannot update a null policy");

		EntityManager em = emf.get();
		Policy result = mergeEntity(em, policy);

		em.flush();
		_logger.debug("Updated policy to : {}", result);
		return result;
	}

	/**
	 * Return all suspension levels based on policy id, called by
	 * /policy/{pid}/level GET
	 * 
	 * @param policy	policy used for query levels
	 * 
	 * @return list of suspension levels
	 */
	@Override
	@Transactional
	public List<SuspensionLevel> getLevels(Policy policy) {
		requireNotDisposed();
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();
		List<SuspensionLevel> result = SuspensionLevel.findByPolicy(em, policy);

		return result;
	}

	/**
	 * Create new suspension levels for a policy based on policy id, called by
	 * /policy/{pid}/level PUT
	 * 
	 * @param	levels	list of suspension levels to be created
	 * 
	 * @return	created suspension levels
	 */
	@Override
	@Transactional
	public List<SuspensionLevel> createLevels(List<SuspensionLevel> levels) {
		requireNotDisposed();
		requireArgument(levels != null && !levels.isEmpty(), "Policies cannot be null.");
		List<SuspensionLevel> result = new ArrayList<SuspensionLevel>();
		for (SuspensionLevel l : levels) {
			result.add(updateLevel(l));
		}
		return result;
	}

	/**
	 * Delete suspension Levels for a policy based on policy id, called by
	 * /policy/{pid}/level DELETE
	 * 
	 * @param	levels	list of suspension levels be deleted
	 */
	@Override
	@Transactional
	public void deleteLevels(List<SuspensionLevel> levels) {
		requireNotDisposed();
		requireArgument(levels != null && !levels.isEmpty(), "Policies cannot be null.");

		for (SuspensionLevel l : levels) {
			deleteLevel(l);
		}
	}

	/**
	 * Return a suspension level of a policy based on suspension level Id.
	 * called by /policy/{pid}/level/{levelid} GET
	 * 
	 * @param policyId
	 *            policy id used for query a level.
	 * @param levelId
	 *            suspension level id used for query a level.
	 * 
	 * @return suspension level
	 */
	@Override
	@Transactional
	public SuspensionLevel getLevel(Policy policy, BigInteger levelId) {
		requireNotDisposed();
		requireArgument(policy != null, "Policy cannot be null or empty.");
		requireArgument(levelId.signum() == 1, "Level id must be positive.");

		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		SuspensionLevel result = SuspensionLevel.findByPolicyAndLevel(em, policy, levelId);

		return result;
	}

	/**
	 * Delete a suspension level of a policy based on suspension level Id.
	 * called by /policy/{pid}/level/{levelid} DELETE
	 * 
	 * @param policyId
	 *            policy id used for query a suspension level.
	 * @param levelId
	 *            suspension level id used for query a suspension level.
	 */

	@Override
	@Transactional
	public void deleteLevel(SuspensionLevel level) {
		requireNotDisposed();
		requireArgument(level != null, "Level cannot be null.");
		_logger.debug("Deleting a level {}.", level);

		EntityManager em = emf.get();

		deleteEntity(em, level);
		em.flush();
	}

	/**
	 * Update a suspension level of a policy based on suspension level Id.
	 * called by /policy/{pid}/level/{levelid} POST Also called by
	 * /policy/{pid}/level POST
	 * 
	 * @param policyId
	 *            policy id used for query a suspension level.
	 * @param levelId
	 *            suspension level id used for query a suspension level.
	 *            
	 * @return 	updated suspension level           
	 */
	@Override
	@Transactional
	public SuspensionLevel updateLevel(SuspensionLevel level) {
		requireNotDisposed();
		requireArgument(level != null, "Cannot update a null policy");

		EntityManager em = emf.get();
		SuspensionLevel result = mergeEntity(em, level);

		em.flush();
		_logger.debug("Updated suspension level to : {}", result);
		return result;
	}

	/**
	 * Return all infractions based on policy id, called by
	 * /policy/{pid}/infraction GET
	 * Also called by /policy/{pid}/suspension
	 * 
	 * @param	policy 	policy used for query infractions
	 * 
	 * @return	list of infractions
	 */
	@Override
	@Transactional
	public List<Infraction> getInfractions(Policy policy) {
		requireNotDisposed();
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();
		List<Infraction> result = Infraction.findByPolicy(em, policy);

		return result;
	}

	/**
	 * Return all infractions based on policy id and username called by
	 * /policy/{pid}/infraction GET 
	 * Also called by /policy/{pid}/user/{uid}/suspension
	 * 
	 * @param	policy		policy used for query infractions
	 * @param	username	username used for query infractions
	 * 
	 * @return 	list of infractions
	 */
	@Override
	@Transactional
	public List<Infraction> getInfractionsByPolicyAndUserName(Policy policy, String username) {
		requireNotDisposed();
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();
		List<Infraction> result = Infraction.findByPolicyAndUserName(em, policy, username);

		return result;
	}

	/**
	 * Return an infraction of a policy based on infraction Id. called by
	 * /policy/{pid}/infracton/{iid} GET
	 * 
	 * @param policyId
	 *            policy id used for query a level.
	 * @param InfractionId
	 *            infraction id used for query a level.
	 *            
	 * @return infraction
	 */
	@Override
	@Transactional
	public Infraction getInfraction(Policy policy, BigInteger infractionId) {
		requireNotDisposed();
		requireArgument(policy != null, "Policy cannot be null or empty.");
		requireArgument(infractionId.signum() == 1, "Infraction id must be positive.");

		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();

		Infraction result = Infraction.findByPolicyAndInfraction(em, policy, infractionId);

		return result;
	}

	/**
	 * Delete a infraction.
	 * 
	 * @param infractionId	deleted infraction
	 */
	@Override
	@Transactional
	public void deleteInfractionById(BigInteger infractionId) {
		requireNotDisposed();
		requireArgument(infractionId != null, "Infraction  id cannot be null.");
		_logger.debug("Deleting a infraction {}.", infractionId);

		EntityManager em = emf.get();
		Infraction infraction = Infraction.findByPrimaryKey(em, infractionId, Infraction.class);
		deleteEntity(em, infraction);
		em.flush();
	}

	/**
	 * Delete infractionss called by /policy/{pid}/user/{uid}/suspension DELETE
	 * Also called by /policy/{pid/suspension DELETE
	 */
	@Override
	@Transactional
	public void deleteInfractionByIds(List<BigInteger> infractionIds) {
		requireNotDisposed();
		requireArgument(infractionIds != null && !infractionIds.isEmpty(), "Infractions cannot be null.");

		for (BigInteger id : infractionIds) {
			deleteInfractionById(id);
		}
	}

	/**
	 * get metrics for a specific user of a policy, called by
	 * /policy/{pid}/user/{uid}/metric GET 
	 * Also called by /user/{uid}/policy/{pid}/metric GET
	 * 
	 * @param policyId
	 *            policy id used to query metrics
	 * @param userId
	 *            user id used to query metrics
	 *            
	 * @return	list of metrics
	 */
	@Override
	public List<com.salesforce.dva.argus.entity.Metric> getMetrics(Policy policy, PrincipalUser user) {
		requireArgument(policy != null, "Policy cannot be null or empty");
		requireArgument(user != null, "User cannot be null or empty");
		List<com.salesforce.dva.argus.entity.Metric> metrics = new ArrayList<com.salesforce.dva.argus.entity.Metric>();
		String expression = _constructMetricExpression(user.getUserName(), policy);

		metrics = _metricService.getMetrics(Arrays.asList(expression));
		return metrics;
	}

	/**
	 * create metrics for a specific user of a policy, called by
	 * /policy/{pid}/user/{uid}/metirc PUT
	 * 
	 * @param policyId
	 *            policy id used to create metrics
	 * @param userId
	 *            user id used to create metrics
	 */
	@Override
	public void creatMetrics(PrincipalUser remoteUser, List<com.salesforce.dva.argus.entity.Metric> legalMetrics) {
		_waaSMonitorService.submitMetrics(remoteUser, legalMetrics);
	}

	/**
	 * Delete an infraction
	 * @param infraction	infraction to be deleted
	 */
	@Override
	@Transactional
	public void deleteInfraction(Infraction infraction) {
		requireNotDisposed();
		requireArgument(infraction != null, "Infraction cannot be null.");
		_logger.debug("Deleting an infraction {}.", infraction);

		EntityManager em = emf.get();

		deleteEntity(em, infraction);
		em.flush();
	}

	/**
	 * get infractions for a specific user called by /user/{uid}/infraction
	 * Also called by /user/{uid}/suspension
	 * 
	 * @param	principalUser	user for query infractons
	 * 
	 * @return	list of infraction
	 */

	@Override
	@Transactional
	public List<Infraction> getInfractionsByUser(PrincipalUser principalUser) {
		requireNotDisposed();
		EntityManager em = emf.get();
		em.getEntityManagerFactory().getCache().evictAll();
		List<Infraction> result = Infraction.findByUser(em, principalUser);

		return result;
	}
}
