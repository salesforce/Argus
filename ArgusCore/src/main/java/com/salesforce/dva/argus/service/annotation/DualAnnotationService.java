package com.salesforce.dva.argus.service.annotation;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

/*
 * Annotation service that reads annotations from both TSDB, ElasticSearch.
 * It defaults to writing annotations to ElasticSearch.
 */
@Singleton
public class DualAnnotationService extends DefaultService implements AnnotationStorageService {
    private static Logger logger = LoggerFactory.getLogger(DualAnnotationService.class);

    private final AnnotationStorageService _elasticSearchAnnotationService;
    private final TSDBService _tsdbService;
    private final long annotationTransitionEpochMs;
    private final ExecutorService _executorService;

    @Inject
    protected DualAnnotationService(SystemConfiguration config,
            @NamedBinding AnnotationStorageService elasticSearchAnnotationService,
            TSDBService tsdbService) {
        super(config);
        requireArgument(elasticSearchAnnotationService != null, "ElasticSearchAnnotationService cannot be null.");
        requireArgument(tsdbService != null, "TSDBService cannot be null.");
        _elasticSearchAnnotationService = elasticSearchAnnotationService;
        _tsdbService = tsdbService;
        this.annotationTransitionEpochMs = Long.parseLong(config.getValue(Property.ANNOTATION_TRANSITION_EPOCH_MS.getName(),
                Property.ANNOTATION_TRANSITION_EPOCH_MS.getDefaultValue()));
        int connCount = Integer.parseInt(config.getValue(Property.ANNOTATION_THREADPOOL_CONNECTION_COUNT.getName(),
                Property.ANNOTATION_THREADPOOL_CONNECTION_COUNT.getDefaultValue()));
        requireArgument(connCount >= 2, "Connection count should be >=2");
        _executorService = Executors.newFixedThreadPool(connCount);
    }

    @Override
    public void dispose() {
        super.dispose();
        _elasticSearchAnnotationService.dispose();
        _tsdbService.dispose();
        _executorService.shutdownNow();
        try {
            _executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.debug("Await Termination Interrupted", e);
        }
    }

    @Override
    public void putAnnotations(List<Annotation> annotations) {
        _elasticSearchAnnotationService.putAnnotations(annotations);
    }

    @Override
    public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
        List<Annotation> annotations = new ArrayList<>();
        for (AnnotationQuery query : queries) {
            convertTimestampToMillis(query);

            if(isQueryHavingEpochCutOff(query)){
                //split annotation query to TSDB and ES
                annotations.addAll(runSplitQueries(query));
            } else if (query.getEndTimestamp() < annotationTransitionEpochMs){
                // annotation query to TSDB
                annotations.addAll(_tsdbService.getAnnotations(Arrays.asList(query)));
            } else {
                // annotation query to ES
                annotations.addAll(_elasticSearchAnnotationService.getAnnotations(Arrays.asList(query)));
            }
        }
        return annotations;
    }

    protected  boolean isQueryHavingEpochCutOff(AnnotationQuery query){
        return query.getStartTimestamp() < annotationTransitionEpochMs && query.getEndTimestamp() >= annotationTransitionEpochMs;
    }

    protected List<Annotation> runSplitQueries(AnnotationQuery original) {
        logger.info("Reading annotations from TSDB and ES");
        Map<AnnotationQuery, Future<List<Annotation>>> queryFutureMap = new HashMap<>();
        List<Annotation> annotations = new ArrayList<>();
        List<AnnotationQuery> queries = splitQuery(original);

        queryFutureMap.put(queries.get(0), _executorService.submit(new QueryWorker(AnnotationServiceType.TSDB, queries.get(0))));
        queryFutureMap.put(queries.get(1), _executorService.submit(new QueryWorker(AnnotationServiceType.ES, queries.get(1))));

        for (Entry<AnnotationQuery, Future<List<Annotation>>> entry : queryFutureMap.entrySet()) {
            try {
                annotations.addAll(entry.getValue().get());
            } catch (InterruptedException | ExecutionException e) {
                logger.warn("Failed to get annotations. Reason: " + e.getMessage());
            }
        }
        return annotations;
    }

    protected List<AnnotationQuery> splitQuery(AnnotationQuery original) {
        List<AnnotationQuery> queries = new ArrayList<AnnotationQuery>();
        queries.add(new AnnotationQuery(original.getScope(),
                original.getMetric(),
                original.getTags(),
                original.getType(),
                original.getStartTimestamp(),
                annotationTransitionEpochMs));

        queries.add(new AnnotationQuery(original.getScope(),
                original.getMetric(),
                original.getTags(),
                original.getType(),
                annotationTransitionEpochMs,
                original.getEndTimestamp()));
        return queries;
    }

    protected void convertTimestampToMillis(AnnotationQuery query) {
        long queryStart = query.getStartTimestamp();
        long queryEnd = query.getEndTimestamp();
        if (queryStart < 100000000000L) query.setStartTimestamp(queryStart * 1000);
        if (queryEnd < 100000000000L) query.setEndTimestamp(queryEnd * 1000);
    }

    public enum Property {
        ANNOTATION_THREADPOOL_CONNECTION_COUNT("service.property.annotation.threadpool.connection.count", "2"),
        ANNOTATION_TRANSITION_EPOCH_MS("service.property.annotation.transition.epoch.ms", "1559153225000");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        private String getDefaultValue() {
            return _defaultValue;
        }

        private String getName() {
            return _name;
        }
    }

    private enum AnnotationServiceType {
        TSDB,
        ES;
    }
    
    /**
     * Helper class used to parallelize query execution.
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    class QueryWorker implements Callable<List<Annotation>> {
        private final AnnotationServiceType  _annotationServiceType;
        private final AnnotationQuery _annotationQuery;

        /**
         * Creates a new QueryWorker object.
         *
         * @param  annotationServiceType  ES or TSDB annotation endpoint type
         * @param  annotationQuery The annotation query issued
         */
        public QueryWorker(AnnotationServiceType annotationServiceType, AnnotationQuery annotationQuery) {
            this._annotationServiceType = annotationServiceType;
            this._annotationQuery = annotationQuery;
        }

        @Override
        public List<Annotation> call() {
            List<Annotation> annotations;
            if(_annotationServiceType.equals(AnnotationServiceType.TSDB)){
                annotations = _tsdbService.getAnnotations(Arrays.asList(_annotationQuery));
                logger.info("Read {} annotations from TSDB", annotations.size());
            } else{
                annotations = _elasticSearchAnnotationService.getAnnotations(Arrays.asList(_annotationQuery));
                logger.info("Read {} annotations from ES", annotations.size());
            }
            return annotations;
        }
    }
}