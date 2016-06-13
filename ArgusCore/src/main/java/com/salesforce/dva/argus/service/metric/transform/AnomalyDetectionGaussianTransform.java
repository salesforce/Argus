package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AnomalyDetectionGaussianTransform implements Transform {

    private static final String RESULT_METRIC_NAME = "probability density";

    //Parameters for Gaussian distribution
    private double mean;
    private double variance;

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        if (metrics == null) {
            throw new MissingDataException("The metrics list cannot be null or empty while performing transforms.");
        }
        if (metrics.size() != 1) {
            throw new UnsupportedOperationException("Anomaly Detection Transform can only be used with one metric.");
        }

        Metric metric = metrics.get(0);
        Map<Long, String> metricData = metric.getDatapoints();
        fitParameters(metricData);
        Metric predictions = predictAnomalies(metricData);

        List<Metric> resultMetrics = new ArrayList<>();
        resultMetrics.add(predictions);

        return resultMetrics;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ANOMALY_GAUSSIAN.name();
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Anomaly Detection Transform is not supposed to be used with constants");
    }

    @Override
    public List<Metric> transform(List<Metric>... metrics) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }

    private void fitParameters(Map<Long, String> metricData) {
        mean = getMetricMean(metricData);
        variance = getMetricVariance(metricData);
    }

    private Metric predictAnomalies(Map<Long, String> metricData) {
        Metric predictions = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
        Map<Long, String> predictionDatapoints = new HashMap<>();

        for (Entry<Long, String> entry : metricData.entrySet()) {
            Long timestamp = entry.getKey();
            String valueString = entry.getValue();
            double valueDouble = Double.parseDouble(valueString);
            double probabilityDensityDouble = (1/Math.sqrt(2 * Math.PI * variance)) *
                    Math.exp(-1 * Math.pow((valueDouble - mean), 2) / 2 * variance);
            String probabilityDensityString = String.valueOf(probabilityDensityDouble);
            predictionDatapoints.put(timestamp, probabilityDensityString);
        }

        predictions.setDatapoints(predictionDatapoints);
        return predictions;
    }

    private double getMetricMean(Map<Long, String> metricData) {
        double sum = 0;
        for (String valueString : metricData.values()) {
            double valueDouble = Double.parseDouble(valueString);
            sum += valueDouble;
        }
        return sum/metricData.size();
    }

    private double getMetricVariance(Map<Long, String> metricData) {
        double sumSquareDiff = 0;
        for (String valueString : metricData.values()) {
            double valueDouble = Double.parseDouble(valueString);
            sumSquareDiff += Math.pow((valueDouble - mean), 2);
        }
        return sumSquareDiff/metricData.size();
    }
}
