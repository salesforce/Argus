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

public class SuspensionLevelTest {

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

	private static final String POLICY = "policy";
	private static final String PRINCIPAL_CREATOR = "principal_creator";
	private static final int LEVEL_NUMBER_CREATE = 0;
	private static final int LEVEL_NUMBER_UPDATE = 1;
	private static final int LEVEL_NUMBER_DELETE = 2;
	private static final int INFRACTION_COUNT = 0;
	private static final Long SUSPENSION_TIME = 0L;

	private static EntityManagerFactory factory;
	private static EntityManager em;
	private static PrincipalUser creator;
	private static Policy policy;

	@BeforeClass
	public static void setUp() {
		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		em = factory.createEntityManager();

		em.getTransaction().begin();

		creator = new PrincipalUser(PRINCIPAL_CREATOR, PRINCIPAL_CREATOR);
		em.persist(creator);
		policy = new Policy(creator, SERVICE, POLICY, OWNER, USER, SUBSYSTEM, METRIC_NAME, TRIGGER_TYPE, AGGREGATOR,
				THRESHOLD, TIMEUNIT, DEFAULT_VALUE, CRON_ENTRY);
		em.persist(policy);

		em.getTransaction().commit();
	}

	@AfterClass
	public static void closeTestFixture() {
		em.close();
		factory.close();
	}

	@Test
	public void testCreateSuspensionLevel() {
		em.getTransaction().begin();
		SuspensionLevel suspensionLevel = new SuspensionLevel(creator, policy, LEVEL_NUMBER_CREATE, INFRACTION_COUNT,
				SUSPENSION_TIME);
		em.persist(suspensionLevel);
		em.getTransaction().commit();

		Query q = em.createQuery("select s from SuspensionLevel s WHERE s.levelNumber = :levelNumber");
		q.setParameter("levelNumber", LEVEL_NUMBER_CREATE);
		assertTrue(((SuspensionLevel) q.getSingleResult()).getLevelNumber() == LEVEL_NUMBER_CREATE);
	}

	@Test
	public void testUpdateSuspensionLevel() {
		em.getTransaction().begin();
		SuspensionLevel suspensionLevel = new SuspensionLevel(creator, policy, Integer.MAX_VALUE, INFRACTION_COUNT,
				SUSPENSION_TIME);
		em.persist(suspensionLevel);
		suspensionLevel.setLevelNumber(LEVEL_NUMBER_UPDATE);
		em.merge(suspensionLevel);
		em.getTransaction().commit();

		Query q = em.createQuery("select s from SuspensionLevel s WHERE s.levelNumber = :levelNumber");
		q.setParameter("levelNumber", LEVEL_NUMBER_UPDATE);
		assertEquals(q.getResultList().size(), 1);
		assertTrue(((SuspensionLevel) q.getSingleResult()).getLevelNumber() == LEVEL_NUMBER_UPDATE);
	}

	@Test
	public void testDeleteSuspensionLevel() {
		em.getTransaction().begin();
		SuspensionLevel suspensionLevel = new SuspensionLevel(creator, policy, LEVEL_NUMBER_DELETE, INFRACTION_COUNT,
				SUSPENSION_TIME);
		em.persist(suspensionLevel);

		Query q = em.createQuery("select s from SuspensionLevel s WHERE s.levelNumber = :levelNumber");
		q.setParameter("levelNumber", LEVEL_NUMBER_DELETE);
		assertTrue(((SuspensionLevel) q.getSingleResult()).getLevelNumber() == LEVEL_NUMBER_DELETE);
		
		em.remove(suspensionLevel);
		em.flush();
		em.getTransaction().commit();
		assertEquals(q.getResultList().size(), 0);
	}
}
