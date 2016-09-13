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
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Date;
import java.util.Objects;

/**
 * Audit object.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Audit implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private BigInteger _id;
    private Date _createdDate;
    private String _message;
    private String _hostName;
    private BigInteger _entityId;

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the audit ID.
     *
     * @return  The audit ID.
     */
    public BigInteger getId() {
        return _id;
    }

    /**
     * Specifies the audit ID.
     *
     * @param  id  The audit ID.
     */
    public void setId(BigInteger id) {
        this._id = id;
    }

    /**
     * Returns the created date.
     *
     * @return  The created date.
     */
    public Date getCreatedDate() {
        return _createdDate == null ? null : new Date(_createdDate.getTime());
    }

    /**
     * Specifies the created date.
     *
     * @param  createdDate  The created date.
     */
    public void setCreatedDate(Date createdDate) {
        _createdDate = createdDate == null ? null : new Date(createdDate.getTime());
    }

    /**
     * Returns the audit message.
     *
     * @return  The message.
     */
    public String getMessage() {
        return _message;
    }

    /**
     * Specifies the audit message.
     *
     * @param  message  The message.
     */
    public void setMessage(String message) {
        this._message = message;
    }

    /**
     * Returns the host name.
     *
     * @return  The host name.
     */
    public String getHostName() {
        return _hostName;
    }

    /**
     * Specifies the host name.
     *
     * @param  hostName  The host name.
     */
    public void setHostName(String hostName) {
        this._hostName = hostName;
    }

    /**
     * Returns the entity ID.
     *
     * @return  The entity ID.
     */
    public BigInteger getEntityId() {
        return _entityId;
    }

    @Override
    public int hashCode() {
        int hash = 7;

        hash = 37 * hash + Objects.hashCode(this._id);
        hash = 37 * hash + Objects.hashCode(this._createdDate);
        hash = 37 * hash + Objects.hashCode(this._message);
        hash = 37 * hash + Objects.hashCode(this._hostName);
        hash = 37 * hash + Objects.hashCode(this._entityId);
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

        final Audit other = (Audit) obj;

        if (!Objects.equals(this._message, other._message)) {
            return false;
        }
        if (!Objects.equals(this._hostName, other._hostName)) {
            return false;
        }
        if (!Objects.equals(this._id, other._id)) {
            return false;
        }
        if (!Objects.equals(this._createdDate, other._createdDate)) {
            return false;
        }
        if (!Objects.equals(this._entityId, other._entityId)) {
            return false;
        }
        return true;
    }

    /**
     * Sets the entity ID for this audit item.
     *
     * @param  entityId  The entity ID.
     */
    public void setEntityId(BigInteger entityId) {
        _entityId = entityId;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
