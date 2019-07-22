package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.metadata.IDBMetadataService;
import com.salesforce.dva.argus.service.metric.metadata.MetadataService;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.QueryContext;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Retain metrics based on whether metadata from an external source (provided by MetadataService) matches
 * the user-provided lookup_value
 */
public class MetadataInclude implements Transform {
    static final String HELP_MESSAGE = StringEscapeUtils.escapeHtml("\nValid METADATA_INCLUDE usages:\n" +
            "METADATA_INCLUDE(<expression>, <lookup_key>, <lookup_value>, #host_from_tag#, #tagk#)\n" +
            "METADATA_INCLUDE(<expression>, <lookup_key>, <lookup_value>, #pod_from_scope#, <Matcher regex to extract metadata>)\n" +
            "METADATA_INCLUDE(<expression>, <lookup_key>, <lookup_value>, #identifier#, <Matcher regex with parentheses to extract metadata>)\n" +
            "METADATA_INCLUDE(<expression>, <lookup_key>, <lookup_value>, #name#, <comma separated names>)\n").replaceAll("\\n", "<br />");
    static final String TRANSFORM_NAME =  TransformFactory.Function.METADATA_INCLUDE.name() + " transform";
    static final String NAME_CSV_MODE = "name";
    static final String IDENTIFIER_EXTRACT_MODE =  "identifier";
    static final String HOST_FROM_TAG_MODE = "host_from_tag";
    static final String CLUSTER_FROM_SCOPE_MODE =  "cluster_from_scope";
    MetadataService metadataService;

    public MetadataInclude(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    List<Metric> processMetadataFilterTransform(List<Metric> metrics, List<String> constants, boolean defaultBoolean) {
        requireArgument(metrics != null, "Cannot transform null metrics");
        requireArgument(constants != null, "Cannot transform with null constants");
        constants = constants.stream().map(String::trim).collect(Collectors.toList());
        requireArgument(constants.stream().allMatch(constant -> constant.length() > 0), "Transform constants cannot be an empty string");
        requireArgument(constants.size() == 3 || constants.size() == 4,
                String.format("%s expecting either 3 or 4 arguments after the expression %s", TRANSFORM_NAME, HELP_MESSAGE));
        if (metrics.size() == 0) {
            return metrics;
        }
        String lookupKey = constants.get(0);
        String lookupValue = constants.get(1);
        String mode = constants.get(2);
        if (constants.size() == 4) {
            requireArgument(
                    mode.equals(IDENTIFIER_EXTRACT_MODE) || mode.equals(NAME_CSV_MODE) || mode.equals(HOST_FROM_TAG_MODE),
                    String.format("%s: When using 4 arguments, expected either #%s# or #%s# or #%s# as 3rd argument %s", TRANSFORM_NAME, IDENTIFIER_EXTRACT_MODE, NAME_CSV_MODE, HOST_FROM_TAG_MODE, HELP_MESSAGE));
        }
        if (constants.size() == 3) {
            requireArgument(
                    mode.equals(CLUSTER_FROM_SCOPE_MODE),
                    String.format("%s: When using 3 arguments, expected either #%s# as 3rd argument %s", TRANSFORM_NAME, CLUSTER_FROM_SCOPE_MODE, HELP_MESSAGE));
        }
        Map<Metric, Boolean> metadataEquality;
        switch (mode) {
            case NAME_CSV_MODE:
                // Name mode can be optimized since it doesn't need any info from the List<Metric> metrics (besides datacenter for IDB)
                // If the lookupKey != lookupValue for any one of the nameTokens
                //      Then discard the entire metrics list. So only up to nameTokens.length Metadata lookups need to be performed
                // Else, include the entire metrics list
                String names = constants.get(3);
                names = names.replaceAll("\\s+", "");
                for (String nameToken: names.split(",")) {
                    Map<Metric, Boolean> equality = metadataService.isMetadataEquals(Arrays.asList(metrics.get(0)),
                            lookupKey,
                            lookupValue,
                            defaultBoolean,
                            metric -> nameToken);
                    if (equality.containsValue(false)) {
                        return Collections.emptyList();
                    }
                }
                return metrics;
            case IDENTIFIER_EXTRACT_MODE:
                String identifierRegex = constants.get(3);
                requireArgument(StringUtils.countMatches(identifierRegex, "(") == 1 &&
                                StringUtils.countMatches(identifierRegex, ")") == 1,
                        String.format("%s #identifier# mode requires a Matcher regex that contains exactly one open and one close parentheses", TRANSFORM_NAME));
                int leftParenIndex = identifierRegex.indexOf("(");
                int rightParenIndex = identifierRegex.indexOf(")");
                requireArgument(leftParenIndex < rightParenIndex, String.format("%s #identifier# mode closing parentheses must appear after open parentheses", TRANSFORM_NAME));

                Pattern pattern = Pattern.compile(identifierRegex);
                Map<Metric, String> matchedSubstrings = new HashMap<>();
                metrics.forEach(metric -> {
                    String identifier = metric.getIdentifier();
                    Matcher matcher = pattern.matcher(identifier);
                    if (matcher.find()) {
                        matchedSubstrings.put(metric, matcher.group(1));
                    }  else {
                        throw new SystemException(String.format("%s #identifier# mode failed to match pattern %s to identifier %s", TRANSFORM_NAME, pattern, identifier));
                    }

                });
                metadataEquality = metadataService.isMetadataEquals(metrics, lookupKey, lookupValue, defaultBoolean, metric -> matchedSubstrings.get(metric));
                break;
            case HOST_FROM_TAG_MODE:
                if (!lookupKey.startsWith(IDBMetadataService.IDB_HOST_FIELD_PREFIX)) {
                    throw new SystemException(String.format("%s #from_device# mode must use a lookup_key that begins with %s", TRANSFORM_NAME, IDBMetadataService.IDB_HOST_FIELD_PREFIX));
                }
                String tagKey = constants.get(3);
                metrics.forEach(metric -> {
                    if (metric.getTag(tagKey) == null) {
                        throw new SystemException(String.format("%s: when applying #%s#, '%s' tagkey is missing for metric with identifier: %s", TRANSFORM_NAME, HOST_FROM_TAG_MODE, tagKey, metric.getIdentifier()));
                    }
                });
                metadataEquality = metadataService.isMetadataEquals(metrics, lookupKey, lookupValue, defaultBoolean, metric -> metric.getTag(tagKey));
                break;
            case CLUSTER_FROM_SCOPE_MODE:
                if (!lookupKey.startsWith(IDBMetadataService.IDB_CLUSTER_FIELD_PREFIX)) {
                    throw new SystemException(String.format("%s #%s# mode must use a lookup_key that begins with %s", TRANSFORM_NAME, CLUSTER_FROM_SCOPE_MODE, IDBMetadataService.IDB_CLUSTER_FIELD_PREFIX));
                }
                metrics.forEach(metric -> {
                    if (metric.getScope().split("\\.").length < 4) {
                        throw new SystemException(String.format("%s: when applying #from_scope#, scope is missing a pod field (expected as the 4th or last period-separated token) for metric %s", TRANSFORM_NAME, metric.getIdentifier()));
                    }
                });
                metadataEquality = metadataService.isMetadataEquals(metrics, lookupKey, lookupValue, defaultBoolean, metric -> {
                    String[] tokens = metric.getScope().split("\\.");
                    return tokens[tokens.length - 1];
                });
                break;
            default:
                // This should never be hit anyways
                throw new UnsupportedOperationException(String.format("Invalid 3rd argument for %s %s", TRANSFORM_NAME, HELP_MESSAGE));
        }
        return metadataEquality.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        return processMetadataFilterTransform(metrics, constants, true);
    }

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException(String.format("%s expects 3 or 4 arguments %s", TRANSFORM_NAME, HELP_MESSAGE));
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... metrics) {
        throw new UnsupportedOperationException(String.format("%s expects 3 or 4 arguments %s", TRANSFORM_NAME, HELP_MESSAGE));
    }

    @Override
    public String getResultScopeName() {
        return TRANSFORM_NAME;
    }
}
