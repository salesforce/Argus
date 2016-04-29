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
	 
package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.ws.dto.EndpointHelpDto;
import com.salesforce.dva.argus.ws.dto.MethodHelpDto;
import com.salesforce.dva.argus.ws.dto.PrincipalUserDto;
import com.salesforce.dva.argus.ws.filter.AuthFilter;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import org.apache.commons.beanutils.BeanUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Abstract base class for web service resource.
 *
 * <p>Subclasses should implement the help() method.</p>
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public abstract class AbstractResource {

	//~ Instance fields ******************************************************************************************************************************

    protected final SystemMain system = ArgusWebServletListener.getSystem();
    protected UserService userService = system.getServiceFactory().getUserService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Generates a list of endpoint help DTOs used to describe the major service endpoints.
     *
     * @param   resourceClasses  The resource classes to describe.
     *
     * @return  The list of endpoint help DTOs.
     */
    protected static List<EndpointHelpDto> describeEndpoints(List<Class<? extends AbstractResource>> resourceClasses) {
        List<EndpointHelpDto> result = new LinkedList<>();

        if (resourceClasses != null && !resourceClasses.isEmpty()) {
            for (Class<? extends AbstractResource> resourceClass : resourceClasses) {
                EndpointHelpDto dto = EndpointHelpDto.fromResourceClass(resourceClass);

                if (dto != null) {
                    result.add(dto);
                }
            }
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the logged in user object.
     *
     * @param   req  The HTTP request.
     *
     * @return  The logged in user object.
     */
    public PrincipalUser getRemoteUser(HttpServletRequest req) {
        requireArgument(req != null, "Request cannot be null.");

        PrincipalUser result = null;
        Object principalAttribute = req.getSession(true).getAttribute(AuthFilter.USER_ATTRIBUTE_NAME);

        if (principalAttribute != null) {
            PrincipalUserDto user = PrincipalUserDto.class.cast(principalAttribute);

            result = userService.findUserByUsername(user.getUserName());
        }
        return result;
    }

    /**
     * Returns the help for the endpoint.  For the context root, it will return the endpoint help for all major endpoints.  For a specific endpoint
     * it will return the method help for the endpoint.
     *
     * @return  Help object describing the service in JSON format.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/help")
    public Map<String, List<? extends Object>> help() {
        Map<String, List<?>> result = new LinkedHashMap<>();
        List<EndpointHelpDto> endpoints = describeEndpoints(getEndpoints());

        if (endpoints != null && !endpoints.isEmpty()) {
            result.put("endpoints", endpoints);
        }

        List<MethodHelpDto> methods = describeMethods();

        if (methods != null && !methods.isEmpty()) {
            result.put("methods", methods);
        }
        return result;
    }

    /**
     * Overridden by the context root to describe the available endpoints.  Specific service endpoints should always return null as they will 
     * only make available the method help.
     *
     * @return  The list of endpoints for which to make help information available.
     */
    public List<Class<? extends AbstractResource>> getEndpoints() {
        return null;
    }

    /**
     * Generates help DTOs for each method on the service interface.  The root context shall return a null list.  All other endpoints will re-use
     * this implementation.
     *
     * @return  The service endpoint method help objects.
     */
    protected List<MethodHelpDto> describeMethods() {
        List<MethodHelpDto> result = new LinkedList<>();
        Path endpointPath = getClass().getAnnotation(Path.class);

        for (Method method : getClass().getDeclaredMethods()) {
            String parentPath = endpointPath == null ? null : endpointPath.value();
            MethodHelpDto methodHelpDto = MethodHelpDto.fromMethodClass(parentPath, method);

            if (methodHelpDto != null) {
                result.add(methodHelpDto);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Validates the owner name and returns the owner object.
     *
     * @param   req        The HTTP request.
     * @param   ownerName  Name of the owner. It is optional.
     *
     * @return  The owner object
     *
     * @throws  WebApplicationException  Throws exception if owner name does not exist.
     */
    protected PrincipalUser validateAndGetOwner(HttpServletRequest req, String ownerName) {
        PrincipalUser remoteUser = getRemoteUser(req);

        if (ownerName == null || ownerName.isEmpty()) {
            return remoteUser;
        } else if (ownerName.equalsIgnoreCase(remoteUser.getUserName())) {
            return remoteUser;
        } else if (remoteUser.isPrivileged()) {
            PrincipalUser owner;

            owner = userService.findUserByUsername(ownerName);
            if (owner == null) {
                throw new WebApplicationException(ownerName + ": User does not exist.", Status.NOT_FOUND);
            } else {
                return owner;
            }
        }
        throw new WebApplicationException(Status.FORBIDDEN.getReasonPhrase(), Status.FORBIDDEN);
    }

    /**
     * Validates the resource authorization. Throws exception if the user is not authorized to access the resource.
     *
     * @param   req           The HTTP request.
     * @param   actualOwner   The owner of the resource.
     * @param   currentOwner  The logged in user.
     *
     * @throws  WebApplicationException  Throws exception if user is not authorized to access the resource.
     */
    protected void validateResourceAuthorization(HttpServletRequest req, PrincipalUser actualOwner, PrincipalUser currentOwner) {
        if (!getRemoteUser(req).isPrivileged() && !actualOwner.equals(currentOwner)) {
            throw new WebApplicationException(Status.FORBIDDEN.getReasonPhrase(), Status.FORBIDDEN);
        }
    }

    /**
     * Validates that the user making the request is a privileged user.
     *
     * @param   req  - Http Request
     *
     * @throws  WebApplicationException  Throws exception if user is not a privileged user.
     */
    protected void validatePrivilegedUser(HttpServletRequest req) {
        if (!getRemoteUser(req).isPrivileged()) {
            throw new WebApplicationException(Status.FORBIDDEN.getReasonPhrase(), Status.FORBIDDEN);
        }
    }

    /**
     * Copies properties.
     *
     * @param   dest    The object to which the properties will be copied.
     * @param   source  The object whose properties are copied
     *
     * @throws  WebApplicationException  Throws exception if beanutils encounter a problem.
     */
    protected void copyProperties(Object dest, Object source) {
        try {
            BeanUtils.copyProperties(dest, source);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new WebApplicationException(e.toString(), Status.BAD_REQUEST);
        }
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Enumerates the supported HTTP methods.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum HttpMethod {

        GET,
        POST,
        PUT,
        DELETE,
        HEAD,
        OPTIONS;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
