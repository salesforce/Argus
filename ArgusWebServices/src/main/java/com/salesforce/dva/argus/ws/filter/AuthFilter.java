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

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import org.slf4j.MDC;

import com.salesforce.dva.argus.ws.resources.JWTUtils;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;

/**
 * Enforces authentication requirements.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class AuthFilter implements Filter {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The session attribute name to store the authenticated user. */
    public static final String USER_ATTRIBUTE_NAME = "USER";

    //~ Methods **************************************************************************************************************************************

    @Override
    public void destroy() { }

    /**
     * Authenticates a user if required.
     *
     * @param   request   The HTTP request.
     * @param   response  The HTTP response.
     * @param   chain     The filter chain to execute.
     *
     * @throws  IOException       If an I/O error occurs.
     * @throws  ServletException  If an unknown error occurs.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        String user = null;

        if (HttpServletRequest.class.isAssignableFrom(request.getClass())) {
            
        	HttpServletRequest req = HttpServletRequest.class.cast(request);
            String authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION);
            
            //Only perform authentication check for endpoints that require it and if the req is not an OPTIONS request.
            if(_requiresAuthentication(req) && !"options".equalsIgnoreCase(req.getMethod())) {
            	if(authorizationHeader != null && authorizationHeader.startsWith("Bearer")) {
            		try {
                    	String jwt = authorizationHeader.substring("Bearer ".length()).trim();
                    	user = JWTUtils.validateTokenAndGetSubj(jwt, JWTUtils.TokenType.ACCESS);
                    } catch(UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
                    	HttpServletResponse httpresponse = HttpServletResponse.class.cast(response);
                    	httpresponse.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
                    	httpresponse.setHeader("Access-Control-Allow-Credentials", "true");
                    	httpresponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unsupported or Malformed JWT. Please provide a valid JWT.");
                    } catch(SignatureException e) {
                    	HttpServletResponse httpresponse = HttpServletResponse.class.cast(response);
                    	httpresponse.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
                    	httpresponse.setHeader("Access-Control-Allow-Credentials", "true");
                    	httpresponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Signature Exception. Please provide a valid JWT.");
                    } catch(ExpiredJwtException e) {
                    	HttpServletResponse httpresponse = HttpServletResponse.class.cast(response);
                    	httpresponse.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
                    	httpresponse.setHeader("Access-Control-Allow-Credentials", "true");
                    	httpresponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT has expired. Please obtain a new token.");
                    }
            	} else {
            		HttpServletResponse httpresponse = HttpServletResponse.class.cast(response);
                	httpresponse.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
                	httpresponse.setHeader("Access-Control-Allow-Credentials", "true");
                	httpresponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, HttpHeaders.AUTHORIZATION + 
                			" Header is either not proivded or incorrectly formatted.");
            	}
            }
        }
        
        try {
            MDC.put(USER_ATTRIBUTE_NAME, user);
            request.setAttribute(USER_ATTRIBUTE_NAME, user);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ATTRIBUTE_NAME);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    private boolean _requiresAuthentication(HttpServletRequest req) {
        String path = req.getRequestURI();
        String contextPath = req.getContextPath();
        
        return !path.startsWith(contextPath + "/v2/auth")
                && !path.startsWith(contextPath + "/auth")
                && !path.endsWith("/help")
                && !path.startsWith(contextPath + "/v1.0/oauth")
                ;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
