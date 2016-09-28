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
	 
package com.salesforce.dva.argus.ws.filter;

import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import org.slf4j.MDC;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Records the unique request ID.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class TxFilter implements Filter {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String TXID_ATTRIBUTE_NAME = "TXID";
    private static final AtomicLong TXID = new AtomicLong(0);

    //~ Methods **************************************************************************************************************************************

    @Override
    public void destroy() { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        long txId = TXID.compareAndSet(Long.MAX_VALUE, 0L) ? 0 : TXID.incrementAndGet();

        ArgusWebServletListener.getSystem().getUnitOfWork().begin();
        try {
            MDC.put(TXID_ATTRIBUTE_NAME, String.valueOf(txId));
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TXID_ATTRIBUTE_NAME);
            ArgusWebServletListener.getSystem().getUnitOfWork().end();
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
