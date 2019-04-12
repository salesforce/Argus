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

/**
 * Enumerates implementation specific configuration properties.
 *
 *  @author  Ian Keck (ikeck@salesforce.com)
 */
public enum RefocusProperty {

    /** The Refocus endpoint. */
    REFOCUS_ENDPOINT("notifier.property.refocus.endpoint", "https://test.refocus.com"),
    /** The Refocus access token. */
    REFOCUS_TOKEN("notifier.property.refocus.token", "test-token"),
    /** The Refocus proxy host. */
    // QUESTION - should these be refocus specific?
    REFOCUS_PROXY_HOST("notifier.property.proxy.host", ""),
    /** The Refocus port. */
    REFOCUS_PROXY_PORT("notifier.property.proxy.port", ""),
    /** The Refocus connection refresh max times. */
    REFOCUS_CONNECTION_REFRESH_MAX_TIMES("notifier.property.refocus.refreshMaxTimes", "3"),
    /** The Refocus forwarder bulk size **/
    REFOCUS_MAX_BULK_ITEMS("notifier.property.refocus.bulk.max_samples", "2000"),
    /** The Refocus forwarder push interval **/
    REFOCUS_SEND_INTERVAL_MS("notifier.property.refocus.bulk.send_interval_ms", "10000"),
    /** The Limit on requests per minute **/
    REFOCUS_MAX_REQUESTS_PER_MINUTE("notifier.property.refocus.maxRequestsPerMinute", "500"),
    /** Configuration for a custom keystore for dev box testing **/
    // TODO - document how to use this!
    REFOCUS_CUSTOM_KEYSTORE_PATH("notifier.property.refocus.keystorePath", ""),
    REFOCUS_CUSTOM_KEYSTORE_PASSWORD("notifier.property.refocus.keystorePassword", ""),
    REFOCUS_CUSTOM_KEYSTORE_KEY_PASSWORD("notifier.property.refocus.keyPassword", ""),

    /** Logging Levels **/
    REFOCUS_FORWARDING_HISTORY("notifier.property.refocus.forwardingHistory", "false"),
    REFOCUS_PER_NOTIFICATION_LOGGING("notifier.property.refocus.detailedNotificationLogging", "false"),
    REFOCUS_FORWARDER_STATUS_INTERVAL_MS("notifier.property.refocus.statusIntervalMs", "10000");



    private final String _name;
    private final String _defaultValue;

    private RefocusProperty(String name, String defaultValue) {
        _name = name;
        _defaultValue = defaultValue;
    }

    /**
     * Returns the property name.
     *
     * @return  The property name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Returns the default property value.
     *
     * @return  The default property value.
     */
    public String getDefaultValue() {
        return _defaultValue;
    }
}

/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */
