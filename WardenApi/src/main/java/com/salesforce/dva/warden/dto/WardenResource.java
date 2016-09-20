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
package com.salesforce.dva.warden.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.EnumMap;
import java.util.Objects;

/**
 * DOCUMENT ME!
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class WardenResource<T> {

    //~ Instance fields ******************************************************************************************************************************

    private T entity;
    private EnumMap<MetaKey, String> meta;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new WardenResource object. */
    public WardenResource() { }

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @param  entity  DOCUMENT ME!
     */
    public void setEntity(T entity) {
        this.entity = entity;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  meta  DOCUMENT ME!
     */
    public void setMeta(EnumMap<MetaKey, String> meta) {
        this.meta = meta;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({ @JsonSubTypes.Type(Policy.class), @JsonSubTypes.Type(Infraction.class), @JsonSubTypes.Type(SuspensionLevel.class) })
    public T getEntity() {
        return entity;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public EnumMap<MetaKey, String> getMeta() {
        return meta;
    }

    @Override
    public int hashCode() {
        int hash = 3;

        hash = 89 * hash + Objects.hashCode(this.entity);
        hash = 89 * hash + Objects.hashCode(this.meta);
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

        final WardenResource<?> other = (WardenResource<?>) obj;

        if (!Objects.equals(this.entity, other.entity)) {
            return false;
        }
        if (!Objects.equals(this.meta, other.meta)) {
            return false;
        }
        return true;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum MetaKey {

        HREF,
        STATUS,
        VERB,
        MESSAGE,
        UI_MESSAGE,
        DEV_MESSAGE
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */