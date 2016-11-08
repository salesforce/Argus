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

import com.salesforce.dva.argus.entity.TSDBEntity;
import org.apache.commons.beanutils.BeanUtils;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * The base TSDB entity DTO.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public abstract class TSDBEntityDto extends BaseDto {

    //~ Instance fields ******************************************************************************************************************************

    private String scope;
    private String metric;
    private Map<String, String> tags;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a TSDB entity to a DTO.
     *
     * @param   <D>         The TSDB DTO type.
     * @param   <E>         The TSDB entity type.
     * @param   clazz       The runtime type of the DTO object.
     * @param   tsdbEntity  The TSDB entity to convert.
     *
     * @return  The TSDB DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static <D extends TSDBEntityDto, E extends TSDBEntity> D createDtoObject(Class<D> clazz, E tsdbEntity) {
        D result = null;

        try {
            result = clazz.newInstance();
            BeanUtils.copyProperties(result, tsdbEntity);
        } catch (Exception ex) {
            throw new WebApplicationException("DTO transformation failed.", Status.INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the scope.
     *
     * @return  The scope.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Sets the scope.
     *
     * @param  scope  The scope.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Returns the metric name.
     *
     * @return  The metric name.
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Sets the metric name.
     *
     * @param  metric  The metric name.
     */
    public void setMetric(String metric) {
        this.metric = metric;
    }

    /**
     * Returns the metric tags.
     *
     * @return  The metric tags.
     */
    public Map<String, String> getTags() {
        return tags;
    }

    /**
     * Sets the metric tags.
     *
     * @param  tags  The metric tags.
     */
    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
