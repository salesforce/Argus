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

package com.salesforce.dva.argus.service.alert.notifier;

import com.google.gson.JsonParser;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.RefocusService;
import com.salesforce.dva.argus.service.alert.notifier.RefocusProperty;
import com.salesforce.dva.argus.service.alert.notifier.NotificationForwarder;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

import org.apache.http.impl.client.*;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpStatus;


import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.System;

import javax.persistence.EntityManager;
import java.sql.Ref;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;


import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.InterruptedIOException;
import java.io.IOException;

/**
 * Implementation of notifier interface for notifying Refocus.
 *
 * @author  Ian Keck (ikeck@salesforce.com)
 */
// Future - rename to DefaultRefocusService
@Singleton
public class RefocusForwarder extends DefaultService implements RefocusService, NotificationForwarder {

	private static final Logger _logger = LoggerFactory.getLogger(RefocusForwarder.class);
    private static final Logger _logger2 = LoggerFactory.getLogger(RefocusService.class);

	private String endpoint;
	private String token;
	protected SystemConfiguration config;
	private boolean detailedLogging = true; // was false
	private boolean detailedHistory = true; // was false // TODO - deploy with true or false?
    private boolean mapToErrorLogging = false; // should be false!

	private LinkedBlockingQueue<RefocusSample> sampleQueue;

	private long max_send_interval_ms;
	private int max_samples_to_send;
	private long last_send_time;
	private long last_forwarder_status_time;
	private long forwarder_status_interval_ms;
	private long interval_for_rate_limit_ms;

	private AtomicInteger queuedCounter;
	private AtomicInteger deliveredCounter;
	private AtomicInteger discardedCounter;
	private int maxQueueLength;

	private Supplier<RefocusResponse> stub_sender;


	/**
	 * Creates a new Refocus Forwarder.
	 *
	 * @param  config             The system configuration. Cannot be null.
	 * @param  emf                The entity manager factory. Cannot be null.
	 */
	@Inject
	public RefocusForwarder(SystemConfiguration config, Provider<EntityManager> emf) {
		super(config);
		this.config = config;
        this.stub_sender = null;

		endpoint     = this.config.getValue(RefocusProperty.REFOCUS_ENDPOINT.getName(), RefocusProperty.REFOCUS_ENDPOINT.getDefaultValue());
		token        = this.config.getValue(RefocusProperty.REFOCUS_TOKEN.getName(),    RefocusProperty.REFOCUS_TOKEN.getDefaultValue());

		// TODO - catch parse errors and log
		max_samples_to_send  = Integer.parseInt(this.config.getValue(RefocusProperty.REFOCUS_MAX_BULK_ITEMS.getName(), RefocusProperty.REFOCUS_MAX_BULK_ITEMS.getDefaultValue()));
		max_send_interval_ms = Integer.parseInt(this.config.getValue(RefocusProperty.REFOCUS_SEND_INTERVAL_MS.getName(), RefocusProperty.REFOCUS_SEND_INTERVAL_MS.getDefaultValue()));
        forwarder_status_interval_ms = Integer.parseInt(this.config.getValue(RefocusProperty.REFOCUS_FORWARDER_STATUS_INTERVAL_MS.getName(), RefocusProperty.REFOCUS_FORWARDER_STATUS_INTERVAL_MS.getDefaultValue()));
        detailedHistory = Boolean.parseBoolean(this.config.getValue(RefocusProperty.REFOCUS_FORWARDING_HISTORY.getName(), RefocusProperty.REFOCUS_FORWARDING_HISTORY.getDefaultValue()));
        detailedLogging = Boolean.parseBoolean(this.config.getValue(RefocusProperty.REFOCUS_PER_NOTIFICATION_LOGGING.getName(), RefocusProperty.REFOCUS_PER_NOTIFICATION_LOGGING.getDefaultValue()));
        interval_for_rate_limit_ms = -1;




        this.queuedCounter    = new AtomicInteger(0);
		this.deliveredCounter = new AtomicInteger(0);
		this.discardedCounter = new AtomicInteger( 0);
		this.maxQueueLength   = 0;
		this.sampleQueue      = new LinkedBlockingQueue<RefocusSample>();
		this.last_send_time   = System.currentTimeMillis();
		this.last_forwarder_status_time = this.last_send_time;
	}


    // --------------------------------------------------------------------
    // GetServiceProperties
    // --------------------------------------------------------------------

    // TODO - shouldn't this return current values?
    @Override
    public Properties getServiceProperties() {
        Properties properties = new Properties();

        for(RefocusProperty property: RefocusProperty.values()){
            properties.put(property.getName(), property.getDefaultValue());
        }
        return properties;
    }

	// --------------------------------------------------------------------
    // RefocusService Interface
    // --------------------------------------------------------------------

	@Override
	public boolean sendRefocusNotification(String subject_aspect, String value, String user_id, String token_id, History history) {

        return sendRefocusNotification( subject_aspect, value, user_id, token_id, history, System.currentTimeMillis());
	}

    // FUTURE - pass in next fire time to handle expiration, and deprecate the previous method.
    @Override
    public boolean sendRefocusNotification(String subject_aspect, String value, String user_id, String token_id, History history, long nextFireTime)
    {
        if (!Boolean.valueOf(config.getValue(SystemConfiguration.Property.REFOCUS_ENABLED))) {
            _logger2.info("Refocus notification is disabled."); // was _info() // TODO - log sample?
            return false;
        }

        RefocusSample sample = new RefocusSample(subject_aspect, value, history, user_id, token_id, nextFireTime);
        try
        {
            this.sampleQueue.put(sample);
            this.queuedCounter.incrementAndGet();

            // String infoMsg = MessageFormat.format("Refocus Sample {0} enqueued.", sample.toJSON());
            String infoMsg = MessageFormat.format("Refocus Sample {0} enqueued by {1}.", sample.toJSON(), this.hashCode());
            _logger2.debug(infoMsg); // was _debug(infoMsg)
            history.appendMessageNUpdateHistory(infoMsg, null, 0);
        }
        catch (InterruptedException e)
        {
            String errMsg = MessageFormat.format("Refocus Sample {0} not enqueued. {1}", sample.toJSON(), e.getMessage());
            _logger2.error(errMsg); // _error(errMsg);
        }
        return true;
    }


    // --------------------------------------------------------------------
    // Notification Forwarder Interface
    // --------------------------------------------------------------------

    @Override
	public int forwardNotifications() throws InterruptedException {

		int count = 0;

        long forwardDuration = 0;
		long curDuration = System.currentTimeMillis() - this.last_send_time;
		int queueSize = this.sampleQueue.isEmpty() ? 0 : this.sampleQueue.size(); // probably redundant

        if (queueSize > maxQueueLength)
        {
            maxQueueLength = queueSize;
        }

        if (((queueSize > 0) && (curDuration >= this.max_send_interval_ms)) || (queueSize > this.max_samples_to_send))
		{
		    _info(MessageFormat.format("RefocusForwarder: forwarding {0} samples queued in {1}ms", queueSize, curDuration)); // DEBUG

            // FUTURE - write function to pull samples and filter expired notifications. (expired = nextFireTime + 1.5mins)
            Duration send_duration = new Duration();
			ArrayList<RefocusSample> samples = new ArrayList<RefocusSample>();
			count = this.sampleQueue.drainTo(samples, this.max_samples_to_send);

			_info(MessageFormat.format("RefocusForwarder: got {0} samples to forward.", samples.size()));  // DEBUG

			if (count > 0)
            {
                last_send_time = System.currentTimeMillis();
                Long jobId = sendMessage(samples);

                if (jobId != null)
                {
                    this.deliveredCounter.addAndGet(count);
                    String infoMsg = MessageFormat.format("RefocusForwarder: {0} samples forwarded. {1} total samples forwarded.", count, this.deliveredCounter.get());
                    _info(infoMsg);

                    // Future, with jobId, get the upsert status and log any errors!
                } else
                {
                    this.discardedCounter.addAndGet(count); // TODO - don't do this until we really have discarded them.
                    count = 0;

                    // IMPORTANT - to handle.  If we aren't successful. We can hold on to the notifications and retry later.  (Need a lastRequest)
                    String warnMsg = MessageFormat.format("RefocusForwarder: {0} samples dropped. {1} total samples dropped", count, this.discardedCounter.get());
                    _warn(warnMsg);

                }

                // Future - improve - for now, use this extremely crude method to rate limit Refocus requests to 500/second.
                forwardDuration = send_duration.duration();
            }
		}

        _logForwarderStats();
        _limitTo500PerSecond(forwardDuration);

		return count;
	}

    @Override
    public int getNotificationsEnqueued()
    {
        return this.queuedCounter.get();
    }

    @Override
    public int getNotificationsDelivered()
    {
        return this.deliveredCounter.get();
    }

    @Override
    public int getNotificationsDiscarded()
    {
        return this.discardedCounter.get();
    }

    @Override
    public int getNotificationsProcessed()
    {
        return getNotificationsDelivered() + getNotificationsDiscarded();
    }

    @Override
    public int getMaxQueueLength()
    {
        return this.maxQueueLength;
    }

    // --------------------------------------------------------------------
    // Implementation
    // --------------------------------------------------------------------

	// Future - remove. this is a crude hack to rate limit.  OK for now.
    private void _limitTo500PerSecond(long dur) throws InterruptedException
    {
        Duration d = new Duration();
        long sleep_ms = 0;

        if (interval_for_rate_limit_ms == -1)
        {
            // Sleep period for rate limiting requests per minute.  // FUTURE - this is a crude approach and should be removed.
            int max_refocus_requests_per_minute = Integer.parseInt(this.config.getValue(RefocusProperty.REFOCUS_MAX_REQUESTS_PER_MINUTE.getName(), RefocusProperty.REFOCUS_MAX_REQUESTS_PER_MINUTE.getDefaultValue()));
            if (max_refocus_requests_per_minute <= 0) {
                max_refocus_requests_per_minute = Integer.parseInt(RefocusProperty.REFOCUS_MAX_REQUESTS_PER_MINUTE.getDefaultValue());
            }
            // assumes sleep is quantized to 10ms intervals in practice. (verify!)
            interval_for_rate_limit_ms = (max_refocus_requests_per_minute > 6000)? 0 : 60000/max_refocus_requests_per_minute;
        }

        // Rate limit to REFOCUS_MAX_REQUESTS_PER_MINUTE sends/sec (default=500)
        if (interval_for_rate_limit_ms > 0 && dur < interval_for_rate_limit_ms)
        {
            sleep_ms = interval_for_rate_limit_ms - dur;
            Thread.sleep(sleep_ms);
        }
    }

    private void _logForwarderStats() throws InterruptedException
    {
        if (System.currentTimeMillis() - this.last_forwarder_status_time > forwarder_status_interval_ms)
        {
            _logger.info("RefocusForwarder: enqueued={} delivered={}, discarded={}, maxQueueLength={}",
                    getNotificationsEnqueued(), getNotificationsDelivered(), getNotificationsDiscarded(), getMaxQueueLength());
            this.last_forwarder_status_time = System.currentTimeMillis();
        }
    }

	private String notificationsToJSON(ArrayList<RefocusSample> notifications)
	{
	    JsonArray items = new JsonArray();
	    for (RefocusSample s: notifications)
        {
            JsonObject sample = new JsonObject();
            sample.addProperty("name", s.name);
            sample.addProperty("value", s.value);
            items.add(sample);
        }
        return items.toString();
	}

	private void addBody(HttpPost post, ArrayList<RefocusSample> notifications) throws Exception
    {
        String body = notificationsToJSON(notifications);
        StringEntity bodyEntity = new StringEntity(body);
        post.setEntity(bodyEntity);
        post.setHeader("Content-type", "application/json");
    }

    public void setStubSender( Supplier<RefocusResponse> stub)
    {
        this.stub_sender = stub;
    }


//    private void perItemLogging( ArrayList<RefocusSample> notifications, Consumer<String> f, String itemFormat)
//    {
//        // TODO - unwrap and rethrow InterruptedException from all functional iterators.
//        if (detailedLogging)
//        {
//            assert(StringUtils.isNotBlank(itemFormat));
//            notifications.forEach( x -> { f.accept(MessageFormat.format(itemFormat,x.toJSON())); });
//        }
//    }
//
//    private void perItemHistory( ArrayList<RefocusSample> notifications, String itemFormat)
//    {
//        // TODO - unwrap and rethrow InterruptedException from all functional iterators.
//        if (detailedHistory)
//        {
//            assert(StringUtils.isNotBlank(itemFormat));
//            String formatStr = itemFormat.replace("{0}", "%s");
//            notifications.forEach( x -> { x.history.appendMessageNUpdateHistory(String.format(formatStr, x.toJSON()), null, 0); } );
//        }
//    }


    private void perItemLoggingAndHistory( ArrayList<RefocusSample> notifications, Consumer<String> f, String itemFormat)
    {
        // TODO - unwrap and rethrow InterruptedException from all functional iterators.
        assert(StringUtils.isNotBlank(itemFormat));
        String formatStr = itemFormat.replace("{0}", "%s");

        notifications.forEach( x -> {
                if (detailedLogging) {
                    f.accept(MessageFormat.format(itemFormat, x.toJSON()));
                }
                if (detailedHistory) {
                    x.history.appendMessageNUpdateHistory(String.format(formatStr, x.toJSON()), null, 0);
                }
            });
    }


    private void closeResponse(CloseableHttpResponse response)
    {
        try {
            if (response != null)
            {
                response.close();
            }
        }
        catch(Exception e)
        {}
    }

    // This method exists to enable mocking of the client for unit testing.
    // It is public because mocking seems to require it.
    public CloseableHttpClient getHttpClient()
    {
        RefocusTransport refocusTransport = RefocusTransport.getInstance();
        CloseableHttpClient httpclient = refocusTransport.getHttpClient(config);
        return httpclient;
    }

    // FUTURE - IMPORTANT - the items are dequeued.  We don't want to lose them, we need logic to sleep and retry.
    // FUTURE - instead of the retry loop, add a retry handler as documented near the bottom of this page:
    // future - https://hc.apache.org/httpcomponents-client-4.5.x/tutorial/html/fundamentals.html
    // NOTE - An oddity of this code is that String.format() is used to generate a perItemMsgFormat with {N} notation used by MessageFormat.

    private Long sendMessage(ArrayList<RefocusSample> notifications) throws InterruptedException{
		String failureMsg = null;
		String perItemMsgFormat = null;
		int count = notifications.size();
        boolean success = false;
        CloseableHttpClient httpclient = null;
        Long refocusJobId = null;

		if (Boolean.valueOf(config.getValue(SystemConfiguration.Property.REFOCUS_ENABLED)) && count > 0) {

			int refreshMaxTimes = Integer.parseInt(config.getValue(RefocusProperty.REFOCUS_CONNECTION_REFRESH_MAX_TIMES.getName(), RefocusProperty.REFOCUS_CONNECTION_REFRESH_MAX_TIMES.getDefaultValue()));
			try {
				httpclient = getHttpClient();

				Duration duration = new Duration();

				HttpPost post = null;
                CloseableHttpResponse response = null;
                String responseBody = "";
				try {
					post = new HttpPost(String.format("%s/v1/samples/upsert/bulk", endpoint));
					post.addHeader("Authorization", token);
                    post.addHeader("Accept", "application/json");
					addBody(post, notifications);

                    for (int i = 0; !success && (i <= refreshMaxTimes); i++)
                    {
                        failureMsg = ""; // clear for retry
                        perItemMsgFormat = "";

                        if (i > 0)
                        {
                            _info("RefocusForwarder: Retrying Bulk Upsert");
                        }

                        int respCode = 0;
                        JsonObject parsedResponse = null;
                        Duration post_duration = new Duration();

                        try
                        {
                            // The stub sender is used to instrument the unit test
                            if (this.stub_sender != null)
                            {
                                try
                                {
                                    response = null; // There is no HTTP response to release
                                    RefocusResponse resp = this.stub_sender.get();
                                    respCode = resp.resp_code;
                                    parsedResponse = resp.result;
                                    if (parsedResponse != null)
                                    {
                                        responseBody = resp.result.toString();
                                    }
                                }
                                catch (RuntimeException ex)
                                {
                                    Throwable cause = ex.getCause();
                                    if (cause == null)
                                    {
                                        throw new IOException("unknown");
                                    }
                                    else if (cause instanceof InterruptedIOException)
                                    {
                                        throw (InterruptedIOException) cause;
                                    }
                                    else if (cause instanceof IOException)
                                    {
                                        throw (IOException) cause;
                                    }
                                    else
                                    {
                                        throw new IOException("unknown");
                                    }
                                }
                            } else
                            {
                                response = httpclient.execute(post);
                                respCode = response.getStatusLine().getStatusCode();
                                responseBody = new BasicResponseHandler().handleResponse(response);
                                parsedResponse = (new JsonParser()).parse(responseBody).getAsJsonObject(); // TODO exceptions?
                            }
                        }
                        catch (InterruptedIOException ex)
                        {
                            respCode = HttpStatus.SC_REQUEST_TIMEOUT;
                            responseBody = "";
                            parsedResponse = new JsonObject();
                        }
                        catch (IOException ex)
                        {
                            respCode = -1;
                            responseBody = String.format("{ \"respCode\": \"%d\", \"message\": \"%s: %s\" }", respCode, ex.getClass().getName(), ex.getMessage());
                            parsedResponse = (new JsonParser()).parse(responseBody).getAsJsonObject();
                        }
                        finally
                        {
                            post_duration.duration();
                        }

                        duration.duration();

                        // Check for success
                        // TODO - allow all 2XXs to be success?
                        if (respCode == HttpStatus.SC_OK || respCode == HttpStatus.SC_CREATED || respCode == HttpStatus.SC_NO_CONTENT)
                        {
                            String upsertStatus = parsedResponse.get("status").getAsString();
                            refocusJobId = parsedResponse.get("jobId").getAsLong();

                            String infoMsg = MessageFormat.format("Refocus Forwarder: Refocus Job ID: {0}. {1} samples sent in {2}ms (post = {3}ms). ",
                                    refocusJobId, count, duration.duration(), post_duration.duration());
                            _info(infoMsg);
                            perItemMsgFormat = "Refocus sample {0} sent.";

                            success = true;
                            break;

                        } else if (respCode == HttpStatus.SC_REQUEST_TIMEOUT)
                        {
                            // Indication that the session timedout, Need to refresh and retry
                            failureMsg = MessageFormat.format("Refocus Forwarder: Failed to forward {0} samples due to session time out.", count);
                            _warn(failureMsg);
                            perItemMsgFormat = "Failed to forward {0} due to session time out.";

                        } else if (respCode == 429)
                        {
                            // Indication that Refocus is throttling.  Need to wait and retry.
                            String warnMsg = MessageFormat.format("Refocus Forwarder: Refocus is Throttling ({0}) at {1}", respCode, System.currentTimeMillis());
                            _error(warnMsg);
                            perItemMsgFormat = "Failed to forward {0} due to refocus throttling.";

                            // FUTURE - get the time to retry at from the 429 response body.
                            if (i <= refreshMaxTimes - 1)
                            {
                                if (interval_for_rate_limit_ms > 0)
                                {
                                    Thread.sleep(interval_for_rate_limit_ms);
                                }
                            }

                        } else if (respCode == HttpStatus.SC_BAD_GATEWAY || respCode == HttpStatus.SC_SERVICE_UNAVAILABLE || respCode == HttpStatus.SC_GATEWAY_TIMEOUT)
                        {
                            // Indication that the session timedout, Need to refresh and retry
                            failureMsg = MessageFormat.format("Refocus Forwarder: Failed to forward {0} samples due to http error {1}", count, respCode );
                            _error(failureMsg);
                            perItemMsgFormat = String.format("Failed to forward %s due to http error %d.", "{0}", respCode);

                        } else {
                            failureMsg = MessageFormat.format("Refocus Forwarder: Failed to forward {0} samples. Response code {1} response:\n{2}",
									count, respCode, responseBody);
							_error(failureMsg);
							perItemMsgFormat = String.format("Failed to forward %s Refocus samples. Response code %d response: %s", "{0}", respCode, responseBody);
							break; // no retry
						}

						closeResponse(response);
						response = null;
					} // for

				} catch (RuntimeException e)
                {
                   throw e;
                }
				catch (Exception e) {
					failureMsg = MessageFormat.format("Refocus Forwarder: Failed to forward {0} samples. Exception {1}\n{2}",
							count, e.getMessage(), stackTraceToString(e));
					_error(failureMsg);
					perItemMsgFormat = String.format("Failed to forward %s. Exception: %s", "{0}", e.getMessage());

				} finally {

                    closeResponse(response);
                    response = null;

                    if (post != null) {
                        post.releaseConnection();
                    }
				}

			} catch (RuntimeException ex) {

				failureMsg = MessageFormat.format("Refocus Forwarder: Failed to forward {0} samples. Exception {1}\n{2}",
						count, ex.getMessage(), stackTraceToString(ex));
				_error(failureMsg);

				perItemMsgFormat = String.format("Failed to forward %s. Runtime exception: %s", "{0}", ex.getMessage());
				perItemLoggingAndHistory(notifications, (x) -> _warn(x), perItemMsgFormat);

				// Unwrap and throw InterruptedException if necessary
				Throwable cause = ex.getCause();
				if (cause != null && cause instanceof InterruptedException)
                {
                    throw (InterruptedException) cause;
                }

				throw new SystemException("Failed to forward Refocus notification.", ex);
			}
			// NOTE - don't close the client here.

		} else {
			failureMsg = "RefocusForwarder: Refocus notification is disabled.";
            perItemMsgFormat = String.format("Unable to send %s. Refocus notification is disabled.", "{0}");
            _info(failureMsg);
            refocusJobId = null;
		}

		// Per Item logging if appropriate
        perItemLoggingAndHistory(notifications, success? (x) -> _info(x) : (x) -> _warn(x), perItemMsgFormat);

		return refocusJobId;
	}


    /**
     * RefocusSample object to generate JSON.
     */
    class RefocusSample {

        public static final String ASPECT_NAME_FIELD = "name";
        public static final String ASPECT_VALUE_FIELD = "value";
        public static final String USER_FIELD = "user";
        public static final String TOKEN_ID_FIELD = "token_id";

        public final String name;
        public final String value;

        public final String user_name;
        public final String token_id;
        public final History history;
        public final long   next_fire_time;

        public RefocusSample(final String name, final String value, final History history, final String user_name, final String token_id, final long next_fire_time) {
            this.name = name;
            this.value = value;
            this.user_name = user_name;
            this.token_id = token_id;
            this.history = history;
            this.next_fire_time = next_fire_time;
        }

        /**
         * Convert data to a JSON string.
         *
         * @return  JSON string
         */
        public String toJSON(boolean terse) {
            JsonObject sampleData = new JsonObject();

            sampleData.addProperty(ASPECT_NAME_FIELD, name);
            sampleData.addProperty(ASPECT_VALUE_FIELD, value);

            if (!terse){
                sampleData.addProperty(USER_FIELD, user_name);
                sampleData.addProperty(TOKEN_ID_FIELD, token_id);
            }
            return sampleData.toString();
        }

        public String toJSON() {
            return toJSON(false);
        }
    }


    // =================================================================================
    // Support for injecting responses
    // =================================================================================

    public RefocusResponse makeResponse(int resp, JsonObject result)
    {
        return new RefocusResponse(resp, result);
    }

    public static class RefocusResponse {
        public int         resp_code;
        public JsonObject  result;

        RefocusResponse(int resp_code, JsonObject result)
        {
            this.resp_code = resp_code;
            this.result = result;
        }
    }

    // =================================================================================
    // Utility Stuff
    // =================================================================================

    // TODO - move to an Argus-wide utility class.
    public static class Duration {
        long start;
        long end;

        public Duration() {
            start = System.currentTimeMillis();
            end = 0;
        }

        public Duration(long start_time)
        {
            start = start_time;
            end   = 0;
        }

        public Duration(Duration d)
        {
            this.start = d.start;
            this.end   = d.end;
        }

        public void start() {
            start = System.currentTimeMillis();
            end = 0;
        }

        public long duration() {
            if (end == 0)
            {
                end = System.currentTimeMillis();
            }
            return end - start;
        }

        public long cur_duration() {
            long now = System.currentTimeMillis();
            return now - start;
        }
    }


    // =========================================================================================
    // Syntactic sugar
    // Note: these exist because I haven't found a mechanism for setting the log level programmatically.

    private void _info(String msg)
    {
        if (mapToErrorLogging) {
            _logger.error(msg);
        }
        else {
            _logger.info(msg);
        }
    }

    private void _error(String msg)
    {
        _logger.error(msg);
    }

    private void _warn(String msg)
    {
        if (mapToErrorLogging) {
            _logger.error(msg);
        }
        else {
            _logger.warn(msg);
        }
    }

    private void _debug(String msg)
    {
        if (mapToErrorLogging) {
            _logger.error(msg);
        }
        else {
            _logger.debug(msg);
        }
    }

    public static String stackTraceToString(Throwable e)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String sStackTrace = sw.toString(); // stack trace as a string
        return sStackTrace;
    }

}
/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */
