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

	private final Logger _logger = LoggerFactory.getLogger(getClass());

	@Inject
	private CachedDiscoveryService _discoveryService;

	private static final List<String> TSDB_FILTERS = Arrays.asList("regexp",
			"literal_or",
			"iliteral_or",
			"not_literal_or",
			"not_iliteral_or",
			"wildcard",
			"iwildcard",
			"regexp");

	@Inject
	private TSDBAwareDiscoveryService(SystemConfiguration config)
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

		MetricQuery queryToDiscover = new MetricQuery(query);

		MetricQueryMasker metricQueryMasker = new MetricQueryMasker();

		if (isTSDBRegexQuery(query)) {
			queryToDiscover = metricQueryMasker.maskQuery(query);
		}

		List<MetricQuery> resultQueries = _discoveryService.getMatchingQueries(queryToDiscover);

		if (metricQueryMasker.isQueryMasked()) {
			for (MetricQuery metricQuery : resultQueries) {
				metricQueryMasker.demaskQuery(metricQuery);
			}
		}

		return resultQueries;
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
	public static boolean checkFilterExpression(String tagExpression) {
		for (String tsdbFilter : TSDB_FILTERS) {
			if (tagExpression.startsWith(tsdbFilter)) {
				return true;
			}
		}
		return false;
	}
}
