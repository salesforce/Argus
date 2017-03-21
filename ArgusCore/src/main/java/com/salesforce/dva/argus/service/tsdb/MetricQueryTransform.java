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

package com.salesforce.dva.argus.service.tsdb;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Transforms Metricquery instances to json (OpenTSDB compatible query json).
 *
 * @author Bhinav Sura (bhinav.sura@salesforce.com)
 */
class MetricQueryTransform {
	
	//~ Constructors *********************************************************************************************************************************
	
	private MetricQueryTransform() {}
	
	//~ Inner Classes ********************************************************************************************************************************
	
	/**
     * The metricquery serializer.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
	static class Serializer extends JsonSerializer<MetricQuery> {

		@Override
		public void serialize(MetricQuery query, JsonGenerator jgen, SerializerProvider sp) 
				throws IOException, JsonProcessingException {
			
			jgen.writeStartObject();
			jgen.writeNumberField("start", Math.max(0, query.getStartTimestamp() - 1));
			jgen.writeNumberField("end", Math.max(query.getStartTimestamp(), query.getEndTimestamp() + 1));
			jgen.writeBooleanField("noAnnotations", true);
			jgen.writeBooleanField("msResolution", true);
			jgen.writeBooleanField("showTSUIDs", true);
			jgen.writeArrayFieldStart("queries");
			jgen.writeStartObject();
			MetricQuery.Aggregator agg = query.getAggregator();
			jgen.writeStringField("aggregator", agg == null ? MetricQuery.Aggregator.AVG.getDescription() : agg.getDescription());
			jgen.writeStringField("metric", query.getTSDBMetricName());
			if(!query.getTags().isEmpty()) {
				jgen.writeObjectFieldStart("tags");
				for(Map.Entry<String, String> tag : query.getTags().entrySet()) {
					jgen.writeStringField(tag.getKey(), tag.getValue());
				}
				jgen.writeEndObject();
			}
			
			if(query.getDownsampler() != null) {
				jgen.writeStringField("downsample", query.getDownsamplingPeriod() + "ms-" + query.getDownsampler().getDescription());
			}
			jgen.writeEndObject();
			jgen.writeEndArray();
			jgen.writeEndObject();
			
		}
		
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */