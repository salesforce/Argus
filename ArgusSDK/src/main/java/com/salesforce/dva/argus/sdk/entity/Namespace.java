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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The namespace object.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Namespace extends Entity {

    //~ Instance fields ******************************************************************************************************************************

    private String qualifier;
    private Set<String> usernames = new HashSet<String>();

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespace qualifier.
     *
     * @return  The namespace qualifier.
     */
    public String getQualifier() {
        return qualifier;
    }

    /**
     * Sets the namespace qualifier.
     *
     * @param  qualifier  The namespace qualifier.
     */
    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    /**
     * Returns the authorized users.
     *
     * @return  The authorized users.
     */
    public Set<String> getUsernames() {
        return usernames;
    }

    /**
     * Sets the authorized users.
     *
     * @param  usernames  The authorized users.
     */
    public void setUsernames(Set<String> usernames) {
        this.usernames = usernames;
    }

    /**
     * Adds an authorized user.
     *
     * @param  username  The authorized user to add.
     */
    public void addUsername(String username) {
        this.usernames.add(username);
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 67 * hash + super.hashCode();
        hash = 67 * hash + Objects.hashCode(this.qualifier);
        hash = 67 * hash + Objects.hashCode(this.usernames);
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

        final Namespace other = (Namespace) obj;

        if (!super.equals(other)) {
            return false;
        }
        if (!Objects.equals(this.qualifier, other.qualifier)) {
            return false;
        }
        if (!Objects.equals(this.usernames, other.usernames)) {
            return false;
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
