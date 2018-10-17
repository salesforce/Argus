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

package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;

import java.util.*;
import java.util.stream.Collectors;

import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import weka.clusterers.SimpleKMeans;
import weka.core.*;

/**
 * Anomaly detection based on the K-means clustering algorithm. Does not make
 * a Gaussian assumption and can be applied to data from any distribution.
 *
 * Sources:
 * (i) http://www.holehouse.org/mlclass/13_Clustering.html (Andrew Ng)
 * (ii) http://trevorwhitney.com/data_mining/anomaly_detection (Clustering section)
 *
 * @author  Shouvik Mani (shouvik.mani@salesforce.com)
 */
public class AnomalyDetectionKMeansTransform extends AnomalyDetectionTransform {

    private int k;
    private List<Double> metricDataValues;
    private Instances trainingData;
    private SimpleKMeans model;
    private Instances clusterCentroids;
    private int[] centroidAssignments;
    private Map<Instance, Double> meanDistancesToCentroids;
    private static final String RESULT_METRIC_NAME = "K-means anomaly score";

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        SystemAssert.requireArgument(k > 0, "K-means anomaly detection transform requires a positive integer " +
                                            "k constant.");

        Map<Long, Double> metricData = metrics.get(0).getDatapoints();
        metricDataValues = metricData.values().stream().collect(Collectors.toList());
        if (metricData.size() == 0) throw new MissingDataException("Metric must contain data points to perform transforms.");

        try {
            trainModel(metricData);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Cluster creation unsuccessful");
        }

        Metric predictions = predictAnomalies(metricData);
        Metric predictionsNormalized = normalizePredictions(predictions);

        List<Metric> resultMetrics = new ArrayList<>();
        resultMetrics.add(predictionsNormalized);
        return resultMetrics;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        SystemAssert.requireArgument(metrics.size() == 1, "Anomaly Detection Transform can only be used with one metric.");
        SystemAssert.requireArgument(constants.size() > 0, "K-means anomaly detection transform requires a k constant.");
        SystemAssert.requireArgument(constants.size() < 2, "K-means anomaly detection transform does not support " +
                                                            "contextual anomaly detection.");

        try {
            k = Integer.valueOf(constants.get(0));
        } catch (NumberFormatException e) {
            throw new UnsupportedOperationException("K-means anomaly detection transform requires a positive integer " +
                                                    "k constant.");
        }

        return transform(null, metrics);
    }

    private void trainModel(Map<Long, Double> metricData) throws Exception {
        //Model has a single metric_value attribute
        Attribute value = new Attribute("metric_value");
        FastVector attributes = new FastVector();
        attributes.addElement(value);

        trainingData = new Instances("metric_value_data", attributes, 0);
        for (Double val : metricData.values()) {
            double[] valArray = new double[] { val };
            Instance instance = new Instance(1.0, valArray);
            trainingData.add(instance);
        }

        //Create and train the model
        model = new SimpleKMeans();
        model.setNumClusters(k);
        model.setMaxIterations(20);
        model.setPreserveInstancesOrder(true);
        model.buildClusterer(trainingData);

        clusterCentroids = model.getClusterCentroids();
        centroidAssignments = model.getAssignments();
        setMeanDistancesToCentroids();
    }

    /**
     * For each cluster, caches the mean distance from data points in the
     * cluster to the cluster centroid. Mean distances are used later in
     * anomaly score calculations.
     */
    private void setMeanDistancesToCentroids() {
        meanDistancesToCentroids = new HashMap<>();
        for (int i = 0; i < clusterCentroids.numInstances(); i++) {    //For each centroid
            int countAssignedInstances = 0;
            double sumDistancesToCentroid = 0.0;
            Instance centroidInstance = clusterCentroids.instance(i);
            for (int j = 0; j < trainingData.numInstances(); j++) {       //For each data point
                 if (i == centroidAssignments[j]) {
                     Instance valueInstance = trainingData.instance(j);
                     double distanceToCentroid = Math.abs(valueInstance.value(0) -
                                                    centroidInstance.value(0));
                     sumDistancesToCentroid += distanceToCentroid;
                     countAssignedInstances++;
                 }
            }
            double meanDistanceToCentroid = sumDistancesToCentroid / countAssignedInstances;
            meanDistancesToCentroids.put(centroidInstance, meanDistanceToCentroid);
        }
    }

    /**
     * Assigns an anomaly score to each data point, indicating how likely it is
     * to be an anomaly relative to other points.
     */
    private Metric predictAnomalies(Map<Long, Double> metricData) {
        Metric predictions = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, Double> predictionDatapoints = new HashMap<>();

        for (Map.Entry<Long, Double> entry : metricData.entrySet()) {
            Long timestamp = entry.getKey();
            double value = entry.getValue();
            try {
                double anomalyScore = calculateAnomalyScore(value);
                predictionDatapoints.put(timestamp, anomalyScore);
            } catch (ArithmeticException e) {
                continue;
            }
        }

        predictions.setDatapoints(predictionDatapoints);
        return predictions;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ANOMALY_KMEANS.name();
    }

    @Override
    public String getResultMetricName() {
        return RESULT_METRIC_NAME;
    }

    /**
     * Calculates the relative distance of the data point to the centroid,
     * which is the ratio of the distance of the point to the centroid to
     * the mean distance of all points in that cluster to the centroid.
     *
     * Anomaly score here is defined as how "far" a data point is from its
     * assigned centroid. Relative distance is used to ensure normalization
     * of distances since clusters can have different densities.
     *
     * @param value value the value of the data point
     * @return the relative distance of the data point from the centroid
     */
    @Override
    public double calculateAnomalyScore(double value) {
        int instanceIndex = metricDataValues.indexOf(value);
        Instance valueInstance = trainingData.instance(instanceIndex);
        //Centroid that is assigned to valueInstance
        Instance centroidInstance = clusterCentroids.instance(centroidAssignments[instanceIndex]);

        if (meanDistancesToCentroids.get(centroidInstance) == 0.0) {
            throw new ArithmeticException("Cannot divide by 0");
        }

        double distanceToCentroid = Math.abs(valueInstance.value(0) - centroidInstance.value(0));
        double relativeDistanceToCentroid = distanceToCentroid / meanDistancesToCentroids.get(centroidInstance);
        return relativeDistanceToCentroid;
    }
}
