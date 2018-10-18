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

import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.KeywordQuery.KeywordQueryBuilder;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.MetricDiscoveryQueryDto;
import com.salesforce.dva.argus.ws.dto.MetricDiscoveryResultDto;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

/**
 * Provides methods to discover resources.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Path("/discover")
@Description("Provides methods to discover resources.")
public class DiscoveryResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private DiscoveryService _discoveryService = system.getServiceFactory().getDiscoveryService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Discover metric schema records. If type is specified, then records of that particular type are returned.
     *
     * @param   req             The HTTP request.
     * @param   namespaceRegex  The namespace filter.
     * @param   scopeRegex      The scope filter.
     * @param   metricRegex     The metric name filter.
     * @param   tagkRegex       The tag key filter.
     * @param   tagvRegex       The tag value filter.
     * @param   limit           The maximum number of records to return.
     * @param   page            The page of results to return
     * @param   type            The field for which to retrieve unique values.  If null, the entire schema record including all the fields is returned.
     *
     * @return  The filtered set of schema records or unique values if a specific field is requested.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/metrics/schemarecords")
    @Description("Discover metric schema records. If type is specified, then records of that particular type are returned.")
    public List<? extends Object> getRecords(@Context HttpServletRequest req,
        @DefaultValue("*") @QueryParam("namespace") final String namespaceRegex,
        @QueryParam("scope") final String scopeRegex,
        @QueryParam("metric") final String metricRegex,
        @DefaultValue("*") @QueryParam("tagk") final String tagkRegex,
        @DefaultValue("*") @QueryParam("tagv") final String tagvRegex,
        @DefaultValue("50") @QueryParam("limit") final int limit,
        @DefaultValue("1") @QueryParam("page") final int page,
        @QueryParam("type") String type) {
        
        if (type == null) {

            MetricSchemaRecordQuery query = new MetricSchemaRecordQueryBuilder().namespace(namespaceRegex)
                                                                                .scope(scopeRegex)
                                                                                .metric(metricRegex)
                                                                                .tagKey(tagkRegex)
                                                                                .tagValue(tagvRegex)
                                                                                .limit(limit * page)
                                                                                .build();
            List<MetricSchemaRecord> schemaRecords = _discoveryService.filterRecords(query);
            
            if(_isFormat(req)) {
                List<String> records = new ArrayList<>(schemaRecords.size());
                _formatToString(schemaRecords, records);
                return _getSubList(records, limit * (page - 1), records.size());
            }
            
            return _getSubList(schemaRecords, limit * (page - 1), schemaRecords.size());
            
        } else {

            MetricSchemaRecordQuery query = new MetricSchemaRecordQueryBuilder().namespace(namespaceRegex)
                                                                                .scope(scopeRegex)
                                                                                .metric(metricRegex)
                                                                                .tagKey(tagkRegex)
                                                                                .tagValue(tagvRegex)
                                                                                .limit(limit * page)
                                                                                .build();
            List<MetricSchemaRecord> records = _discoveryService.getUniqueRecords(query, RecordType.fromName(type));

            return _getValueForType(_getSubList(records, limit*(page-1), records.size()), RecordType.fromName(type)); 
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/metrics/browsing")
    @Description("Browse metric schema records.")
    public List<? extends Object> browseRecords(@Context HttpServletRequest req,
                                             @QueryParam("query") String queryRegex) {

        if (StringUtils.isNotEmpty(queryRegex))
        {
            queryRegex = queryRegex + "*";
        }
        else
        {
            List<String> scopeLetters = Arrays.asList(
                    "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                    "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
                    "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
                    "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z");

            return scopeLetters;
        }

        MetricSchemaRecordQuery query = new MetricSchemaRecordQueryBuilder().scope(queryRegex)
                .namespace("*")
                .metric("*")
                .tagKey("*")
                .tagValue("*")
                .limit(10000)
                .build();

        int indexLevel = StringUtils.countMatches(queryRegex, ".");

        return _discoveryService.browseRecords(query, RecordType.SCOPE, indexLevel);
    }

    /**
     * Discover metric schema records. If type is specified, then records of that particular type are returned.
     *
     * @param   req             The HTTP request.
     * @param   metricDiscoveryQueryDto This contains metric query parameters along with scanner starting schema record
     * @return  The filtered set of schema records or unique values if a specific field is requested.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/metrics/schemarecords")
    @Description("Discover metric schema records. If type is specified, then records of that particular type are returned.")
    public MetricDiscoveryResultDto getRecords(@Context HttpServletRequest req,
        MetricDiscoveryQueryDto metricDiscoveryQueryDto) {
        
        MetricSchemaRecordQuery query = new MetricSchemaRecordQueryBuilder().namespace(metricDiscoveryQueryDto.getNamespace())
                                                                            .scope(metricDiscoveryQueryDto.getScope())
                                                                            .metric(metricDiscoveryQueryDto.getMetric())
                                                                            .tagKey(metricDiscoveryQueryDto.getTagk())
                                                                            .tagValue(metricDiscoveryQueryDto.getTagv())
                                                                            .limit(metricDiscoveryQueryDto.getLimit())
                                                                            .page(metricDiscoveryQueryDto.getPage())
                                                                            .scanFrom(metricDiscoveryQueryDto.getScanStartSchemaRecord())
                                                                            .build();

        if (metricDiscoveryQueryDto.getType() == null) {
            
            List<MetricSchemaRecord> schemaRecords = _discoveryService.filterRecords(query);
            
            if(_isFormat(req)) {
                List<String> records = new ArrayList<>(schemaRecords.size());
                _formatToString(schemaRecords, records);
                return new MetricDiscoveryResultDto(records, records.isEmpty() ? null : schemaRecords.get(schemaRecords.size()-1));
            }
            
            return new MetricDiscoveryResultDto(schemaRecords, schemaRecords.isEmpty() ? null : schemaRecords.get(schemaRecords.size()-1));
        } else {
            List<MetricSchemaRecord> records = _discoveryService.getUniqueRecords(query, metricDiscoveryQueryDto.getType());
            return new MetricDiscoveryResultDto(_getValueForType(records, metricDiscoveryQueryDto.getType()), 
                                                 records.isEmpty() ? null : records.get(records.size()-1));
        }
        
    }

    private boolean _isFormat(HttpServletRequest req) {
        return req.getParameterMap().containsKey("format") &&
                (req.getParameter("format") == null || req.getParameter("format").isEmpty() || Boolean.parseBoolean(req.getParameter("format")));
    }
    
    /**
     * Discover metric schema records. If type is specified, then records of that particular type are returned.
     *
     * @param   req             The HTTP request.
     * @param   metricDiscoveryQueryDto This contains metric query parameters along with scanner starting schema record
     *
     * @return  The filtered set of schema records or unique values if a specific field is requested.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/metrics/search")
    @Description("Discover metric schema records. If type is specified, then records of that particular type are returned.")
    public MetricDiscoveryResultDto searchRecords(@Context HttpServletRequest req,
        MetricDiscoveryQueryDto metricDiscoveryQueryDto) {

        KeywordQuery query = new KeywordQueryBuilder().query(metricDiscoveryQueryDto.getKeywordQuery())
                                                      .limit(metricDiscoveryQueryDto.getLimit())
                                                      .page(metricDiscoveryQueryDto.getPage())
                                                      .scope(metricDiscoveryQueryDto.getScope())
                                                      .metric(metricDiscoveryQueryDto.getMetric())
                                                      .tagKey(metricDiscoveryQueryDto.getTagk())
                                                      .tagValue(metricDiscoveryQueryDto.getTagv())
                                                      .namespace(metricDiscoveryQueryDto.getNamespace())
                                                      .type(metricDiscoveryQueryDto.getType())
                                                      .build();

        List<MetricSchemaRecord> schemaRecords = _discoveryService.filterRecords(query);

        if(query.getType() == null) {
            if(_isFormat(req)) {
                List<String> records = new ArrayList<>(schemaRecords.size());
                _formatToString(schemaRecords, records);
                return new MetricDiscoveryResultDto(records, records.isEmpty() ? null : schemaRecords.get(schemaRecords.size()-1));
            }

            return new MetricDiscoveryResultDto(schemaRecords, schemaRecords.isEmpty() ? null : schemaRecords.get(schemaRecords.size()-1));
        } else {
            return new MetricDiscoveryResultDto(_getValueForType(schemaRecords, query.getType()),
                    schemaRecords.isEmpty() ? null : schemaRecords.get(schemaRecords.size()-1));
        }
        
    }
    
    
    
    private static List<String> _getValueForType(List<MetricSchemaRecord> records, RecordType type) {

        List<String> result=new ArrayList<>();

        for(MetricSchemaRecord record:records){
            result.add(record.getStringValueForType(type));
        }

        return result;
    }
    
    private static void _formatToString(List<MetricSchemaRecord> schemaRecords, List<String> records) {

        for(MetricSchemaRecord msr : schemaRecords) {
            records.add(MetricSchemaRecord.print(msr));
        }
    }

    private static <T> List<T> _getSubList(List<T> list, int from, int to){

        if(list.size()<from){
            return new ArrayList<T>();
        }else{
            return list.subList(from, to);
        }

    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
