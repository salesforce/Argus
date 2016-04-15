package com.salesforce.dva.argus.entity;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;

public class PolicyTest {

	private static final String PERSISTENCE_UNIT_NAME = "argus-pu";
	private static final String SERVICE = "service";
	private static final List<String> OWNER = Arrays.asList("owner");
	private static final List<String> USER = Arrays.asList("user");
	private static final String SUBSYSTEM = "subsystem";
	private static final String METRIC_NAME = "metric_name";
	private static final TriggerType TRIGGER_TYPE = TriggerType.GREATER_THAN;
	private static final String AGGREGATOR = "aggregator";
	private static final List<Double> THRESHOLD = Arrays.asList(0.0);
	private static final String TIMEUNIT = "timeunit";
	private static final double DEFAULT_VALUE = 0.0;
	private static final String CRON_ENTRY = "00**3*";
	private static final String PRINCIPAL_CREATOR = "principal_creator";
	private static final String POLICY_CREATE = "policy_create";
	private static final String POLICY_UPDATE = "policy_update";
	private static final String POLICY_DELETE = "policy_delete";

	private static EntityManagerFactory factory;
	private static EntityManager em;
	private static PrincipalUser creator;

	@BeforeClass
	public static void setUp() {
		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		em = factory.createEntityManager();

		em.getTransaction().begin();

		creator = new PrincipalUser(PRINCIPAL_CREATOR, PRINCIPAL_CREATOR);
		em.persist(creator);

		em.getTransaction().commit();
	}

	@AfterClass
	public static void closeTestFixture() {
		em.close();
		factory.close();
	}

	@Test
	public void testCreatePolicy() {
		em.getTransaction().begin();
		Policy policy = new Policy(creator, SERVICE, POLICY_CREATE, OWNER, USER, SUBSYSTEM, METRIC_NAME, TRIGGER_TYPE,
				AGGREGATOR, THRESHOLD, TIMEUNIT, DEFAULT_VALUE, CRON_ENTRY);
		em.persist(policy);
		em.getTransaction().commit();

		Query q = em.createQuery("select p from Policy p WHERE p.name = :name");
		q.setParameter("name", POLICY_CREATE);
		assertTrue(((Policy) q.getSingleResult()).getName().equals(POLICY_CREATE));
	}

	@Test
	public void testUpdatePolicy() {
		em.getTransaction().begin();
		Policy policy = new Policy(creator, SERVICE, null, OWNER, USER, SUBSYSTEM, METRIC_NAME, TRIGGER_TYPE,
				AGGREGATOR, THRESHOLD, TIMEUNIT, DEFAULT_VALUE, CRON_ENTRY);
		em.persist(policy);
		policy.setName(POLICY_UPDATE);
		em.merge(policy);
		em.getTransaction().commit();

		Query q = em.createQuery("select p from Policy p WHERE p.name = :name");
		q.setParameter("name", POLICY_UPDATE);
		assertEquals(q.getResultList().size(), 1);
		assertTrue(((Policy) q.getSingleResult()).getName().equals(POLICY_UPDATE));
	}

	@Test
	public void testDeletePolicy() {
		em.getTransaction().begin();
		Policy policy = new Policy(creator, SERVICE, POLICY_DELETE, OWNER, USER, SUBSYSTEM, METRIC_NAME, TRIGGER_TYPE,
				AGGREGATOR, THRESHOLD, TIMEUNIT, DEFAULT_VALUE, CRON_ENTRY);
		em.persist(policy);
		policy.setName(POLICY_DELETE);
		em.merge(policy);

		Query q = em.createQuery("select p from Policy p WHERE p.name = :name");
		q.setParameter("name", POLICY_DELETE);
		assertTrue(((Policy) q.getSingleResult()).getName().equals(POLICY_DELETE));
		
		em.remove(policy);
		em.flush();
		em.getTransaction().commit();
		assertEquals(q.getResultList().size(), 0);
	}
}
