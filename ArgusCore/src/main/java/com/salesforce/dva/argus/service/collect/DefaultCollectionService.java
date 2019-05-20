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

package com.salesforce.dva.argus.service.collect;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ANNOTATION;
import static com.salesforce.dva.argus.service.MQService.MQQueue.METRIC;
import static com.salesforce.dva.argus.service.MQService.MQQueue.HISTOGRAM;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.HistogramBucket;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.TSDBEntity;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.CollectionService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.NamespaceService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.WardenService;
import com.salesforce.dva.argus.service.WardenService.PolicyCounter;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * Default implementation of the CollectionService interface.
 *
 * @author  tvaline@salesforce.com, rsarkapally@salesforce.com
 */
public class DefaultCollectionService extends DefaultJPAService implements CollectionService {

    //~ Static fields/initializers *******************************************************************************************************************

    protected static final int MAX_ANNOTATION_SIZE_BYTES = 2000;
    private static final int BATCH_METRICS = 50;
    private static final int MAX_HISTOGRAM_BUCKETS = 100;
    private static final Logger _logger = LoggerFactory.getLogger(DefaultCollectionService.class);

    //~ Instance fields ******************************************************************************************************************************

    @Inject
    Provider<EntityManager> emf;
    private final MQService _mqService;
    private final TSDBService _tsdbService;
    private final AnnotationStorageService _annotationStorageService;
    private final SchemaService _schemaService;
    private final WardenService _wardenService;
    private final MonitorService _monitorService;
    private final NamespaceService _namespaceService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultCollectionService object.
     *
     * @param  mqService                  The MQ service implementation with which to queue and dequeue submitted metrics and annotations
     * @param  tsdbService                The TSDB service implementation with which to write metrics and annotations into storage.
     * @param  auditService               The audit service instance to use. Cannot be null.
     * @param  annotationStorageService   The audit service instance to use. Cannot be null.
     * @param  configuration              The system configuration instance to use. Cannot be null.
     * @param  schemaService              The schema service instance to use. Cannot be null.
     * @param  wardenService              The warden service instance to use. Cannot be null.
     * @param  monitorService             The monitor service instance to use. Cannot be null.
     * @param  namespaceService           The namespace service instance to use.  Cannot be null.
     */
    @Inject
    DefaultCollectionService(MQService mqService, TSDBService tsdbService, AuditService auditService,
            AnnotationStorageService annotationStorageService, SystemConfiguration configuration, SchemaService schemaService, WardenService wardenService,
            MonitorService monitorService, NamespaceService namespaceService) {
        super(auditService, configuration);
        _mqService = mqService;
        _tsdbService = tsdbService;
        _annotationStorageService = annotationStorageService;
        _schemaService = schemaService;
        _wardenService = wardenService;
        _monitorService = monitorService;
        _namespaceService = namespaceService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void submitMetric(PrincipalUser submitter, Metric metric) {
        submitMetrics(submitter, Arrays.asList(new Metric[] { metric }));
    }

    @Override
    public void submitMetrics(PrincipalUser submitter, List<Metric> metrics) {
        requireNotDisposed();
        requireArgument(submitter != null, "Submitting user cannot be null.");
        requireArgument(metrics != null, "The list of metrics to submit cannot be null.");

        MetricData metricData = _parseMetricData(metrics);

        _monitorService.modifyCounter(Counter.DATAPOINT_WRITES, metricData.getDataPointsSize(), null);
        if (!submitter.isPrivileged() && _wardenService.isWardenServiceEnabled()) {
            checkSubmitMetricPolicyRequirementsMet(submitter, metrics);
            _logger.info("Capturing warden metrics.");
            _wardenService.modifyPolicyCounter(submitter, PolicyCounter.METRICS_PER_HOUR, metrics.size());
            _wardenService.modifyPolicyCounter(submitter, PolicyCounter.DATAPOINTS_PER_HOUR, metricData.getDataPointsSize());

            Long minResolutionDataPoints = metricData.getMinResolutionDataPointsAcrossAllMetrics();

            if (minResolutionDataPoints != null) {
                _wardenService.updatePolicyCounter(submitter, PolicyCounter.MINIMUM_RESOLUTION_MS, minResolutionDataPoints);
            }
            metrics = _getAllowedMetrics(metrics, submitter);
            _logger.info("User metric data is: Metrics size - {}, Datapoints size - {}, Minimum resolution - {}.", metrics.size(),
                    metricData.getDataPointsSize(), metricData.getMinResolutionDataPointsAcrossAllMetrics());
        }

        List<ArrayList<Metric>> batches = _batchMetrics(metrics);

        _mqService.enqueue(METRIC.getQueueName(), batches);
    }

    @Override
    public void submitAnnotation(PrincipalUser submitter, Annotation annotation) {
        submitAnnotations(submitter, Arrays.asList(new Annotation[] { annotation }));
    }

    @Override
    public void submitAnnotations(PrincipalUser submitter, List<Annotation> annotations) {
        requireNotDisposed();
        requireArgument(submitter != null, "Submitting user cannot be null.");
        requireArgument(annotations != null, "The list of annotations to submit cannot be null.");
        checkSubmitAnnotationPolicyRequirementsMet(submitter, annotations);

        List<Annotation> putAnnotationList = new LinkedList<>();
        /* Replace unsupported characters in annotation */
        for (Annotation annotation : annotations) {
            if (annotation.computeSizeBytes() > MAX_ANNOTATION_SIZE_BYTES) {
                _logger.debug("Annotation size of {} bytes exceeded max size {} allowed for annotation {}.",
                        annotation.computeSizeBytes(),
                        MAX_ANNOTATION_SIZE_BYTES,
                        annotation);
                Map<String, String> tags = new HashMap<>();
                tags.put("source", annotation.getSource());
                _monitorService.modifyCounter(Counter.ANNOTATION_DROPS_MAXSIZEEXCEEDED, 1, tags);
            } else {
                annotation.setScope(TSDBEntity.replaceUnsupportedChars(annotation.getScope()));
                annotation.setMetric(TSDBEntity.replaceUnsupportedChars(annotation.getMetric()));
                Map<String, String> filteredTags = new HashMap<>();
                for (String tagKey : annotation.getTags().keySet()) {
                    filteredTags.put(TSDBEntity.replaceUnsupportedChars(tagKey), TSDBEntity.replaceUnsupportedChars(annotation.getTags().get(tagKey)));
                }
                annotation.setTags(filteredTags);

                putAnnotationList.add(annotation);
            }
        }
        _monitorService.modifyCounter(Counter.ANNOTATION_WRITES, putAnnotationList.size(), null);
        if (!putAnnotationList.isEmpty()) {
            _mqService.enqueue(ANNOTATION.getQueueName(), putAnnotationList);
        }
    }

    @Override
    public List<Metric> commitMetrics(int messageCount, int timeout) {
        requireNotDisposed();
        requireArgument(messageCount > 0, "Message count must be greater than zero.");
        requireArgument(timeout > 0, "The timeout in milliseconds must be greater than zero.");

        CollectionType type = new ObjectMapper().getTypeFactory().constructCollectionType(ArrayList.class, Metric.class);
        List<ArrayList<Metric>> dequeuedMessages = _mqService.dequeue(METRIC.getQueueName(), type, timeout, messageCount);
        List<Metric> dequeued = new ArrayList<Metric>();

        for (List<Metric> list : dequeuedMessages) {
            dequeued.addAll(list);
        }
        if (!dequeued.isEmpty()) {
            _tsdbService.putMetrics(dequeued);
            _logger.debug("Committed {} metrics.", dequeued.size());
        }
        return dequeued;
    }

    @Override
    public int commitMetricSchema(int messageCount, int timeout) {
        requireNotDisposed();
        requireArgument(messageCount > 0, "Message count must be greater than zero.");
        requireArgument(timeout > 0, "The timeout in milliseconds must be greater than zero.");

        CollectionType type = new ObjectMapper().getTypeFactory().constructCollectionType(ArrayList.class, Metric.class);
        List<ArrayList<Metric>> dequeuedMessages = _mqService.dequeue(METRIC.getQueueName(), type, timeout, messageCount);
        List<Metric> dequeued = new ArrayList<Metric>();

        for (List<Metric> list : dequeuedMessages) {
            dequeued.addAll(list);
        }
        if (!dequeued.isEmpty()) {
            _schemaService.put(dequeued);
            _logger.debug("Committed {} metrics for schema records creation.", dequeued.size());
        }
        return dequeued.size();
    }

    @Override
    public int commitAnnotations(int annotationCount, int timeout) {
        requireNotDisposed();
        requireArgument(annotationCount > 0, "Annotation count must be greater than zero.");
        requireArgument(timeout > 0, "The timeout in milliseconds must be greater than zero.");

        List<Annotation> dequeued = _mqService.dequeue(ANNOTATION.getQueueName(), Annotation.class, timeout, annotationCount);

        if (!dequeued.isEmpty()) {
            _annotationStorageService.putAnnotations(dequeued);
            _logger.debug("Committed {} annotations.", dequeued.size());
        }
        return dequeued.size();
    }

    @Override
    public int commitHistograms(int histogramCount, int timeout) {
        requireNotDisposed();
        requireArgument(histogramCount > 0, "Histogram count must be greater than zero.");
        requireArgument(timeout > 0, "The timeout in milliseconds must be greater than zero.");

        List<Histogram> dequeued = _mqService.dequeue(HISTOGRAM.getQueueName(), Histogram.class, timeout, histogramCount);

        if (!dequeued.isEmpty()) {
            _tsdbService.putHistograms(dequeued);
            _logger.debug("Committed {} histograms.", dequeued.size());
        }
        return dequeued.size();
    }

    @Override
    public void submitHistogram(PrincipalUser submitter, Histogram histogram) {
        submitHistograms(submitter, Arrays.asList(new Histogram[] { histogram }));
    }

    @Override
    public void submitHistograms(PrincipalUser submitter, List<Histogram> histograms) {
        requireNotDisposed();
        requireArgument(submitter != null, "Submitting user cannot be null.");
        requireArgument(histograms != null, "The list of histograms to submit cannot be null.");
        checkSubmitHistogramPolicyRequirementsMet(submitter, histograms);

        List<Histogram> filteredHistograms = new ArrayList<>();

        /* Replace unsupported characters in histogram and validate bounds */
        for (Histogram histogram : histograms) {
            if(histogram.getBuckets() == null || histogram.getBuckets().isEmpty()){
                _logger.warn("Histogram buckets is null or empty. Dropping this histogram");
                _monitorService.modifyCounter(Counter.HISTOGRAM_DROPPED, 1, null);
            } else if (histogram.getBuckets().size() > MAX_HISTOGRAM_BUCKETS) {
                _logger.warn("Histogram buckets exceeded max size {}. Dropping this histogram", MAX_HISTOGRAM_BUCKETS);
                _monitorService.modifyCounter(Counter.HISTOGRAM_DROPPED, 1, null);
            } else {
                boolean boundsCheck = true;
                for(HistogramBucket histogramBucket: histogram.getBuckets().keySet()){
                    if(histogramBucket.getLowerBound() >= histogramBucket.getUpperBound()){
                        _logger.warn("Histogram lower bound, must be less than upper bound. Dropping this histogram");
                        _monitorService.modifyCounter(Counter.HISTOGRAM_DROPPED, 1, null);
                        boundsCheck = false;
                        break;
                    }
                }
                if(boundsCheck == false) continue;

                histogram.setScope(TSDBEntity.replaceUnsupportedChars(histogram.getScope()));
                histogram.setMetric(TSDBEntity.replaceUnsupportedChars(histogram.getMetric()));
                Map<String, String> filteredTags = new HashMap<>();
                for (String tagKey : histogram.getTags().keySet()) {
                    filteredTags.put(TSDBEntity.replaceUnsupportedChars(tagKey), TSDBEntity.replaceUnsupportedChars(histogram.getTags().get(tagKey)));
                }
                histogram.setTags(filteredTags);

                filteredHistograms.add(histogram);
            }
        }

        _monitorService.modifyCounter(Counter.HISTOGRAM_WRITES, filteredHistograms.size(), null);
        _mqService.enqueue(HISTOGRAM.getQueueName(), filteredHistograms);
    }

    @Override
    public void dispose() {
        super.dispose();
    }

    private void checkSubmitAnnotationPolicyRequirementsMet(PrincipalUser submitter, List<Annotation> annotations) {
        assert (submitter != null) : "Submitter should not be null.";
        assert (annotations != null) : "List of annotations should not be null.";
        _logger.warn("Policy checks for submitting annotations are not yet implemented.");
    }

    private void checkSubmitMetricPolicyRequirementsMet(PrincipalUser submitter, List<Metric> metrics) {
        assert (submitter != null) : "Submitter should not be null.";
        assert (metrics != null) : "List of metrics should not be null.";
        _wardenService.assertSubSystemUsePermitted(submitter, SubSystem.POSTING);
    }

    private void checkSubmitHistogramPolicyRequirementsMet(PrincipalUser submitter, List<Histogram> histograms) {
        assert (submitter != null) : "Submitter should not be null.";
        assert (histograms != null) : "List of histograms should not be null.";
        _logger.warn("Policy checks for submitting histograms are not yet implemented.");
    }

    /*
     * Parses the metrics, and gets data points size and minimum resolution of data points across all metrics. To get minimum resolution, calculate
     * the minimum of the minimum resolution across each metric category If there is not more than 1 data point in a metric category, then don't
     * compute timestamp diff.
     */
    private MetricData _parseMetricData(List<Metric> metrics) {
        long dataPointsSize = 0;
        Long minDiff = null;
        Map<Metric, List<Long>> metricCategoryMap = new HashMap<Metric, List<Long>>();
        List<Long> metricList;

        for (Metric metric : metrics) {
            dataPointsSize += metric.getDatapoints().size();

            Metric metricCategory = new Metric(metric.getScope(), metric.getMetric());

            metricCategory.setTags(metric.getTags());
            if (metricCategoryMap.containsKey(metricCategory)) {
                metricList = metricCategoryMap.get(metricCategory);
                metricList.addAll(metric.getDatapoints().keySet());
            } else {
                metricCategoryMap.put(metricCategory, new ArrayList<Long>(metric.getDatapoints().keySet()));
            }
        }

        Metric minResolutionMetric = null;
        for (Entry<Metric, List<Long>> entry : metricCategoryMap.entrySet()) {
            Long minDiffInMetricCategory = null;
            List<Long> dataPointsTimeStampList = entry.getValue();

            if (dataPointsTimeStampList.size() >= 2) {
                Collections.sort(dataPointsTimeStampList);
                minDiffInMetricCategory = dataPointsTimeStampList.get(1) - dataPointsTimeStampList.get(0);
                for (int i = 2; i < dataPointsTimeStampList.size(); i++) {
                    minDiffInMetricCategory = Math.min(minDiffInMetricCategory, dataPointsTimeStampList.get(i) - dataPointsTimeStampList.get(i - 1));
                }
                if (minDiff == null) {
                    minDiff = minDiffInMetricCategory;
                    minResolutionMetric = entry.getKey();
                }else {
                    if(minDiff>minDiffInMetricCategory) {
                        minResolutionMetric = entry.getKey();
                        minDiff = minDiffInMetricCategory;
                    }
                }
            }
        }

        if(minDiff!=null && minDiff<PolicyCounter.MINIMUM_RESOLUTION_MS.getDefaultValue()) {
            _logger.error("Minimum resolution policy has been violated for the metric " + minResolutionMetric.toString());
        }
        return new MetricData(dataPointsSize, minDiff);
    }

    private List<Metric> _getAllowedMetrics(List<Metric> metrics, PrincipalUser submitter) {
        List<Metric> allowedMetrics = new ArrayList<Metric>(metrics.size());

        for (Metric m : metrics) {
            if (_namespaceService.isPermitted(m.getNamespace(), submitter)) {
                allowedMetrics.add(m);
            }
        }
        return allowedMetrics;
    }

    private List<ArrayList<Metric>> _batchMetrics(List<Metric> metrics) {
        List<ArrayList<Metric>> batches = new ArrayList<ArrayList<Metric>>();
        int count = 0;
        ArrayList<Metric> batch = new ArrayList<Metric>(BATCH_METRICS);

        for (Metric metric : metrics) {
            if (count == BATCH_METRICS) {
                count = 0;
                batches.add(batch);
                batch = new ArrayList<Metric>(BATCH_METRICS);
            }

            /*
             * We are doing the unsupported character replacement before we write to the queue.
             * This way the same data is seen by any downstream schema or metric consumers, and both will be in sync.
             */
            metric.setScope(TSDBEntity.replaceUnsupportedChars(metric.getScope()));
            metric.setMetric(TSDBEntity.replaceUnsupportedChars(metric.getMetric()));
            metric.setNamespace(TSDBEntity.replaceUnsupportedChars(metric.getNamespace()));
            Map<String, String> filteredTags = new HashMap<>();
            for(String tagKey : metric.getTags().keySet()) {
                filteredTags.put(TSDBEntity.replaceUnsupportedChars(tagKey), TSDBEntity.replaceUnsupportedChars(metric.getTags().get(tagKey)));
            }
            metric.setTags(filteredTags);

            batch.add(metric);
            count++;
        }
        batches.add(batch);
        return batches;
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Data structure for holding the data points and minimum resolution of data points across all metrics.
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    public static final class MetricData {

        long dataPointsSize;
        Long minResolutionDataPointsAcrossAllMetrics;

        /**
         * Creates a new MetricData object.
         *
         * @param  dataPointsSize  The number of data points in the data set.
         * @param  minDiff         The minimum time between any two data points in the set.
         */
        public MetricData(long dataPointsSize, Long minDiff) {
            this.dataPointsSize = dataPointsSize;
            this.minResolutionDataPointsAcrossAllMetrics = minDiff;
        }

        /**
         * Returns the number of data points in the data set.
         *
         * @return  The number of data points in the data set.
         */
        public long getDataPointsSize() {
            return dataPointsSize;
        }

        /**
         * Returns the minimum time between any two data points in the set.
         *
         * @return  The minimum time between any two data points in the set.
         */
        public Long getMinResolutionDataPointsAcrossAllMetrics() {
            return minResolutionDataPointsAcrossAllMetrics;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
