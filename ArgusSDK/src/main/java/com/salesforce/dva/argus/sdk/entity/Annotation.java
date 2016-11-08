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

import java.util.Map;
import java.util.Objects;

/**
 * Annotation object.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Annotation extends TSDBEntity {

    //~ Instance fields ******************************************************************************************************************************

    private String source;
    private String id;
    private Long timestamp;
    private String type;
    private Map<String, String> fields;

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the annotation source.
     *
     * @return  The annotation source.
     */
    public String getSource() {
        return source;
    }

    /**
     * Specifies the annotation source.
     *
     * @param  source  The annotation source.
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Returns the annotation ID.
     *
     * @return  The annotation ID.
     */
    public String getId() {
        return id;
    }

    /**
     * Specifies the annotation ID.
     *
     * @param  id  The annotation ID.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the annotation time stamp.
     *
     * @return  The annotation time stamp.
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Specifies the annotation time stamp.
     *
     * @param  timestamp  The annotation time stamp.
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the annotation type.
     *
     * @return  The annotation type.
     */
    public String getType() {
        return type;
    }

    /**
     * Specifies the annotation type.
     *
     * @param  type  The annotation type.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the annotation fields.
     *
     * @return  The annotation fields.
     */
    public Map<String, String> getFields() {
        return fields;
    }

    /**
     * Specifies the annotation fields.
     *
     * @param  fields  The annotation fields.
     */
    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 79 * hash + super.hashCode();
        hash = 79 * hash + Objects.hashCode(this.source);
        hash = 79 * hash + Objects.hashCode(this.id);
        hash = 79 * hash + Objects.hashCode(this.timestamp);
        hash = 79 * hash + Objects.hashCode(this.type);
        hash = 79 * hash + Objects.hashCode(this.fields);
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

        final Annotation other = (Annotation) obj;

        if (!super.equals(other)) {
            return false;
        }
        if (!Objects.equals(this.source, other.source)) {
            return false;
        }
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (!Objects.equals(this.timestamp, other.timestamp)) {
            return false;
        }
        if (!Objects.equals(this.fields, other.fields)) {
            return false;
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
