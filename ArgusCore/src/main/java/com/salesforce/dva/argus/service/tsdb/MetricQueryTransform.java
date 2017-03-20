package com.salesforce.dva.argus.service.tsdb;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

class MetricQueryTransform {
	
	private MetricQueryTransform() {}
	
	static class Serizlizer extends JsonSerializer<MetricQuery> {

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
