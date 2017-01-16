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
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * Alert Dto.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardDto extends EntityDTO {

    //~ Instance fields ******************************************************************************************************************************

    private String name;
    private String content;
    private String ownerName;
    private boolean shared;
    private String description;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts alert entity to alertDto.
     *
     * @param   dashboard  The alert object. Cannot be null.
     *
     * @return  AlertDto object.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static DashboardDto transformToDto(Dashboard dashboard) {
        if (dashboard == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        DashboardDto result = createDtoObject(DashboardDto.class, dashboard);

        result.setOwnerName(dashboard.getOwner().getUserName());
        return result;
    }

    /**
     * Converts list of alert entity objects to list of alertDto objects.
     *
     * @param   dashboards  List of alert entities. Cannot be null.
     *
     * @return  List of alertDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<DashboardDto> transformToDto(List<Dashboard> dashboards) {
        if (dashboards == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<DashboardDto> result = new ArrayList<DashboardDto>();

        for (Dashboard dashboard : dashboards) {
            result.add(transformToDto(dashboard));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the dashboard content.
     *
     * @return  The dashboard content.
     */
    public String getContent() {
        return content;
    }

    /**
     * Specifies the dashboard content.
     *
     * @param  content  The dashboard content.
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Indicates whether the dashboard is shared.
     *
     * @return  True if the dashboard is shared.
     */
    public boolean isShared() {
        return shared;
    }

    /**
     * Specifies whether the dashboard is shared.
     *
     * @param  shared  True if the dashboard is shared.
     */
    public void setShared(boolean shared) {
        this.shared = shared;
    }

    /**
     * Returns the alert name.
     *
     * @return  The alert name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the alert name.
     *
     * @param  name  The alert name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the owner name.
     *
     * @return  The owner name.
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * Sets the owner name.
     *
     * @param  ownerName  The owner name.
     */
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    /**
     * Returns the description of the dashboard.
     *
     * @return  The dashboard description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the dashboard.
     *
     * @param  description  The dashboard description.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Object createExample() {
        DashboardDto result = new DashboardDto();

        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date());
        result.setId(BigInteger.ONE);
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date());
        result.setName("example-dashboard");
        result.setOwnerName("admin");
        result.setShared(false);
        result.setDescription("a description of the dashboard.");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
