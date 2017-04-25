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

import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuthService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.salesforce.dva.argus.ws.dto.PrincipalUserDto;
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
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.HttpHeaders;

/**
 * Enforces authentication requirements.<br />
 * If you're in a secure environment and wants to get rid of the login/logout procedure, automated authentication
 * can be achieved by setting 'service.config.auth.auto.login=true' in your argus.properties.
 *
 * @author Tom Valine (tvaline@salesforce.com)
 * @author Bhinav Sura (bhinav.sura@salesforce.com)
 * @author Axel Koehler (axel.koehler@kiwigrid.com)
 */
public class AuthFilter implements Filter {

	//~ Static fields/initializers *******************************************************************************************************************

	/**
	 * The session attribute name to store the authenticated user.
	 */
	public static final String USER_ATTRIBUTE_NAME = "USER";
	private static final String SESSION_ATTRIBUTE_NAME = "SESSION";

	//~ Instance fields ******************************************************************************************************************************
	private final SystemMain system = ArgusWebServletListener.getSystem();
	private final AuthService authService = system.getServiceFactory().getAuthService();

	//~ Methods **************************************************************************************************************************************

	@Override
	public void destroy() {
	}

	/**
	 * Authenticates a user if required.
	 *
	 * @param request The HTTP request.
	 * @param response The HTTP response.
	 * @param chain The filter chain to execute.
	 * @throws IOException If an I/O error occurs.
	 * @throws ServletException If an unknown error occurs.
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException
	{
		String user = null;
		String sessionId = null;

		if (HttpServletRequest.class.isAssignableFrom(request.getClass())) {
			HttpServletRequest httpServletRequest = HttpServletRequest.class.cast(request);
			String authorizationHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);

			if (authorizationHeader == null && !_isTokenAuthEndpoint(httpServletRequest)) {
				user = sessionBasedAuth(response, httpServletRequest);
			} else {
				user = tokenBasedAuth(response, httpServletRequest);
			}
		}

		try {
			MDC.put(USER_ATTRIBUTE_NAME, user);
			MDC.put(SESSION_ATTRIBUTE_NAME, sessionId);
			request.setAttribute(USER_ATTRIBUTE_NAME, user);
			chain.doFilter(request, response);
		} finally {
			MDC.remove(USER_ATTRIBUTE_NAME);
			MDC.remove(SESSION_ATTRIBUTE_NAME);
		}
	}

	private String sessionBasedAuth(ServletResponse response, HttpServletRequest httpServletRequest)
			throws IOException
	{
		LoggerFactory.getLogger(getClass()).info("Using Session Based Auth...");

		boolean autoLogin = Boolean.valueOf(system.getConfiguration()
				.getValue(SystemConfiguration.Property.AUTH_FILTER_AUTO_LOGIN));
		HttpSession httpSession = httpServletRequest.getSession(true);
		Object remoteUser = httpSession.getAttribute(USER_ATTRIBUTE_NAME);

		// If automated login configured and currently no principalUser associated with HttpSession,
		// assign principalUser to this httpSession.
		if (autoLogin && remoteUser == null) {
			String loginUser = String.valueOf(system.getConfiguration()
					.getValue(SystemConfiguration.Property.AUTH_FILTER_AUTO_LOGIN_USER));
			LoggerFactory.getLogger(getClass()).info("Automated login as user {} active...", loginUser);
			String loginPwd = String.valueOf(system.getConfiguration()
					.getValue(SystemConfiguration.Property.AUTH_FILTER_AUTO_LOGIN_PWD));
			PrincipalUser principalUser = authService.getUser(loginUser, loginPwd);
			PrincipalUserDto principalUserDto = PrincipalUserDto.transformToDto(principalUser);
			httpServletRequest.getSession(true).setAttribute(AuthFilter.USER_ATTRIBUTE_NAME, principalUserDto);
			return principalUserDto.getUserName();
		}

		// If it's not an HTTP OPTION request or login/logout request and no principalUser is associated
		// with HttpSession, then return SC_UNAUTHORIZED
		if (!"options".equalsIgnoreCase(httpServletRequest.getMethod())
				&& !_isAuthEndpoint(httpServletRequest)
				&& remoteUser == null)
		{
			sendUnauthorizedError(response, httpServletRequest, StringUtils.EMPTY);
		}
		if (remoteUser != null) {
			return PrincipalUserDto.class.cast(remoteUser).getUserName();
		}

		return null;
	}

	private String tokenBasedAuth(ServletResponse response, HttpServletRequest httpServletRequest) throws IOException {
		LoggerFactory.getLogger(getClass()).info("Using Token Based Auth...");

		String authorizationHeader = httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION);
		if (!"options".equalsIgnoreCase(httpServletRequest.getMethod()) && !_isAuthEndpoint(httpServletRequest)) {
			if (!authorizationHeader.startsWith("Bearer ")) {
				sendUnauthorizedError(response, httpServletRequest, HttpHeaders.AUTHORIZATION +
						" Header is either not provided or incorrectly formatted.");
			} else {
				try {
					String jwt = authorizationHeader.substring("Bearer ".length()).trim();
					return JWTUtils.validateTokenAndGetSubj(jwt, JWTUtils.TokenType.ACCESS);
				} catch (UnsupportedJwtException | MalformedJwtException | IllegalArgumentException e) {
					sendUnauthorizedError(response,
							httpServletRequest,
							"Unsupported or Malformed JWT. Please provide a valid JWT.");
				} catch (SignatureException e) {
					sendUnauthorizedError(response,
							httpServletRequest,
							"Signature Exception. Please provide a valid JWT.");
				} catch (ExpiredJwtException e) {
					sendUnauthorizedError(response, httpServletRequest, "JWT has expired. Please obtain a new token.");
				}
			}
		}
		return null;
	}

	private void sendUnauthorizedError(ServletResponse response, HttpServletRequest httpServletRequest, String msg)
			throws IOException
	{
		HttpServletResponse httpResponse = HttpServletResponse.class.cast(response);
		httpResponse.setHeader("Access-Control-Allow-Origin", httpServletRequest.getHeader("Origin"));
		httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
		httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, msg);
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {
	}

	private boolean _isAuthEndpoint(HttpServletRequest req) {
		String path = req.getRequestURI();
		String contextPath = req.getContextPath();

		return path.startsWith(contextPath + "/auth") || path.startsWith(contextPath + "/v2/auth") || path.endsWith(
				"/help");
	}

	private boolean _isTokenAuthEndpoint(HttpServletRequest req) {
		String path = req.getRequestURI();
		String contextPath = req.getContextPath();

		return path.startsWith(contextPath + "/v2/auth") || path.endsWith("/help");
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
