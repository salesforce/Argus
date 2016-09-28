package com.salesforce.dva.argus.service;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import com.google.inject.Inject;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Infraction;
import com.salesforce.dva.argus.entity.Policy;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.SuspensionLevel;
import com.salesforce.dva.argus.service.warden.WaaSNotifier;
import com.salesforce.dva.warden.dto.Policy.Aggregator;
import com.salesforce.dva.warden.dto.Policy.TriggerType;
import com.sun.tools.javac.util.Log;

public class WaaSServiceTest extends AbstractTest {
	@Inject

    private UserService _userService;
    private WaaSService _waaSService;
    private AlertService _alertService;
    private static EntityManagerFactory factory;
	private static EntityManager em;
    
	private static Policy testPolicy = null;
    private static final String SERVICE = "service";
    private static final TriggerType TRIGGER_TYPE = TriggerType.EQUAL;
    private static final Aggregator AGGREGATOR = Aggregator.AVG;
    private static final String TIME_UNIT = "5m";
    private static final String CRON_ENTRY = "* * * * *";
    private static final double DEFAULT_VALUE = 99.0;
    private static final List<Double> THRESHOLD = Arrays.asList(100.0);
    
    private static final int LEVEL_NUMBER = 3;
    private static final int INFRACTION_COUNT = 3;
    private static final long SUSPENSION_TIME = 3 * 1000L;
    private static final String POLICY = "policy";
    private static final String WAAS_ALERT_NAME_PREFIX = "waas-";
    
    @Rule public TestName testName = new TestName();
    PrincipalUser user = null;
    @SuppressWarnings("static-access")
	@Before
    @Override
    public void setUp() {
        super.setUp();
        _userService = system.getServiceFactory().getUserService();
        _waaSService = system.getServiceFactory().getWaaSService();
        _alertService = system.getServiceFactory().getAlertService();
        
        factory = Persistence.createEntityManagerFactory("argus-pu");
		em = factory.createEntityManager();
		
        user = _userService.findUserByUsername("rzhang");
        
        if (user == null) {
            user = new PrincipalUser("rzhang", "rzhang@salesforce.com");
            user = _userService.updateUser(user);
        }
        
        /* create policy and suspensionLevel for testing */
        String userName = user.getUserName();		
		testPolicy = new Policy(user, this.SERVICE, this.POLICY, Arrays.asList(userName),
				Arrays.asList(userName), this.TRIGGER_TYPE, this.AGGREGATOR, this.THRESHOLD, this.TIME_UNIT,
				this.DEFAULT_VALUE, this.CRON_ENTRY);
    }
    
  //============is this the reason?==================
    @After
    @Override
    public void tearDown() {
    	try{
    		em.getTransaction().begin();
        	em.flush();
        	em.getTransaction().commit();
        	em.close();
        	factory.close();
        	super.tearDown();
    	}catch (Exception ex) {
    		throw new RuntimeException("failed to tear down!!!");
    	}
    	
    }
  //============is this the reason?==================
   @Test
   public void testPolicyCRUD(){
	   testPolicy.setName(testName.getMethodName());

	   Policy oldPolicy = _waaSService.updatePolicy(testPolicy);
	   assertNotNull(oldPolicy);
	   assertTrue(DEFAULT_VALUE == oldPolicy.getDefaultValue());
	   
	   
	   oldPolicy.setDefaultValue(DEFAULT_VALUE * -1);
	   _waaSService.updatePolicy(oldPolicy);
	   Policy updatedPolicy = _waaSService.getPolicy(oldPolicy.getId());
	   assertTrue(DEFAULT_VALUE * -1 == updatedPolicy.getDefaultValue());
	   
	   _waaSService.deletePolicy(updatedPolicy);
	   Policy deletedPolicy = _waaSService.getPolicy(updatedPolicy.getId());
	   assertEquals(deletedPolicy, null);
   }
   @Test
   public void testPoliciesCRUD(){
	   testPolicy.setName(testName.getMethodName());

	   List<Policy> oldPolicies = _waaSService.updatePolicies(Arrays.asList(testPolicy));
	   assertNotNull(oldPolicies);
	   assertEquals(THRESHOLD.get(0), oldPolicies.get(0).getThreshold().get(0));
	   
	   
	   oldPolicies.get(0).setThreshold(Arrays.asList(THRESHOLD.get(0) * -1));
	   _waaSService.updatePolicies(oldPolicies);
	   List<Policy> updatedPolicies = _waaSService.getPolicies();
	   assertNotNull(updatedPolicies);
	   
	   List<Policy> updatedPoliciesForUser = _waaSService.getPoliciesForUser(testPolicy.getUsers().get(0));
	   assertNotNull(updatedPolicies);
	   assertNotNull(updatedPoliciesForUser);
	   
	   assertEquals((Double) (THRESHOLD.get(0) * -1), updatedPolicies.get(0).getThreshold().get(0));
	   
	   _waaSService.deletePolicies(updatedPolicies);
	   List<Policy> deletedPolicies = _waaSService.getPolicies();
	   assertTrue(deletedPolicies.size()==0);
   }

   @Test
   public void testLevelCRUD(){
	   testPolicy.setName(testName.getMethodName());
	   Policy oldPolicy = _waaSService.updatePolicy(testPolicy);
	   
	   SuspensionLevel testLevel = new SuspensionLevel(this.user, oldPolicy, LEVEL_NUMBER, INFRACTION_COUNT, SUSPENSION_TIME);
	   SuspensionLevel oldLevel = _waaSService.updateLevel(testLevel);
	   assertNotNull(oldLevel);
	   assertEquals(INFRACTION_COUNT, oldLevel.getInfractionCount());
	   
	   
	   oldLevel.setInfractionCount(INFRACTION_COUNT * 100);
	   _waaSService.updateLevel(oldLevel);
	   SuspensionLevel updatedLevel = _waaSService.getLevel(oldPolicy, oldLevel.getId());
	   assertEquals(INFRACTION_COUNT * 100, updatedLevel.getInfractionCount());
	   
	   _waaSService.deleteLevel(updatedLevel);
	   Policy deletedLevel = _waaSService.getPolicy(updatedLevel.getId());
	   assertEquals(deletedLevel, null);
   }
   @Test
   public void testLevelsCRUD(){
	   testPolicy.setName(testName.getMethodName());
	   Policy oldPolicy = _waaSService.updatePolicy(testPolicy);

	   SuspensionLevel testLevel = new SuspensionLevel(this.user, oldPolicy, LEVEL_NUMBER, INFRACTION_COUNT, SUSPENSION_TIME);
	   List<SuspensionLevel> oldLevels = _waaSService.createLevels(Arrays.asList(testLevel));
	   assertNotNull(oldLevels);
	   assertEquals(INFRACTION_COUNT, oldLevels.get(0).getInfractionCount());
	   
	   
	   oldLevels.get(0).setInfractionCount(INFRACTION_COUNT * 100);
	   _waaSService.updateLevel(oldLevels.get(0));
	   List<SuspensionLevel> updatedLevels = _waaSService.getLevels(oldPolicy);
	   assertNotNull(updatedLevels);
	   
	   
	   assertEquals(INFRACTION_COUNT * 100,  updatedLevels.get(0).getInfractionCount());
	   
	   _waaSService.deleteLevels(updatedLevels);
	   List<SuspensionLevel> deletedLevels = _waaSService.getLevels(oldPolicy);
	   assertTrue(deletedLevels.size() == 0);
   }
   
   @Test
   public void testInfractionReadDelete(){
	 	testPolicy.setName(testName.getMethodName());
	 	Policy mergedPolicy = _waaSService.updatePolicy(testPolicy);
	 	_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
	 	List<Infraction> infractionsByPolicy = _waaSService.getInfractions(mergedPolicy);
	 	assertTrue(infractionsByPolicy.size() ==1);
	 	
	 	List<Infraction> infractionsByPolicyAndUserName = _waaSService.getInfractionsByPolicyAndUserName(mergedPolicy,user.getUserName());
	 	assertEquals(user, infractionsByPolicyAndUserName.get(0).getUser());
	 	
	 	Infraction infraction = _waaSService.getInfraction(mergedPolicy, infractionsByPolicy.get(0).getId());
	 	assertEquals(infraction.getId(),infractionsByPolicyAndUserName.get(0).getId());
	 	
	 	_waaSService.deleteInfractionByIds(Arrays.asList(infraction.getId()));
	 	List<Infraction> deletedInfractionsWithId = _waaSService.getInfractions(mergedPolicy);
	 	assertTrue(deletedInfractionsWithId.size() == 0);
	 	
	 	
	 	_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
	 	List<Infraction> infractionsByUser = _waaSService.getInfractionsByUser(user);
	 	assertTrue(infractionsByUser.size() ==1);
	 	_waaSService.deleteInfraction(infractionsByUser.get(0));
	 	List<Infraction> infractionsByUserAfterDeletion = _waaSService.getInfractionsByUser(user);
	 	assertTrue(infractionsByUserAfterDeletion.size() == 0 );

   }
   //=============ws call test ends here====================
	@Test
	public void testUpsertPolicyForCreateAndUpdate() {
		testPolicy.setName(testName.getMethodName());
		_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
		Policy oldPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());

		assertEquals(THRESHOLD.get(0), oldPolicy.getThreshold().get(0));

		_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, Arrays.asList(THRESHOLD.get(0) * -1));
		Policy newPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
		assertTrue(THRESHOLD.get(0) * -1.0 ==  newPolicy.getThreshold().get(0));
	}
	
	@Test
	public void testUpsertPolicyForAlertCreation() {
		testPolicy.setName(testName.getMethodName());
		_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
		
		PrincipalUser adminUser = _userService.findAdminUser();
		String expectedAlertName = WAAS_ALERT_NAME_PREFIX + testPolicy.getUsers().get(0) + "-" + testPolicy.getMetricName();
		Alert expectedAlert = _alertService.findAlertByNameAndOwner(expectedAlertName,  adminUser);
		
		assertTrue(expectedAlert !=null);
		
		Object[] params = {testPolicy.getTimeUnit(), testPolicy.getMetricName() , testPolicy.getUsers().get(0), testPolicy.getAggregator().getDescription()};
		String format = "-{0}:{1}'{'user={2}'}':{3}";
        String expectedExpression = MessageFormat.format(format, params);
        String expectedTriggerName = "policy-value-" + testPolicy.getTriggerType().toString() + "-policy-threshold";
        String expectedNotificationName = "WaaS Notification";
        String expectedNotifierName = WaaSNotifier.class.getName();
        
		assertEquals(expectedAlert.getCronEntry(), testPolicy.getCronEntry());		
		assertEquals(expectedAlert.getExpression(), expectedExpression);
		assertEquals(expectedAlert.getTriggers().get(0).getName(), expectedTriggerName);
		assertEquals(expectedAlert.getTriggers().get(0).getThreshold(), testPolicy.getThreshold().get(0));
		assertEquals(expectedAlert.getTriggers().get(0).getType().value(), testPolicy.getTriggerType().value());
		assertEquals(expectedAlert.getNotifications().get(0).getName(), expectedNotificationName);
		assertEquals(expectedAlert.getNotifications().get(0).getNotifierName(), expectedNotifierName);
	}
    
	@Test
	public void testUpsertSuspensionLevelsForCreateAndUpdate() {
		testPolicy.setName(testName.getMethodName());
		Policy mergedPolicy = _waaSService.updatePolicy(testPolicy);
		_waaSService.updateSuspensionLevel(mergedPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);

		SuspensionLevel oldSuspensionLevel = _waaSService
				.getPolicy(testPolicy.getName(), testPolicy.getService()).getSuspensionLevels()
				.get(0);

		
		assertEquals(LEVEL_NUMBER, oldSuspensionLevel.getLevelNumber());
		assertEquals(INFRACTION_COUNT, oldSuspensionLevel.getInfractionCount());
		assertEquals(SUSPENSION_TIME, oldSuspensionLevel.getSuspensionTime());

		Policy p = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
		_waaSService.updateSuspensionLevel(p, p.getSuspensionLevels().get(0).getLevelNumber(), INFRACTION_COUNT * 100,
				SUSPENSION_TIME * 100);

		SuspensionLevel newSuspensionLevel = _waaSService
				.getPolicy(testPolicy.getName(), testPolicy.getService()).getSuspensionLevels()
				.get(0);
		assertEquals(INFRACTION_COUNT * 100, newSuspensionLevel.getInfractionCount());
		assertEquals(SUSPENSION_TIME * 100, newSuspensionLevel.getSuspensionTime());
	}
    
    @Test
    public void testSuspendUserVerifyInfractionTime(){
    	testPolicy.setName(testName.getMethodName());
    	
    	_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
    	List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy, testPolicy.getUsers().get(0));
    	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
    	List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(oldInfractions.size() + 1 == newInfractions.size());   
    	assertTrue(1 == newInfractions.size()); 
    	assertTrue(System.currentTimeMillis() - newInfractions.get(0).getInfractionTimestamp()  < 1000L);
    }
    
	@Test
	public void testSuspendUserLessThanSuspensionLevelsVerifyDefaultSuspensionTimeAndNotSuspended() {
		testPolicy.setName(testName.getMethodName());
		Policy newPolicy = _waaSService.updatePolicy(testPolicy);
		_waaSService.updateSuspensionLevel(newPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);
		List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy,
				testPolicy.getUsers().get(0));

		Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
		_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
		List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy,
				testPolicy.getUsers().get(0));

		assertTrue(oldInfractions.size() + 1 == newInfractions.size());
		assertTrue(newInfractions.get(0).getExpirationTimestamp() == 0L);
		assertFalse(_waaSService.isSuspended(testPolicy.getUsers().get(0), mergedPolicy));
	}

	@Test
	public void testSuspendUserEqualsToSuspensionLevelVerifyTempSuspensionAndIsSuspended() {
		testPolicy.setName(testName.getMethodName());
		Policy newPolicy = _waaSService.updatePolicy(testPolicy);
		_waaSService.updateSuspensionLevel(newPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);
		List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy,
				testPolicy.getUsers().get(0));

		Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
		for (int i = 0; i < INFRACTION_COUNT; i++) {
			_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
		}

		List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy,
				testPolicy.getUsers().get(0));

		assertTrue(oldInfractions.size() + INFRACTION_COUNT == newInfractions.size());
		assertTrue(newInfractions.get(INFRACTION_COUNT - 1).getExpirationTimestamp()
				- newInfractions.get(INFRACTION_COUNT - 1).getInfractionTimestamp() == SUSPENSION_TIME);
		assertTrue(_waaSService.isSuspended(testPolicy.getUsers().get(0), mergedPolicy));
	}

    @Test
    public void testSuspendUserGreaterThanSuspensionLevelVerifyIndefiniteSuspensionAndIsSuspended(){
    	testPolicy.setName(testName.getMethodName());
    	Policy newPolicy = _waaSService.updatePolicy(testPolicy);
    	_waaSService.updateSuspensionLevel(newPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);
    	List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy, testPolicy.getUsers().get(0));
    	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	for( int i = 0; i <= INFRACTION_COUNT; i++){
    		_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
    	}
    	
    	List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(oldInfractions.size() + INFRACTION_COUNT + 1 == newInfractions.size());  
    	assertTrue(newInfractions.get(INFRACTION_COUNT).getExpirationTimestamp() == -1L);
    	assertTrue(_waaSService.isSuspended(testPolicy.getUsers().get(0),mergedPolicy));
    }
    
    @Test
    public void testReinstateUserForNotSuspendedUserVerifyNoInfractionDeletion(){
    	testPolicy.setName(testName.getMethodName());
    	Policy newPolicy = _waaSService.updatePolicy(testPolicy);
    	_waaSService.updateSuspensionLevel(newPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);
    	List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy, testPolicy.getUsers().get(0));
    	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
    	List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(oldInfractions.size() + 1 == newInfractions.size());    	
    	assertTrue(newInfractions.get(0).getExpirationTimestamp() == 0L);
    	assertFalse(_waaSService.isSuspended(testPolicy.getUsers().get(0),testPolicy));
    	
    	_waaSService.reinstateUser(testPolicy.getUsers().get(0), mergedPolicy);
    	List<Infraction> reinstateInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(reinstateInfractions.size() == newInfractions.size());
    }
    
    @Test
    public void testReinstateUserForTempSuspendedUserVerifyNoInfractionDeletion(){
    	testPolicy.setName(testName.getMethodName());
    	Policy newPolicy = _waaSService.updatePolicy(testPolicy);
    	_waaSService.updateSuspensionLevel(newPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);
    	List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy, testPolicy.getUsers().get(0));
    	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	for( int i = 0; i < INFRACTION_COUNT; i++){
    		_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
    	}
    	
    	List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(oldInfractions.size() + INFRACTION_COUNT == newInfractions.size());  
    	assertTrue(newInfractions.get(INFRACTION_COUNT - 1).getExpirationTimestamp()-newInfractions.get(INFRACTION_COUNT-1).getInfractionTimestamp() == SUSPENSION_TIME);
    	
    	_waaSService.reinstateUser(testPolicy.getUsers().get(0), mergedPolicy);
    	List<Infraction> reinstateInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(reinstateInfractions.size() == newInfractions.size());
    }
    
    @Test
    public void testReinstateUserForIndefiniteSuspendedUserVerifyAllInfractionDeletion(){
    	testPolicy.setName(testName.getMethodName());
    	Policy newPolicy = _waaSService.updatePolicy(testPolicy);
    	_waaSService.updateSuspensionLevel(newPolicy, LEVEL_NUMBER,
				INFRACTION_COUNT, SUSPENSION_TIME);
    	List<Infraction> oldInfractions = Infraction.findByPolicyAndUserName(em, testPolicy, testPolicy.getUsers().get(0));
    	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	for( int i = 0; i <= INFRACTION_COUNT; i++){
    		_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
    	}
    	
    	List<Infraction> newInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(oldInfractions.size() + INFRACTION_COUNT + 1 == newInfractions.size());  
    	assertTrue(newInfractions.get(INFRACTION_COUNT).getExpirationTimestamp() == -1L);
    	
    	_waaSService.reinstateUser(testPolicy.getUsers().get(0), mergedPolicy);
    	List<Infraction> reinstateInfractions = Infraction.findByPolicyAndUserName(em, mergedPolicy, testPolicy.getUsers().get(0));
    	
    	assertTrue(reinstateInfractions.size() == 0);
    }
    
    @Test
    public void testGetPolicyById(){
    	testPolicy.setName(testName.getMethodName());
    	
    	Policy insertedPolicy = _waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
    	BigInteger expectedPolicyId = insertedPolicy.getId();
    			
    	Policy p = _waaSService.getPolicy(expectedPolicyId);
    	
    	assertNotNull(p);
    	assertTrue(expectedPolicyId.equals(p.getId()));   
    }
    
    @Test
    public void testGetPolicy(){
    	testPolicy.setName(testName.getMethodName());
    	
    	Policy insertedPolicy =_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
    	BigInteger expectedPolicyId = insertedPolicy.getId();
    	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	BigInteger mergedPolicyId = mergedPolicy.getId();
    	
    	assertNotNull(mergedPolicy);
    	assertTrue(expectedPolicyId.equals(mergedPolicyId)); 
    }
    
    @Test
    public void testGetPoliciesForService(){
    	testPolicy.setName(testName.getMethodName());
    	testPolicy.setService(testName.getMethodName());
    	
    	_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
    	
    	List<Policy> mergedPolicies = _waaSService.getPoliciesForService(testPolicy.getService());
    	
    	assertNotNull(mergedPolicies);
    	assertTrue(mergedPolicies.size() == 1); 
    }
    
    @Test
    public void testGetPoliciesForName(){
    	testPolicy.setName(testName.getMethodName());
    	
    	_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
    	
    	List<Policy> mergedPolicies = _waaSService.getPoliciesForName(testName.getMethodName());
    	
    	assertNotNull(mergedPolicies);
    	assertTrue(mergedPolicies.size() == 1); 
    }
    
    @Test
    public void testGetInfractions(){
    	testPolicy.setName(testName.getMethodName());
    	_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
	
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	for( int i = 0; i < INFRACTION_COUNT; i++){
    		_waaSService.suspendUser(testPolicy.getUsers().get(0), mergedPolicy);
    	}
    	
    	List<Infraction> expectedInfractions = _waaSService.getInfractionsByPolicyAndUserName(mergedPolicy,testPolicy.getUsers().get(0));
    	assertTrue(expectedInfractions.size() == INFRACTION_COUNT);
    }
    
    @Test
    public void testGetSuspensionLevels(){
    	testPolicy.setName(testName.getMethodName());
    	_waaSService.updatePolicy(testPolicy.getUsers().get(0), testPolicy, testPolicy.getThreshold());
    	Policy mergedPolicy = _waaSService.getPolicy(testPolicy.getName(), testPolicy.getService());
    	for(int i = 1 ; i <= LEVEL_NUMBER ; i ++){
    		_waaSService.updateSuspensionLevel(mergedPolicy, i,
				INFRACTION_COUNT, SUSPENSION_TIME);
    	}
    	
    	int expectedNumber = _waaSService.getSuspensionLevels(mergedPolicy).size();
    	assertTrue(expectedNumber == LEVEL_NUMBER);
    }  
}
