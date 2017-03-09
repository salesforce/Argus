package com.salesforce.dva.argus.service.schema;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.salesforce.dva.argus.service.schema.TSDBAwareDiscoveryService.checkFilterExpression;

/**
 * Helper class to mask and demask a queries.
 *
 * @author stefanwiedemann {@literal stefan.wiedemann@kiwigrid.com}
 */
public class MetricQueryMasker {

	private final Logger _logger = LoggerFactory.getLogger(getClass());

	private final Map<String, String> replacementMap = new HashMap<>();

	/**
	 * Remove the masks from the query, using the given replacement map. The tagExpression will be URL encoded, to allow
	 * the usage of tsdb filters through the rest call to the openTSDB.
	 *
	 * @param metricQuery - the metric query to demask
	 */
	public MetricQuery demaskQuery(final MetricQuery metricQuery) {

		MetricQuery demaskedQuery = new MetricQuery(metricQuery);

		Map<String, String> demaskedTags = new LinkedHashMap<>();
		for (Map.Entry<String, String> tag : metricQuery.getTags().entrySet()) {
			String tagExpression = tag.getValue();
			if (replacementMap.containsKey(tagExpression)) {
				_logger.debug("Replacing masked expression {}.", tagExpression);
				tagExpression = replacementMap.get(tagExpression);
			}
			try {
				demaskedTags.put(tag.getKey(), URLEncoder.encode(tagExpression, "UTF-8"));
			} catch (UnsupportedEncodingException e) {
				_logger.error(String.format("The expression %s cannot be encoded as an URL.", tagExpression), e);
				throw new IllegalArgumentException(String.format("The expression %s cannot be encoded as an URL.",
						tagExpression), e);
			}
		}
		demaskedQuery.setTags(demaskedTags);

		return demaskedQuery;
	}

	/**
	 * Mask the filters in the given query. Will exchange the tagExpression with a hash of the expression.
	 *
	 * @param query - the query to mask
	 * @return the masked query
	 */
	public MetricQuery maskQuery(final MetricQuery query) {

		MetricQuery maskedQuery = new MetricQuery(query);

		Map<String, String> maskedTags = new LinkedHashMap<>();

		for (Map.Entry<String, String> tag : query.getTags().entrySet()) {
			String tagExpression = tag.getValue();
			String maskedExpression = tagExpression;
			if (checkFilterExpression(tagExpression)) {
				_logger.debug("Mask expression {}.", tagExpression);
				maskedExpression = String.valueOf(tagExpression.hashCode());
				replacementMap.put(maskedExpression, tagExpression);
			}
			maskedTags.put(tag.getKey(), maskedExpression);
		}
		maskedQuery.setTags(maskedTags);
		return maskedQuery;
	}

	public boolean isQueryMasked() {
		return !replacementMap.isEmpty();
	}

}
