package com.salesforce.dva.argus.service.schema;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the DiscoveryService, that decorates the {@link CachedDiscoveryService} to allow openTSDB 2.x
 * filters for tags. It checks the tags of a given query for the occurrence of tsdb filters and masks them, before
 * forwarding the query to the {@link CachedDiscoveryService}. That way, it allows all the caching and query discovery
 * by wildcards of the {@link CachedDiscoveryService}, but also the usage of the tsdb tag query features. It is not
 * possible to use argus-wildcard and tsdb filters in the same tag, but it is possible to use filters for one tag and
 * wildcards for the other.
 *
 * @author stefanwiedemann {@literal stefan.wiedemann@kiwigrid.com}
 */
@Singleton
public class TSDBAwareDiscoveryService extends DefaultService implements DiscoveryService {

	protected final Logger _logger = LoggerFactory.getLogger(getClass());

	@Inject
	private CachedDiscoveryService _discoveryService;

	private final List<String> TSDB_FILTERS = Arrays.asList("regexp",
			"literal_or",
			"iliteral_or",
			"not_literal_or",
			"not_iliteral_or",
			"wildcard",
			"iwildcard",
			"regexp");

	@Inject
	TSDBAwareDiscoveryService(SystemConfiguration config)
	{
		super(config);
	}

	@Override
	public void dispose() {
		super.dispose();
		_discoveryService.dispose();
	}

	@Override
	public List<MetricSchemaRecord> filterRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagkRegex, String tagvRegex, int limit, int page) {
		return _discoveryService.filterRecords(namespaceRegex,
				scopeRegex,
				metricRegex,
				tagkRegex,
				tagvRegex,
				limit,
				page);
	}

	@Override
	public List<String> getUniqueRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagkRegex, String tagvRegex, SchemaService.RecordType type, int limit, int page) {
		return _discoveryService.getUniqueRecords(namespaceRegex,
				scopeRegex,
				metricRegex,
				tagkRegex,
				tagvRegex,
				type,
				limit,
				page);
	}

	/**
	 * Checks first if the query has some openTSDB tag-filters. If so, it masks them with a hass before forwarding them
	 * to the {@link CachedDiscoveryService}. The masked tags will be unmasked after the queries return from the
	 * service.
	 */
	@Override
	public List<MetricQuery> getMatchingQueries(MetricQuery query) {

		Map<String, String> replacementMap = null;

		if (isTSDBRegexQuery(query)) {
			replacementMap = maskQuery(query);
		}

		List<MetricQuery> resultQueries = _discoveryService.getMatchingQueries(query);

		if (replacementMap != null && !replacementMap.isEmpty()) {
			for (MetricQuery metricQuery : resultQueries) {
				demaskQuery(metricQuery, replacementMap);
			}
		}

		return resultQueries;
	}

	/**
	 * Remove the masks from the query, using the given replacement map. The tagExpression will be URL encoded, to allow
	 * the usage of tsdb filters.
	 *
	 * @param metricQuery - the metric query to demask
	 * @param replacementMap - a map with replacements for the masks
	 */
	void demaskQuery(MetricQuery metricQuery, Map<String, String> replacementMap) {

		Map<String, String> demaskedTags = new LinkedHashMap<>();
		for (Map.Entry<String, String> tag : metricQuery.getTags().entrySet()) {
			String tagExpression = tag.getValue();
			if (replacementMap.containsKey(tagExpression)) {
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
		metricQuery.setTags(demaskedTags);
	}

	/**
	 * Mask the filters in the given query. Will exchange the tagExpression with a hash of the expression.
	 *
	 * @param query - the query to mask
	 * @return a map with the masks and the regexes they replaced.
	 */
	Map<String, String> maskQuery(MetricQuery query) {

		Map<String, String> replacementMap = new LinkedHashMap<>();
		Map<String, String> maskedTags = new LinkedHashMap<>();

		for (Map.Entry<String, String> tag : query.getTags().entrySet()) {
			String tagExpression = tag.getValue();
			String maskedExpression = tagExpression;
			if (checkFilterExpression(tagExpression)) {
				maskedExpression = String.valueOf(tagExpression.hashCode());
				replacementMap.put(maskedExpression, tagExpression);
			}
			maskedTags.put(tag.getKey(), maskedExpression);
		}
		query.setTags(maskedTags);
		return replacementMap;
	}

	@Override
	public boolean isWildcardQuery(MetricQuery query) {
		return _discoveryService.isWildcardQuery(query);
	}

	/**
	 * Check if the given query has a tag that includes a tsdb filter
	 *
	 * @param query - the query to check
	 * @return is there a filter?
	 */
	private boolean isTSDBRegexQuery(MetricQuery query) {

		for (String tagExpression : query.getTags().values()) {
			if (checkFilterExpression(tagExpression)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if the given expression is a tsdb filter
	 *
	 * @param tagExpression - the expression to check
	 * @return is there a filter?
	 */
	private boolean checkFilterExpression(String tagExpression) {
		for (String tsdbFilter : TSDB_FILTERS) {
			if (tagExpression.startsWith(tsdbFilter)) {
				return true;
			}
		}
		return false;
	}
}
