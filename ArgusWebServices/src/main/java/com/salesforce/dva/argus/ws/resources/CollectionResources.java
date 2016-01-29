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
	 
package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.CollectionService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.AnnotationDto;
import com.salesforce.dva.argus.ws.dto.MetricDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * Provides methods to collect annotation events and metric data.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
@Path("/collection")
@Description("Provides methods to collect annotation events and metric data.")
public class CollectionResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private CollectionService _collectionService = system.getServiceFactory().getCollectionService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Submits externally collected metric data.
     *
     * @param   req         The HTTP request.
     * @param   metricDtos  The metric DTOs to submit.
     *
     * @return  The number of metrics that were submitted, and the number of errors encountered.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/metrics")
    @Description("Submits externally collected metric data.")
    public Map<String, Object> submitMetrics(@Context HttpServletRequest req, final List<MetricDto> metricDtos) {
        PrincipalUser remoteUser = getRemoteUser(req);

        SystemAssert.requireArgument(metricDtos != null, "Cannot submit null timeseries metrics list.");

        List<Metric> legalMetrics = new ArrayList<>();
        List<MetricDto> illegalMetrics = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (MetricDto metricDto : metricDtos) {
            try {
                Metric metric = new Metric(metricDto.getScope(), metricDto.getMetric());

                copyProperties(metric, metricDto);
                legalMetrics.add(metric);
            } catch (Exception e) {
                illegalMetrics.add(metricDto);
                errorMessages.add(e.getMessage());
            }
        }
        _collectionService.submitMetrics(remoteUser, legalMetrics);

        Map<String, Object> result = new HashMap<>();

        result.put("Success", legalMetrics.size() + " metrics");
        result.put("Error", illegalMetrics.size() + " metrics");
        result.put("Error Messages", errorMessages);
        return result;
    }

    /**
     * Submits externally collected annotation data.
     *
     * @param   req             The HTTP request.
     * @param   annotationDtos  The annotation DTOs to submit.
     *
     * @return  The number of annotations that were submitted, and the number of errors encountered.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/annotations")
    @Description("Submits externally collected annotation data.")
    public Map<String, Object> submitAnnotations(@Context HttpServletRequest req, final List<AnnotationDto> annotationDtos) {
        PrincipalUser remoteUser = getRemoteUser(req);

        SystemAssert.requireArgument(annotationDtos != null, "Cannot submit null annotations list.");

        List<Annotation> legalAnnotations = new ArrayList<>();
        List<AnnotationDto> illegalAnnotations = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        for (AnnotationDto annotationDto : annotationDtos) {
            try {
                Annotation annotation = new Annotation(annotationDto.getSource(), annotationDto.getId(), annotationDto.getType(),
                    annotationDto.getScope(), annotationDto.getMetric(), annotationDto.getTimestamp());

                copyProperties(annotation, annotationDto);
                legalAnnotations.add(annotation);
            } catch (Exception e) {
                illegalAnnotations.add(annotationDto);
                errorMessages.add(e.getMessage());
            }
        }
        _collectionService.submitAnnotations(remoteUser, legalAnnotations);

        Map<String, Object> result = new HashMap<>();

        result.put("Success", legalAnnotations.size() + " annotations");
        result.put("Error", illegalAnnotations.size() + " annotations");
        result.put("Error Messages", errorMessages);
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
