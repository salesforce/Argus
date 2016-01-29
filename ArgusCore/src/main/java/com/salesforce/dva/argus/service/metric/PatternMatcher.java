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
	 
package com.salesforce.dva.argus.service.metric;

import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands a wildcard query using the provided schema records.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class PatternMatcher {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final Logger LOGGER = LoggerFactory.getLogger(PatternMatcher.class);

    //~ Constructors *********************************************************************************************************************************

    /** Avoid direct instantiation. */
    private PatternMatcher() { }

    //~ Methods **************************************************************************************************************************************

    /**
     * Expands a wildcard query using the provided schema records.
     *
     * @param   metricsSchema  The schema records used to expand the query.  Cannot be null.
     * @param   query          The query to expand.  Cannot be null.
     *
     * @return  The list of expanded queries.
     */
    public static List<MetricQuery> getMatches(Map<String, Map<String, Map<String, Set<String>>>> metricsSchema, MetricQuery query) {
        List<MetricQuery> queries = new ArrayList<>();
        String inputScope = query.getScope();

        // Escape all periods in the input scope
        inputScope = inputScope.replaceAll("\\.", "\\\\.");

        // Replace all * with .* so that it will match any character 0 or more times.
        inputScope = inputScope.replaceAll("\\*", ".*");

        String inputMetric = query.getMetric();

        // Escape all periods in the input metric
        inputMetric = inputMetric.replaceAll("\\.", "\\\\.");

        // Replace all * with .* so that it will match any character 0 or more times.
        inputMetric = inputMetric.replaceAll("\\*", ".*");

        Pattern scopePattern = Pattern.compile(inputScope);
        Pattern metricPattern = Pattern.compile(inputMetric);

        for (Map.Entry<String, Map<String, Map<String, Set<String>>>> scopeEntry : metricsSchema.entrySet()) {
            String scope = scopeEntry.getKey();
            Matcher scopeMatcher = scopePattern.matcher(scope);

            if (scopeMatcher.matches()) {
                // Can never be null
                Map<String, Map<String, Set<String>>> metricsMap = scopeEntry.getValue();

                for (Map.Entry<String, Map<String, Set<String>>> entry : metricsMap.entrySet()) {
                    String metric = entry.getKey();
                    Matcher metricMatcher = metricPattern.matcher(metric);

                    if (metricMatcher.matches()) {
                        // Can never be null
                        Map<String, Set<String>> tagsMap = entry.getValue();
                        Map<String, String> inputTags = query.getTags();
                        Map<String, String> tags = new HashMap<>();

                        for (Map.Entry<String, String> tagEntry : inputTags.entrySet()) {
                            String key = tagEntry.getKey();
                            String inputTagValue = tagEntry.getValue();

                            if (!(inputTagValue.length() == 1 && "*".equals(inputTagValue))) {
                                // Escape all periods in the input tagValue
                                inputTagValue = inputTagValue.replaceAll("\\.", "\\\\.");

                                // Replace all * with .* so that it will match any character 0 or more times.
                                inputTagValue = inputTagValue.replaceAll("\\*", ".*");

                                Pattern tagValuePattern = Pattern.compile(inputTagValue);
                                Set<String> tagValues = tagsMap.get(key);

                                if (tagValues != null) {
                                    StringBuilder sb = new StringBuilder();

                                    for (String tagValue : tagValues) {
                                        Matcher tagValueMatcher = tagValuePattern.matcher(tagValue);

                                        if (tagValueMatcher.matches()) {
                                            sb.append(tagValue).append('|');
                                        }
                                    }
                                    if (sb.length() > 0) {
                                        tags.put(key, sb.substring(0, sb.length() - 1));
                                    }
                                }
                            } else {
                                tags.put(key, inputTagValue);
                            }
                        }

                        MetricQuery generatedQuery = new MetricQuery(scope, metric, tags, query.getStartTimestamp(), query.getEndTimestamp());

                        generatedQuery.setAggregator(query.getAggregator());
                        generatedQuery.setDownsampler(query.getDownsampler());
                        generatedQuery.setDownsamplingPeriod(query.getDownsamplingPeriod());
                        LOGGER.debug("Generated MetricQuery: " + generatedQuery);
                        queries.add(generatedQuery);
                    }
                } // end for
            } // end if
        } // end for

        // queries list will be empty for metrics that don't exist in this schema(metrics pushed before schema implementation)
        // and before wildcarding feature implementation. In that case add back the corresponding query.
        if (queries.isEmpty()) {
            queries.add(query);
        }
        return queries;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
