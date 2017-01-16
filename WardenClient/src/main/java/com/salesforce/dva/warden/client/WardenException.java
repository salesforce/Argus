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
package com.salesforce.dva.warden.client;

/**
 * Exception class for the Argus SDK.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
public class WardenException extends RuntimeException {

    //~ Instance fields ******************************************************************************************************************************

    private int _status;
    private String _message;
    private String _url;
    private String _json;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Constructs an instance of <code>ArgusServiceException</code> with the specified detail message.
     *
     * @param  msg  the detail message.
     */
    public WardenException(String msg) {
        super(msg);
    }

    /**
     * Creates a new WardenException object.
     *
     * @param  status   DOCUMENT ME!
     * @param  message  DOCUMENT ME!
     * @param  url      DOCUMENT ME!
     * @param  json     DOCUMENT ME!
     */
    public WardenException(int status, String message, String url, String json) {
        _status = status;
        _message = message;
        _url = url;
        _json = json;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public int getStatus() {
        return _status;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getMessage() {
        return _message;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getUrl() {
        return _url;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public String getJson() {
        return _json;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
