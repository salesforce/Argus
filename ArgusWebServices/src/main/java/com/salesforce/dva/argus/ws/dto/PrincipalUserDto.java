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
	 
package com.salesforce.dva.argus.ws.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.PrincipalUser.Preference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * The principal user DTO.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrincipalUserDto extends EntityDTO {

    //~ Instance fields ******************************************************************************************************************************

    private String userName;
    private String email;
    private Map<Preference, String> preferences = new HashMap<>();
    private List<BigInteger> ownedDashboardIds = new ArrayList<>();
    private boolean privileged;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a user entity to DTO.
     *
     * @param   user  The entity to convert.
     *
     * @return  The DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static PrincipalUserDto transformToDto(PrincipalUser user) {
        if (user == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        PrincipalUserDto result = createDtoObject(PrincipalUserDto.class, user);

        for (Dashboard dashboard : user.getOwnedDashboards()) {
            result.addOwnedDashboardId(dashboard.getId());
        }
        return result;
    }

    /**
     * Converts list of alert entity objects to list of alertDto objects.
     *
     * @param   users  alerts List of alert entities. Cannot be null.
     *
     * @return  List of alertDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<PrincipalUserDto> transformToDto(List<PrincipalUser> users) {
        if (users == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<PrincipalUserDto> result = new ArrayList<>();

        for (PrincipalUser user : users) {
            result.add(transformToDto(user));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the user name.
     *
     * @return  The user name.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Sets the user name.
     *
     * @param  userName  The user name.
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Returns the email.
     *
     * @return  The email.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the email.
     *
     * @param  email  The email.
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the user preferences.
     *
     * @return  The user preferences.
     * 
     */
    public Map<Preference, String> getPreferences() {
        return preferences;
    }

    /**
     * Sets the user preferences.
     *
     * @param  preferences  The user preferences.
     */
    public void setPreferences(Map<Preference, String> preferences) {
        this.preferences = preferences;
    }

    /**
     * Returns the list of dashboards IDs owned by the user.
     *
     * @return  The list of dashboards IDs owned by the user.
     */
    public List<BigInteger> getOwnedDashboardIds() {
        return ownedDashboardIds;
    }

    /**
     * Adds a dashboard ID to the list of owned dashboards.
     *
     * @param  id  The dashboard ID.
     */
    public void addOwnedDashboardId(BigInteger id) {
        this.getOwnedDashboardIds().add(id);
    }

    /**
     * Indicates if the user has privileged access.
     *
     * @return  True if the user has privileged access.
     */
    public boolean isPrivileged() {
        return privileged;
    }

    /**
     * Specifies if the user has privileged access.
     *
     * @param  privileged  True if the user has privileged access.
     */
    public void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    @Override
    public Object createExample() {
        return null;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
