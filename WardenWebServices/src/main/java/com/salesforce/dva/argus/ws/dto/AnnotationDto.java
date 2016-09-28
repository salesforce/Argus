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

import com.salesforce.dva.argus.entity.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * Annotation DTO.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class AnnotationDto extends TSDBEntityDto {

    //~ Instance fields ******************************************************************************************************************************

    private String source;
    private String id;
    private Long timestamp;
    private String type;
    private Map<String, String> fields;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts annotation entity to DTO.
     *
     * @param   annotation  The annotation entity.  Cannot be null.
     *
     * @return  The annotation DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static AnnotationDto transformToDto(Annotation annotation) {
        if (annotation == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        AnnotationDto result = createDtoObject(AnnotationDto.class, annotation);

        return result;
    }

    /**
     * Converts list of alert entity objects to list of alertDto objects.
     *
     * @param   annotations  List of alert entities. Cannot be null.
     *
     * @return  List of alertDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<AnnotationDto> transformToDto(List<Annotation> annotations) {
        if (annotations == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<AnnotationDto> result = new ArrayList<>();

        for (Annotation annotation : annotations) {
            result.add(transformToDto(annotation));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public Object createExample() {
        AnnotationDto result = new AnnotationDto();
        Map<String, String> sampleFields = new HashMap<>();

        fields.put("fieldName", "fieldValue");
        result.setFields(sampleFields);
        result.setId("datasourceSpecificUniqueID");
        result.setMetric("metric");
        result.setScope("scope");
        result.setSource("splunk");

        Map<String, String> sampleTags = new HashMap<>();

        sampleTags.put("tagk", "tagv");
        result.setTags(sampleTags);
        result.setTimestamp(System.currentTimeMillis());
        result.setType("erelease");
        return result;
    }

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
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
