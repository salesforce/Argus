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

import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.resources.AbstractResource;
import javax.ws.rs.Path;

/**
 * Endpoint help DTO.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class EndpointHelpDto {

    //~ Instance fields ******************************************************************************************************************************

    String endpoint;
    String description;

    //~ Methods **************************************************************************************************************************************

    /**
     * Creates an endpoint help DTO from a resource class.
     *
     * @param   resourceClass  The resource class.
     *
     * @return  The endpoint help DTO.
     */
    public static EndpointHelpDto fromResourceClass(Class<? extends AbstractResource> resourceClass) {
        Path path = resourceClass.getAnnotation(Path.class);
        Description description = resourceClass.getAnnotation(Description.class);

        if (path != null && description != null) {
            EndpointHelpDto result = new EndpointHelpDto();

            result.setDescription(description.value());
            result.setEndpoint(path.value());
            return result;
        } else {
            return null;
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the endpoint.
     *
     * @return  The endpoint.
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Specifies the endpoint.
     *
     * @param  endpoint  The endpoint.
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Returns the description.
     *
     * @return  The description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Specifies the description.
     *
     * @param  description  The description.
     */
    public void setDescription(String description) {
        this.description = description;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
