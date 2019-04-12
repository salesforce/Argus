package com.salesforce.dva.argus.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.service.alert.notifier.RefocusForwarder;
import com.salesforce.dva.argus.service.alert.notifier.RefocusProperty;

import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.system.SystemMain;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import org.apache.http.impl.client.*;

import java.util.function.Supplier;
import java.util.Iterator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// =====================================================================================================
// This test is a unit test of the RefocusForwarder class.
// With correct configuration, it can be pointed at Refocus itself or the Refocus sandbox.
// By default it is a fully mocked unit test of the RefocusForwarder.
//
// The variables relevant to configuration of this are:
// test_mode : UNIT_TEST or REFOCUS.  Mocked when UNIT_TEST, talks to a Refocus instance when not.
// use_sandbox:  Selects the refocus sandbox or refocus.
// use_proxy:  Adds proxy configuration.
// use_personal_ssl_certs: Needed when you connect from your desktop.
//
// IMPORTANT - you should create the relevant content in the Refocus instance before running these tests.
// ====================================================================================================

@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class RefocusServiceTest {
	
	@Mock private Provider<EntityManager> _emProviderMock;
	@Mock private HistoryService _historyServiceMock;
	@Mock private ObjectMapper _mapper;

    private SystemMain system;
    private SystemConfiguration _configuration;
	private AtomicInteger _jobCounter;
	private ExecutorService _executor;
	private RefocusForwarder refocusForwarder;

    private final Logger LOGGER = LoggerFactory.getLogger(RefocusServiceTest.class);
    private static final boolean mapToErrorLogging = false;  // call LOGGER.error for all logs when true.


    enum TestMode { UNIT_TEST, REFOCUS };

	// This is the unit test config - enable before checking in.
     private TestMode test_mode = TestMode.UNIT_TEST;
     private boolean use_proxy = false;             // doesn't matter for UNIT_TEST mode.
	 private boolean use_sandbox = true;            // doesn't matter for UNIT_TEST mode.
	 private boolean use_personal_ssl_certs = true; // doesn't matter for UNIT_TEST mode.


    // Note - replace anything you need to here if you switch to TestMode.REFOCUS
    private static final String argus_refocus_user_name="<argus_refocus_user_name>";
    private static final String argus_refocus_user_password="<argus_refocus_user_password>";
    private static final String refocus_prd_proxy = "public0-proxy1-0-prd.data.sfdc.net";
    private static final String refocus_prd_proxy_port = "8080";
    private static final String argus_user_refocus_user_token = "<argus_refocus_user_token>";
    private static final String argus_user_refocus_sandbox_user_token = "<argus_refocus_user_sandbox_token>";

    // These values are needed to configure desktop testing against Refocus or the Refocus Sandbox
    // TODO - write directions for constructing the keystore from the SSL Cert & Key (or write a function that creates the keystore in memory from these 2 files.)
    private static final String my_sandbox_user_token = "<my_refocus_user_token>";
    private static final String my_private_ssl_keystore_path = "<path_to_my_ssl_keystore>"; //
    private static final String my_private_ssl_keystore_password = "<keystore_password>";
    private static final String my_private_ssl_key_password = "<key_password>";


	// @Before
	public void refocus_setup() {

		_configuration = system.getConfiguration();

        _configuration.setProperty("system.property.refocus.enabled", "true");  // How to set property for testing?

        // This mode allows the test to be run against a Refocus instance rather than verifying the forwarder behavior through mocking.
        if (test_mode == TestMode.REFOCUS)
        {
            if (!use_sandbox)
            {
                _configuration.setProperty(RefocusProperty.REFOCUS_ENDPOINT.getName(), "https://refocus.internal.salesforce.com");
                _configuration.setProperty(RefocusProperty.REFOCUS_TOKEN.getName(), argus_user_refocus_user_token); // "<argus_refocus_user_token_from_server_config>");
            }
            else
            {
                _configuration.setProperty(RefocusProperty.REFOCUS_ENDPOINT.getName(), "https://refocus-sandbox.internal.salesforce.com");
                _configuration.setProperty(RefocusProperty.REFOCUS_TOKEN.getName(), my_sandbox_user_token ); // "<argus_refocus_user_token_for_sandbox>");
            }

            if (use_proxy)
            {
                _configuration.setProperty(RefocusProperty.REFOCUS_PROXY_HOST.getName(), refocus_prd_proxy); // e.g. public0-proxy1-0-prd.data.sfdc.net in prd
                _configuration.setProperty(RefocusProperty.REFOCUS_PROXY_PORT.getName(), refocus_prd_proxy_port);
            }

            if (use_personal_ssl_certs)
            {
                _configuration.setProperty(RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_PATH.getName(), my_private_ssl_keystore_path);
                _configuration.setProperty(RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_PASSWORD.getName(), my_private_ssl_keystore_password);
                _configuration.setProperty(RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_KEY_PASSWORD.getName(), my_private_ssl_key_password);
            }

            _info(String.format("Test Mode: REFOCUS  %s%s%s", (use_sandbox?"Refocus Sandbox":"Refocus Production"), (use_proxy?", via Proxy":""), (use_personal_ssl_certs?", SSL Certs Specified":"")));
            _info(String.format("    URL: %s",       _configuration.getValue(RefocusProperty.REFOCUS_ENDPOINT.getName(),   "Unknown")));
            _info(String.format("    Token: %s",     _configuration.getValue(RefocusProperty.REFOCUS_TOKEN.getName(),      "Unknown")));
            _info(String.format("    ProxyHost: %s", _configuration.getValue(RefocusProperty.REFOCUS_PROXY_HOST.getName(), "Unknown")));
            _info(String.format("    ProxyPort: %s", _configuration.getValue(RefocusProperty.REFOCUS_PROXY_PORT.getName(), "Unknown")));
        }
        else if (test_mode == TestMode.UNIT_TEST)
        {
            _configuration.setProperty(RefocusProperty.REFOCUS_MAX_REQUESTS_PER_MINUTE.getName(), "100000"); // any value above 6000 will result in no sleeping in the unit tests.
        }


        if (test_mode == TestMode.UNIT_TEST)
        {
            refocusForwarder = new RefocusForwarder(_configuration, _emProviderMock);

            CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
            refocusForwarder = spy(refocusForwarder);
            when(refocusForwarder.getHttpClient()).thenReturn(mockClient);
        }
        else
        {
            refocusForwarder = new RefocusForwarder(_configuration, _emProviderMock);
        }

		_jobCounter = new AtomicInteger(0);
		_executor = null;
	}


    @BeforeClass
    static public void setUpClass() {
    }

    @AfterClass
    static public void tearDownClass() {
    }

    @Before
    public void setup() {

        system = TestUtils.getInstanceWithInMemProps(); // getInstance();
        system.start();
        refocus_setup();
    }

    @After
    public void tearDown() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }


	// -----------------------------------------------------------------------------------
    // Support for Generating sequences of stubbed responses.
    // -----------------------------------------------------------------------------------

	static class StubResult {
	    int sleep_ms;
	    int  resp_code;
	    JsonObject gson;

	    public StubResult(int sleep_ms, int resp_code, JsonObject gson)
        {
            this.sleep_ms = sleep_ms;
            this.resp_code = resp_code;
            this.gson = gson;
        }
    }

    static JsonObject makeUpcertResponse(String status, int jobId) {
        JsonObject response = new JsonObject();
        response.addProperty("status", status);
        response.addProperty("jobId", jobId);
        return response;
    }

    static StubResult make200(int sleep_ms, int txId)
    {
        return new StubResult(sleep_ms, 200, makeUpcertResponse("OK", txId));
    }

    // OK result
    static StubResult make201(int sleep_ms, int txId)
    {
        return new StubResult(sleep_ms, 201, makeUpcertResponse("OK", txId));
    }

    static StubResult make204(int sleep_ms, int txId)
    {
        return new StubResult(sleep_ms, 204, makeUpcertResponse("OK", txId));
    }

    static StubResult makeTimedOut(int sleep_ms)  // suggest timeouts >= 10000
    {
        return new StubResult(sleep_ms, 408, new JsonObject());
    }

    static StubResult makeTooManyRequests(int sleep_ms)
    {
        return new StubResult(sleep_ms, 429, new JsonObject());
    }

    static StubResult makeWithResponseCode(int sleep_ms, int respCode)
    {
        return new StubResult(sleep_ms, respCode, new JsonObject());
    }

    public static void SleepMs(int sleep_ms)
    {
        try {
            if (sleep_ms > 0)
            {
                Thread.sleep(sleep_ms);
            }
        }
        catch( InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static class ResultStubSupplier implements Supplier<RefocusForwarder.RefocusResponse> {

        protected JsonObject cloneJson(JsonObject r)
        {
            String jsonText = r.toString();
            JsonObject obj = (new JsonParser()).parse(jsonText).getAsJsonObject();
            return obj;
        }

        public RefocusForwarder.RefocusResponse get()
        {
            return null;
        }
    }

	static class ResultListSupplier extends ResultStubSupplier {

	    ArrayList<StubResult> results;
	    Iterator<StubResult> iterator;
	    RefocusForwarder forwarder;
	    StubResult defaultResult;

	    public ResultListSupplier( ArrayList<StubResult> resultsToReturn, StubResult defaultResult, RefocusForwarder forwarder)
        {
            super();
            this.results = resultsToReturn;
            this.iterator = resultsToReturn.iterator();
            this.forwarder = forwarder;
            this.defaultResult = defaultResult;
        }

        @Override
	    public RefocusForwarder.RefocusResponse get()
        {
            StubResult r = this.iterator.hasNext() ? this.iterator.next() : defaultResult;
            SleepMs(r.sleep_ms);
            RefocusForwarder.RefocusResponse refocus_response = forwarder.makeResponse(r.resp_code, cloneJson(r.gson));
            return refocus_response;
        }
    }

    static class ResultCycleStubSupplier extends ResultStubSupplier {

        StubResult[] resultCycle;
        Iterator<StubResult> iterator;
        RefocusForwarder forwarder;
        int pos = 0;

        public ResultCycleStubSupplier( StubResult[] resultCycle, RefocusForwarder forwarder)
        {
            this.resultCycle = resultCycle;
            this.pos = 0;
            this.forwarder = forwarder;
            assert(this.resultCycle.length > 0);
        }

        @Override
        public RefocusForwarder.RefocusResponse get()
        {
            StubResult r = this.resultCycle[ this.pos % this.resultCycle.length ];
            this.pos = (this.pos + 1) % this.resultCycle.length;
            SleepMs(r.sleep_ms);
            RefocusForwarder.RefocusResponse refocus_response = forwarder.makeResponse(r.resp_code, cloneJson(r.gson));
            return refocus_response;
        }
    }

    void enqueueSamples(int numSamples, History history)
    {
        enqueueSamples(numSamples, history, 0, 0);
    }

    void enqueueSamples(int numSamples, History history, int sleep_interval, long sleep_ms)
    {
        IntStream.range(0,numSamples).forEach( x -> {

            String value = String.format("%d", x);
            String sa = String.format("a.b%d|c", x);
            try
            {
                refocusForwarder.sendRefocusNotification(sa, value, "myuser", "mytoken", history);

                if (sleep_ms > 0 && sleep_interval > 0)
                {
                    if ( ((x+1) % sleep_interval) == 0)
                    {
                        Thread.sleep(sleep_ms);
                    }
                }
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    void waitUntilQueuedNotificationsAreProcessed(long extraSleep, long maxWait, long check_period_ms) throws InterruptedException
    {
        long start = System.currentTimeMillis();
        long duration = 0;

        while( ((maxWait > 0 && duration < maxWait) || (maxWait <= 0)) &&
                refocusForwarder.getNotificationsDelivered() + refocusForwarder.getNotificationsDiscarded() < refocusForwarder.getNotificationsEnqueued())
        {
            Thread.sleep(check_period_ms);
            duration = System.currentTimeMillis() - start;
        }
        Thread.sleep(extraSleep);
    }

    void logForwarderStats()
    {
        int maxQueueLength = refocusForwarder.getMaxQueueLength();
        _info(MessageFormat.format("MaxQueueLength was {0} samples", maxQueueLength));
    }

    void verifyProcessed( long expect_delivered, long expect_discarded, long enqueued)
    {
        long delivered = refocusForwarder.getNotificationsDelivered();
        long discarded = refocusForwarder.getNotificationsDiscarded();
        long processed = refocusForwarder.getNotificationsProcessed();
        assertEquals(expect_delivered, delivered);
        assertEquals(expect_discarded, discarded);
        assertEquals(processed, delivered+discarded);
        assertEquals(processed, enqueued);
    }

	// -----------------------------------------------------------------------------------
	// Tests
	// -----------------------------------------------------------------------------------

	@Test
	public void testRefocusForwarderStartStop() {

        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

		startRefocusService();
		try
		{
			Thread.sleep(1000);
		}
		catch( InterruptedException e)
		{}

		stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        verifyProcessed(0, 0, 0);
    }


	@Test
	public void testRefocusForwarderSendSomeSamples() {

        int timeout = 45;
        int extra_sleep = 2000;
        int check_period_ms = 500;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        if (test_mode == TestMode.UNIT_TEST)
        {
            timeout = 0; // 10;
            extra_sleep = 100;
            check_period_ms = 0; // 20;

            ResultListSupplier responseSupplier = new ResultListSupplier(new ArrayList<StubResult>(),
                    make200(timeout, 1),
                    refocusForwarder);

            refocusForwarder.setStubSender(responseSupplier);
        }


		startRefocusService();
		History history = mock(History.class);
		try
		{
		    enqueueSamples(1000, history);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 12000, check_period_ms);
		}
		catch( InterruptedException e)
		{}
		catch( RuntimeException e)
		{}

		stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(1000, 0, 1000);
	}

    @Test
    public void testRefocusForwarderThrottled() {

	    Assume.assumeTrue(test_mode == TestMode.UNIT_TEST);

	    int timeout = 0; // 10;
        int extra_sleep = 100;
        int check_period_ms = 0; // 20;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeTooManyRequests(120),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);

        startRefocusService();
        History history = mock(History.class);
        try
        {
            enqueueSamples(2000, history);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 12000, check_period_ms);

        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(0, 2000, 2000);
    }


    @Test
    public void testRefocusForwarderTimedOut() {

        Assume.assumeTrue(test_mode == TestMode.UNIT_TEST);

        int timeout = 0; // 10;
        int extra_sleep = 100;
        int check_period_ms = 0; // 20;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeTimedOut(timeout),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);

        startRefocusService();
        History history = mock(History.class);
        try
        {
            enqueueSamples(2000, history);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 50000, check_period_ms);
        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(0, 2000, 2000);
    }

    @Test
    public void testRefocusNoAuth() {

        Assume.assumeTrue(test_mode == TestMode.UNIT_TEST);

        int timeout= 0; // 10; // 10000;
        int extra_sleep = 100; // 10000;
        int check_period_ms = 0; // 20;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        ResultListSupplier responseSupplier = new ResultListSupplier(
                    new ArrayList<StubResult>(),
                    makeWithResponseCode(timeout, 401),
                    refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);


        startRefocusService();
        History history = mock(History.class);
        try
        {
            enqueueSamples(2000, history);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 50000, check_period_ms);
        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(0, 2000, 2000);
    }

    @Test
    public void testRefocusServiceUnavail() {

        Assume.assumeTrue(test_mode == TestMode.UNIT_TEST);

        int timeout= 0; // 10;
        int extra_sleep = 100;
        int check_period_ms = 0; // 20;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeWithResponseCode(timeout, 503),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);


        startRefocusService();
        History history = mock(History.class);
        try
        {
            enqueueSamples(2000, history);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 50000, check_period_ms);
        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(0, 2000, 2000);
    }

    @Test
    public void testIntermittentThrottle() {

        Assume.assumeTrue(test_mode == TestMode.UNIT_TEST);

        int timeout = 0; // 10;
        int enqueue_timeout = 0; // 10;
        int extra_sleep = 100;
        int check_period_ms = 0; // 20;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();


        // NOTE: This assumes that the #retries == 3
        StubResult [] cycle = {
                makeTooManyRequests(timeout),
                makeTooManyRequests(timeout),
                make200(timeout, 1)
        };
        ResultCycleStubSupplier responseSupplier = new ResultCycleStubSupplier( cycle, refocusForwarder);
        refocusForwarder.setStubSender(responseSupplier);

        int num_notifications = 100000;

        startRefocusService();
        History history = mock(History.class);
        try
        {
            enqueueSamples(num_notifications, history, 1000, enqueue_timeout);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 50000, check_period_ms);
        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(num_notifications, 0, num_notifications);
    }

    @Ignore
    @Test
    public void test10mNotifications() {

	    int timeout = 250;
        int enqueue_timeout = 1000;
        int extra_sleep = 10000;
        int check_period_ms = 500;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        if (test_mode == TestMode.UNIT_TEST)
        {
            timeout = 0; // 10;
            enqueue_timeout = 0; // 10;
            extra_sleep = 100;
            check_period_ms = 0; // 20;

            ResultListSupplier responseSupplier = new ResultListSupplier(
                    new ArrayList<StubResult>(),
                    make200(timeout, 1),
                    refocusForwarder);

            refocusForwarder.setStubSender(responseSupplier);
        }

        int num_notifications = 600000; // 600k - should take 10mins

        startRefocusService();
        History history = mock(History.class);
        try
        {
            // 1000 samples every second.
            enqueueSamples(num_notifications, history, 1000, enqueue_timeout);
            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 50000, check_period_ms);
        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(num_notifications, 0, num_notifications);
    }


    @Test
    public void testRefocusForwarderSend100kSamples() throws Exception {

        int timeout = 45;
        int enqueue_timeout = 50;
        int extra_sleep = 1000;
        int check_period_ms = 500;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        if (test_mode == TestMode.UNIT_TEST)
        {
            timeout = 0; // 10;
            enqueue_timeout = 0; // 10;
            extra_sleep = 100;
            check_period_ms = 0; // 20;

            ResultListSupplier responseSupplier = new ResultListSupplier(
                    new ArrayList<StubResult>(),
                    make200(timeout, 1),
                    refocusForwarder);

            refocusForwarder.setStubSender(responseSupplier);
        }

        int num_samples = 100000;

        startRefocusService();
        History history = mock(History.class);
        try
        {
            long start = System.currentTimeMillis();
            enqueueSamples(num_samples, history, 1000, enqueue_timeout);
            long end = System.currentTimeMillis();
            long duration = end - start;
            _info(MessageFormat.format("Enqueued {0} samples in  {1}ms", num_samples, duration));

            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 60000, check_period_ms);

        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(num_samples, 0, num_samples);
    }

    @Test
    public void test60kSamplesPerMinute() throws Exception {

        int timeout = 45;
        int enqueue_timeout = 1000;
        int extra_sleep = 1000;
        int check_period_ms = 500;
        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        if (test_mode == TestMode.UNIT_TEST)
        {
            timeout = 0; // 10;
            enqueue_timeout = 0; // 10;
            extra_sleep = 100;
            check_period_ms = 0; // 20;

            ResultListSupplier responseSupplier = new ResultListSupplier(
                    new ArrayList<StubResult>(),
                    make200(timeout, 1),
                    refocusForwarder);

            refocusForwarder.setStubSender(responseSupplier);
        }

        int num_samples = 60000;

        startRefocusService();
        History history = mock(History.class);
        try
        {
            long start = System.currentTimeMillis();
            enqueueSamples(num_samples, history, 1000, enqueue_timeout);
            long end = System.currentTimeMillis();
            long duration = end - start;
            _info(MessageFormat.format("Enqueued {0} samples in {1}ms", num_samples, duration));

            waitUntilQueuedNotificationsAreProcessed(extra_sleep, 120000, check_period_ms);

        }
        catch( InterruptedException e)
        {}
        catch( RuntimeException e)
        {}

        stopRefocusService();
        _error(MessageFormat.format("Test duration= {0}ms", d.duration()));
        logForwarderStats();
        verifyProcessed(num_samples, 0, num_samples);
    }



    // -------------------------------------------------------------------------------------------------------
    // Thread Pool and Executor Support
    // -------------------------------------------------------------------------------------------------------

	private void startRefocusService() {
		assert(_executor == null);
		try {
			_info("Starting Refocus service.");
			_executor = startRefocusClientService(refocusForwarder);
			_info("Refocus service started.");
		} catch (Exception ex) {
			throw new SystemException("There was a problem starting the Refocus Service.", ex);
		}
	}

	private void stopRefocusService() {
		assert(_executor != null);

		try {

			_info("Stopping Refocus service.");
			_executor.shutdownNow();
			try
			{
				if (!_executor.awaitTermination(60000, TimeUnit.MILLISECONDS))
				{
					_warn("Shutdown timed out after 60 seconds.  Exiting.");
				}
			} catch (InterruptedException iex)
			{
				_warn("Forcing shutdown of Refocus Service.");
			}
			_info("Service stopped.");
		} catch (Exception ex) {
			throw new SystemException("There was a problem shutting down the Refocus Service.", ex);
		} finally {
			_info("Finished");
			_executor = null;
		}
	}


	private ExecutorService startRefocusClientService(RefocusService refocus) {
		int configuredCount = Integer.valueOf(_configuration.getValue(SystemConfiguration.Property.REFOCUS_CLIENT_THREADS));
		int configuredTimeout = Integer.valueOf(_configuration.getValue(SystemConfiguration.Property.REFOCUS_CLIENT_CONNECT_TIMEOUT));
		int threadPoolCount = Math.max(configuredCount, 1);
		int timeout = Math.max(10000, configuredTimeout);
        AtomicInteger jobCounter = new AtomicInteger(0);

        ExecutorService service = Executors.newFixedThreadPool(threadPoolCount, new ThreadFactory() {

			AtomicInteger id = new AtomicInteger(0);

			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, MessageFormat.format("RefocusServiceTest-{0}", id.getAndIncrement()));
			}
		});
		for (int i = 0; i < threadPoolCount; i++) {
			service.submit(new RefocusRunner(refocus, timeout, jobCounter));
		}
		return service;
	}


	class RefocusRunner implements Runnable {

		//~ Instance fields ******************************************************************************************************************************

		private final RefocusService service;
		private final int timeout;
		private final AtomicInteger jobCounter;
		private final Logger LOGGER = LoggerFactory.getLogger(RefocusService.class);

		//~ Constructors *********************************************************************************************************************************

		/**
		 * Creates a new Alerter object.
		 *
		 * @param  service     The Refocus service to use.
		 * @param  timeout     The timeout in milliseconds for a single alert evaluation. Must be a positive number.
		 * @param  jobCounter  The job counter. Cannot be null.
		 */
		RefocusRunner(RefocusService service, int timeout, AtomicInteger jobCounter) {
			this.service = service;
			this.timeout = timeout;
			this.jobCounter = jobCounter;
		}

		//~ Methods **************************************************************************************************************************************

		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					int forwarded = service.forwardNotifications();
					Thread.sleep(10);
				} catch (InterruptedException ex) {
				    // TODO - should we catch RuntimeException and handle wrapped InterruptedExceptions too?
					_info("Execution was interrupted.");
					Thread.currentThread().interrupt();
					break;
				} catch (Throwable ex) {
					_error(String.format("Exception in alerter: %s", ExceptionUtils.getFullStackTrace(ex)));
				}
			}
			_warn(String.format("Refocus thread interrupted. %d notifications forwardeed by this thread.", jobCounter.get()));
			service.dispose();
		}
	}



	// =================================================================================
    // Logging wrappers
    // Note: these exist because I haven't found a mechanism for setting the log level programmatically.
    // =================================================================================


    // Syntactic sugar
    private void _info(String msg)
    {
        if (mapToErrorLogging) {
            LOGGER.error(msg);
        }
        else {
            LOGGER.info(msg);
        }
    }

    private void _error(String msg)
    {
        LOGGER.error(msg);
    }

    private void _warn(String msg)
    {
        if (mapToErrorLogging) {
            LOGGER.error(msg);
        }
        else {
            LOGGER.warn(msg);
        }
    }

}
