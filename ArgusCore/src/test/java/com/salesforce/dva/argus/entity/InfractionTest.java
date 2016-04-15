package com.salesforce.dva.argus.entity;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;

public class InfractionTest {

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
	private static final String PRINCIPAL_USER = "principal_user";
	private static final Long EXPIRATION_TIME = 0L;
	private static final Long INRACTION_TIME_CREATE = 0L;
	private static final Long INRACTION_TIME_UPDATE = 1L;
	private static final Long INRACTION_TIME_DELETE = 2L;

	private static EntityManagerFactory factory;
	private static EntityManager em;
	private static PrincipalUser creator, user;
	private static Policy policy;

	@BeforeClass
	public static void setUp() {
		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		em = factory.createEntityManager();

		em.getTransaction().begin();

		creator = new PrincipalUser(PRINCIPAL_CREATOR, PRINCIPAL_CREATOR);
		em.persist(creator);
		policy = new Policy(creator, SERVICE, POLICY, OWNER, USER, SUBSYSTEM, METRIC_NAME, TRIGGER_TYPE,
				AGGREGATOR, THRESHOLD, TIMEUNIT, DEFAULT_VALUE, CRON_ENTRY);
		em.persist(policy);
		user = new PrincipalUser(PRINCIPAL_USER, PRINCIPAL_USER);
		em.persist(user);

		em.getTransaction().commit();
	}

	@AfterClass
	public static void closeTestFixture() {
		em.close();
		factory.close();
	}

	@Test
	public void testCreateInfraction() {
		em.getTransaction().begin();
		Infraction infraction = new Infraction(creator, policy, user, INRACTION_TIME_CREATE, EXPIRATION_TIME);
		em.persist(infraction);
		em.getTransaction().commit();

		Query q = em.createQuery("select i from Infraction i WHERE i.infractionTimestamp = :infractionTimestamp");
		q.setParameter("infractionTimestamp", INRACTION_TIME_CREATE);
		assertTrue(((Infraction) q.getSingleResult()).getInfractionTimestamp() == INRACTION_TIME_CREATE);
	}

	@Test
	public void testUpdateInfraction() {
		em.getTransaction().begin();
		Infraction infraction = new Infraction(creator, policy, user, Long.MAX_VALUE, EXPIRATION_TIME);
		em.persist(infraction);
		infraction.setInfractionTimestamp(INRACTION_TIME_UPDATE);
		em.merge(infraction);
		em.getTransaction().commit();

		Query q = em.createQuery("select i from Infraction i WHERE i.infractionTimestamp = :infractionTimestamp");
		q.setParameter("infractionTimestamp", INRACTION_TIME_UPDATE);
		assertEquals(q.getResultList().size(), 1);
		assertTrue(((Infraction) q.getSingleResult()).getInfractionTimestamp() == INRACTION_TIME_UPDATE);
	}

	@Test
	public void testDeleteInfraction() {
		em.getTransaction().begin();
		Infraction infraction = new Infraction(creator, policy, user, INRACTION_TIME_DELETE, EXPIRATION_TIME);
		em.persist(infraction);

		Query q = em.createQuery("select i from Infraction i WHERE i.infractionTimestamp = :infractionTimestamp");
		q.setParameter("infractionTimestamp", INRACTION_TIME_DELETE);
		assertTrue(((Infraction) q.getSingleResult()).getInfractionTimestamp() == INRACTION_TIME_DELETE);
		
		em.remove(infraction);
		em.flush();
		em.getTransaction().commit();
		assertEquals(q.getResultList().size(), 0);
	}
}
