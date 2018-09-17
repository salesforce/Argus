package com.salesforce.dva.argus.ws.resources;

import com.google.common.collect.Maps;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.ws.dto.MetricDto;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

public class CollectionResourcesTest {

    @Test
    public void createMetric() {
        MetricDto metricDto = new MetricDto();
        metricDto.setScope("scope");
        metricDto.setMetric("metric");

        Map<String, String> tags = Maps.newHashMap();
        tags.put("tagk1", "tagv1");
        tags.put("tagk2", "tagv2");
        tags.put(CollectionResources.INTERNAL_TAGS[0], "value");
        metricDto.setTags(tags);
        Metric metric = CollectionResources.createMetric(metricDto);
        Map<String, String> cleanedUpTags = metric.getTags();

        assertEquals("expect to have only 2 tags left in metric object", 2, cleanedUpTags.size());
        assertFalse("expect internal tag to not exist in metric object", cleanedUpTags.containsKey(CollectionResources.INTERNAL_TAGS[0]));
    }
}