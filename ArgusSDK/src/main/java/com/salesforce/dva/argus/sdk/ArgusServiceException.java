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
package com.salesforce.dva.argus.sdk;

/**
 * Exception class for the Argus SDK.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class ArgusServiceException extends RuntimeException {

    //~ Instance fields ******************************************************************************************************************************

    private final int _status;
    private final String _url;
    private final String _json;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ArgusServiceException object.
     *
     * @param  status   The corresponding HTTP status code.
     * @param  message  The exception message.
     * @param  url      The request URL.
     * @param  json     The JSON request payload.
     */
    ArgusServiceException(int status, String message, String url, String json) {
        super(message);
        _status = status;
        _url = url;
        _json = json;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the status code.
     *
     * @return  The status code.
     */
    public int getStatus() {
        return _status;
    }

    /**
     * Returns the request URL.
     *
     * @return  The request URL.
     */
    public String getUrl() {
        return _url;
    }

    /**
     * Returns the JSON payload.
     *
     * @return  The JSON payload.
     */
    public String getJson() {
        return _json;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
