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
package com.salesforce.dva.argus.sdk.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The principal user object.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrincipalUser extends Entity {

    //~ Instance fields ******************************************************************************************************************************

    private String userName;
    private String email;
    private List<BigInteger> ownedDashboardIds = new ArrayList<>();
    private boolean privileged;

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
    public int hashCode() {
        int hash = 7;

        hash = 73 * hash + super.hashCode();
        hash = 73 * hash + Objects.hashCode(this.userName);
        hash = 73 * hash + Objects.hashCode(this.email);
        hash = 73 * hash + Objects.hashCode(this.ownedDashboardIds);
        hash = 73 * hash + (this.privileged ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final PrincipalUser other = (PrincipalUser) obj;

        if (!super.equals(other)) {
            return false;
        }
        if (this.privileged != other.privileged) {
            return false;
        }
        if (!Objects.equals(this.userName, other.userName)) {
            return false;
        }
        if (!Objects.equals(this.email, other.email)) {
            return false;
        }
        if (!Objects.equals(this.ownedDashboardIds, other.ownedDashboardIds)) {
            return false;
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
