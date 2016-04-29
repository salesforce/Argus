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

import com.google.gson.Gson;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.AnnotationDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

/**
 * Provides methods to query annotations.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com) Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Path("/annotations")
@Description("Provides methods to query annotations.")
public class AnnotationResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private final String COMMA = ",";
    private final String NEW_LINE = "\n";
    private final String EMPTY = "";
    private final String QUOTE = "\"";

    //~ Methods **************************************************************************************************************************************

    /**
     * Performs an annotation query using the given expression.
     *
     * @param   req          The HTTP request.
     * @param   expressions  The list of expressions to evaluate.
     *
     * @return  The resulting annotations.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";qs=2")
    @Description("Performs an annotation query using the given expression.")
    public List<AnnotationDto> getAnnotationsJSON(@Context HttpServletRequest req,
        @QueryParam("expression") final List<String> expressions) {
        List<Annotation> annotations = _getAnnotations(req, expressions);

        return AnnotationDto.transformToDto(annotations);
    }

    /**
     * Downloads annotations in CSV format.
     *
     * @param   req          The HttpServlet request object. Cannot be null.
     * @param   expressions  Expressions for annotations
     *
     * @return  Annotations in CSV format
     */
    @GET
    @Produces({ "application/ms-excel;qs=1" })
    @Description("Downloads annotations in CSV format.")
    public Response getAnnotationsCSV(@Context HttpServletRequest req,
        @QueryParam("expression") final List<String> expressions) {
        ResponseBuilder response = null;

        try {
            List<Annotation> annotations = _getAnnotations(req, expressions);

            response = Response.ok(_convertToCSV(annotations));
        } catch (SystemException ex) {
            response = Response.status(Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage());
        }
        response.header("Content-Disposition", "attachment; filename=annotations.csv");
        return response.build();
    }

    private List<Annotation> _getAnnotations(HttpServletRequest req, final List<String> expressions) {
        validateAndGetOwner(req, null);
        SystemAssert.requireArgument(expressions != null && !expressions.isEmpty(), "Expression list cannot be null or empty");

        AnnotationService annotationService = system.getServiceFactory().getAnnotationService();
        List<Annotation> annotations = new ArrayList<>();

        for (String expression : expressions) {
            annotations.addAll(annotationService.getAnnotations(expression));
        }
        return annotations;
    }

    private String _convertToCSV(List<Annotation> annotations) {
        StringBuilder result = new StringBuilder();

        if (annotations != null) {
            result.append(_getCSVHeaders());

            Gson gson = new Gson();

            for (Annotation annotation : annotations) {
                result.append(NEW_LINE);
                result.append(_getCSVForAnnotation(annotation, gson));
            }
        }
        return result.toString();
    }

    private String _getCSVHeaders() {
        StringBuilder result = new StringBuilder();

        result.append("Timestamp");
        result.append(COMMA);
        result.append("Expression");
        result.append(COMMA);
        result.append("Source");
        result.append(COMMA);
        result.append("Type");
        result.append(COMMA);
        result.append("Fields");
        result.append(COMMA);
        return result.toString();
    }

    private String _getCSVForAnnotation(Annotation annotation, Gson gson) {
        StringBuilder result = new StringBuilder();

        if (annotation != null) {
            result.append(QUOTE);
            result.append(annotation.getTimestamp());
            result.append(QUOTE);
            result.append(COMMA);
            result.append(QUOTE);
            result.append(_getAnnotationExpression(annotation));
            result.append(QUOTE);
            result.append(COMMA);
            result.append(QUOTE);
            result.append(annotation.getSource());
            result.append(QUOTE);
            result.append(COMMA);
            result.append(QUOTE);
            result.append(annotation.getType());
            result.append(QUOTE);
            result.append(COMMA);
            if (annotation.getFields().size() > 0) {
                result.append(QUOTE);
                for (Entry<String, String> tag : annotation.getFields().entrySet()) {
                    result.append(tag.getKey() + "=" + tag.getValue());
                    result.append(COMMA);
                }
                result.deleteCharAt(result.length() - 1);
                result.append(QUOTE);
            } else {
                result.append(EMPTY);
            }
        }
        return result.toString();
    }

    private String _getAnnotationExpression(Annotation annotation) {
        StringBuilder result = new StringBuilder();

        if (annotation != null) {
            result.append(annotation.getScope());
            result.append(":" + annotation.getMetric());
            if (annotation.getTags() != null && annotation.getTags().size() > 0) {
                result.append("{");
                for (Entry<String, String> tag : annotation.getTags().entrySet()) {
                    result.append(tag.getKey() + "=" + tag.getValue());
                    result.append(COMMA);
                }
                result.deleteCharAt(result.length() - 1);
                result.append("}");
            }
        }
        return result.toString();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
