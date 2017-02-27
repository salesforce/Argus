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

import java.io.IOException;

import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuthService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.ws.dto.PrincipalUserDto;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.slf4j.MDC;

/**
 * Enforces authentication requirements.<br />
 * If you're in a secure environment and wants to get rid of the login/logout procedure, automated authentication
 * can be achieved by setting 'service.config.auth.auto.login=true' in your argus.properties.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class AuthFilter implements Filter {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The session attribute name to store the authenticated user. */
    public static final String USER_ATTRIBUTE_NAME = "USER";

    //~ Instance fields ******************************************************************************************************************************
    private final SystemMain system = ArgusWebServletListener.getSystem();
    private final AuthService authService = system.getServiceFactory().getAuthService();

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
            boolean autoLogin = Boolean.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.AUTH_FILTER_AUTO_LOGIN));
            HttpServletRequest httpServletRequest = HttpServletRequest.class.cast(request);
            HttpSession httpSession = httpServletRequest.getSession(true);
            Object remoteUser = httpSession.getAttribute(USER_ATTRIBUTE_NAME);

            // If automated login configured and currently no principalUser associated with HttpSession,
            // assign principalUser to this httpSession.
            if(autoLogin && remoteUser == null) {
                String loginUser = String.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.AUTH_FILTER_AUTO_LOGIN_USER));
                String loginPwd = String.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.AUTH_FILTER_AUTO_LOGIN_PWD));
                PrincipalUser principalUser = authService.getUser(loginUser, loginPwd);
                PrincipalUserDto principalUserDto = PrincipalUserDto.transformToDto(principalUser);
                httpServletRequest.getSession(true).setAttribute(AuthFilter.USER_ATTRIBUTE_NAME, principalUserDto);
                user = principalUserDto.getUserName();
            }
            // If it's not an HTTP OPTION request or login/logout request and no principalUser is associated
            // with HttpSession, then return SC_UNAUTHORIZED
            else if (!"options".equalsIgnoreCase(httpServletRequest.getMethod()) && !_isAuthEndpoint(httpServletRequest) && remoteUser == null) {
                HttpServletResponse httpResponse = HttpServletResponse.class.cast(response);
                httpResponse.setHeader("Access-Control-Allow-Origin", httpServletRequest.getHeader("Origin"));
                httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } else if (remoteUser != null) {
                user = PrincipalUserDto.class.cast(httpSession.getAttribute(USER_ATTRIBUTE_NAME)).getUserName();
            }
        }
        try {
            MDC.put(USER_ATTRIBUTE_NAME, user);
            chain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ATTRIBUTE_NAME);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    private boolean _isAuthEndpoint(HttpServletRequest req) {
        String path = req.getRequestURI();
        String contextPath = req.getContextPath();

        return path.startsWith(contextPath + "/auth") || path.endsWith("/help");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
