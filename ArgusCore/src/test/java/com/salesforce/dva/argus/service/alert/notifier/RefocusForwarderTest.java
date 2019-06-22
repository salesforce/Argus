package com.salesforce.dva.argus.service.alert.notifier;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;

import com.salesforce.dva.argus.TestUtils;


import com.salesforce.dva.argus.service.RefocusService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemMain;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import org.apache.http.impl.client.*;
import org.apache.http.HttpStatus;

import java.util.function.Supplier;
import java.util.Iterator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;


@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class RefocusForwarderTest {
	
	@Mock private Provider<EntityManager> _emProviderMock;
	@Mock private HistoryService _historyServiceMock;
	@Mock private ObjectMapper _mapper;

    private SystemMain system;
    private SystemConfiguration _configuration;
	private RefocusForwarder refocusForwarder;

    private final Logger LOGGER = LoggerFactory.getLogger(RefocusForwarderTest.class);
    private static final boolean mapToErrorLogging = false;  // call LOGGER.error for all logs when true.


	// @Before
	public void refocus_setup() {

		_configuration = system.getConfiguration();

        _configuration.setProperty("system.property.refocus.enabled", "true");
        _configuration.setProperty(RefocusProperty.REFOCUS_MAX_REQUESTS_PER_MINUTE.getName(), "100000"); // any value above 6000 will result in no sleeping in the unit tests.
        _configuration.setProperty(RefocusProperty.REFOCUS_SEND_INTERVAL_MS.getName(), "0");             // no waiting
        _configuration.setProperty(RefocusProperty.REFOCUS_MAX_BULK_ITEMS.getName(), "50");              // 50 samples per bulk request
        _configuration.setProperty(RefocusProperty.REFOCUS_CONNECTION_REFRESH_MAX_TIMES.getName(), "3"); // ensure it is at least 3

        refocusForwarder = new RefocusForwarder(_configuration, _emProviderMock);

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        refocusForwarder = spy(refocusForwarder);
        when(refocusForwarder.getHttpClient()).thenReturn(mockClient);
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
        system.start();  // 4s initially, 0.7s each subsequent run.
        refocus_setup(); // 50ms initially, 0-2ms each subsequent run.
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

        public void action() throws RuntimeException
        {
        }
    }


    static class StubIOExceptionResult extends StubResult {

	    public StubIOExceptionResult(int sleep_ms, int resp_code, JsonObject gson)
        {
            super(sleep_ms, resp_code, gson);
        }

        @Override
        public void action() throws RuntimeException
        {
            throw new RuntimeException(new IOException());
        }
    }

    static class StubInterruptedIOExceptionResult extends StubResult {

        public StubInterruptedIOExceptionResult(int sleep_ms, int resp_code, JsonObject gson)
        {
            super(sleep_ms, resp_code, gson);
        }

        @Override
        public void action() throws RuntimeException
        {
            throw new RuntimeException(new InterruptedIOException());
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
        return new StubResult(sleep_ms, HttpStatus.SC_OK, makeUpcertResponse("OK", txId));
    }

    // OK result
    static StubResult make201(int sleep_ms, int txId)
    {
        return new StubResult(sleep_ms, HttpStatus.SC_CREATED, makeUpcertResponse("OK", txId));
    }

    static StubResult make204(int sleep_ms, int txId)
    {
        return new StubResult(sleep_ms, HttpStatus.SC_NO_CONTENT, makeUpcertResponse("OK", txId));
    }

    static StubResult makeTimedOut(int sleep_ms)  // suggest timeouts >= 10000
    {
        return new StubResult(sleep_ms, HttpStatus.SC_REQUEST_TIMEOUT, new JsonObject());
    }

    static StubResult makeTooManyRequests(int sleep_ms)
    {
        return new StubResult(sleep_ms, 429, new JsonObject());
    }

    static StubResult makeWithResponseCode(int sleep_ms, int respCode)
    {
        return new StubResult(sleep_ms, respCode, new JsonObject());
    }

    static StubResult makeWithIOException(int sleep_ms, int respCode)
    {
        return new StubIOExceptionResult(sleep_ms, respCode, new JsonObject());
    }

    static StubResult makeWithInterruptedIOException(int sleep_ms, int respCode)
    {
        return new StubInterruptedIOExceptionResult(sleep_ms, respCode, new JsonObject());
    }

    static class ResultStubSupplier implements Supplier<RefocusForwarder.RefocusResponse> {

        protected JsonObject cloneJson(JsonObject r)
        {
            String jsonText = r.toString();
            JsonObject obj = (new JsonParser()).parse(jsonText).getAsJsonObject();
            return obj;
        }

        public RefocusForwarder.RefocusResponse get() throws RuntimeException
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
	    public RefocusForwarder.RefocusResponse get() throws RuntimeException
        {
            StubResult r = this.iterator.hasNext() ? this.iterator.next() : defaultResult;
            // SleepMs(r.sleep_ms);
            r.action();
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
        public RefocusForwarder.RefocusResponse get() throws RuntimeException
        {
            StubResult r = this.resultCycle[ this.pos % this.resultCycle.length ];
            this.pos = (this.pos + 1) % this.resultCycle.length;
            // SleepMs(r.sleep_ms);
            r.action();
            RefocusForwarder.RefocusResponse refocus_response = forwarder.makeResponse(r.resp_code, cloneJson(r.gson));
            return refocus_response;
        }
    }

    void enqueueSamples(int numSamples, History history)
    {
        IntStream.range(0,numSamples).forEach( x -> {

            String value = String.format("%d", x);
            String sa = String.format("a.b%d|c", x);
            try
            {
                refocusForwarder.sendRefocusNotification(sa, value, "myuser", "mytoken", history);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }


    boolean waitUntilQueuedNotificationsAreProcessed(RefocusService service)
    {
        boolean rv = true;
        try
        {
            while( refocusForwarder.getNotificationsDelivered() + refocusForwarder.getNotificationsDiscarded() < refocusForwarder.getNotificationsEnqueued())
            {
                int forwarded = service.forwardNotifications();
            }
        }
        catch( InterruptedException | RuntimeException e)
        {
            rv = false;
        }
        return false;
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
	public void testRefocusForwarderSendSamples() {

        ResultListSupplier responseSupplier = new ResultListSupplier(new ArrayList<StubResult>(),
                make200(10, 1),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);
        int num_notifications = 100;

		History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(num_notifications, 0, num_notifications);
	}

    @Test
    public void testRefocusForwarderThrottled() {

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeTooManyRequests(120),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);
        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(0, num_notifications, num_notifications);
    }


    @Test
    public void testRefocusForwarderTimedOut() {

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeTimedOut(10),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);
        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(0, num_notifications, num_notifications);
    }

    @Test
    public void testRefocusForwarderInterruptedIOException() {

        RefocusForwarder.Duration d = new RefocusForwarder.Duration();

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeWithInterruptedIOException(10, -1),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);
        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(0, num_notifications, num_notifications);
    }

    @Test
    public void testRefocusForwarderIOException() {

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeWithIOException(10, -1),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);

        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(0, num_notifications, num_notifications);
    }

    @Test
    public void testRefocusNoAuth() {

        ResultListSupplier responseSupplier = new ResultListSupplier(
                    new ArrayList<StubResult>(),
                    makeWithResponseCode(10, HttpStatus.SC_UNAUTHORIZED),
                    refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);

        int num_notifications = 5;
        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(0, num_notifications, num_notifications);
    }

    @Test
    public void testRefocusServiceUnavail() {

        ResultListSupplier responseSupplier = new ResultListSupplier(
                new ArrayList<StubResult>(),
                makeWithResponseCode(10, HttpStatus.SC_SERVICE_UNAVAILABLE),
                refocusForwarder);

        refocusForwarder.setStubSender(responseSupplier);

        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(0, num_notifications, num_notifications);
    }

    @Test
    public void testIntermittentThrottle() {

        // Assumes retries >= 3
        StubResult [] cycle = {
                makeTooManyRequests(10),
                makeTooManyRequests(10),
                make200(10, 1)
        };
        ResultCycleStubSupplier responseSupplier = new ResultCycleStubSupplier( cycle, refocusForwarder);
        refocusForwarder.setStubSender(responseSupplier);

        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(num_notifications, 0, num_notifications);
    }

    @Test
    public void testIntermittentInterruptedIOException() {

        // Assumes retries >= 3
        StubResult [] cycle = {
                makeWithInterruptedIOException(10, -1),
                makeWithInterruptedIOException(10, -1),
                make200(10, 1)
        };
        ResultCycleStubSupplier responseSupplier = new ResultCycleStubSupplier( cycle, refocusForwarder);
        refocusForwarder.setStubSender(responseSupplier);

        int num_notifications = 5;

        History history = mock(History.class);
        enqueueSamples(num_notifications, history);
        waitUntilQueuedNotificationsAreProcessed(refocusForwarder);

        logForwarderStats();
        verifyProcessed(num_notifications, 0, num_notifications);
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
