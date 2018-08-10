package com.salesforce.dva.argus.service.metric.transform;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.NumberOperations;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemAssert;

public class MaxDatapointTransform implements Transform {

    private Map.Entry<Long, Number> updateChunk(FilterType filter, List<Map.Entry<Long, Number>> chunkValues) {
        switch (filter) {
        case NTH:
            return chunkValues.get(0);
        case MAX:
            return Collections.max(chunkValues, new Comparator<Map.Entry<Long, Number>>() {

                @Override
                public int compare(Entry<Long, Number> o1, Entry<Long, Number> o2) {
                    return NumberOperations.compare(o1.getValue(), o2.getValue());
                }

            });
        case MIN:
            return Collections.min(chunkValues, new Comparator<Map.Entry<Long, Number>>() {

                @Override
                public int compare(Entry<Long, Number> o1, Entry<Long, Number> o2) {
                    return NumberOperations.compare(o1.getValue(), o2.getValue());
                }

            });
        case AVG:
            Number[] vals = getNumbers(chunkValues);
            return new SimpleEntry<Long, Number>(chunkValues.get(0).getKey(), NumberOperations.mean(vals));
        case MEDIAN:
            double middle = 0.5 * (chunkValues.size() + 1);
            if (chunkValues.size() == 1) {
                return chunkValues.get(0);
            }

            Collections.sort(chunkValues, new Comparator<Map.Entry<Long, Number>>() {

                @Override
                public int compare(Entry<Long, Number> o1, Entry<Long, Number> o2) {
                    return NumberOperations.compare(o1.getValue(), o2.getValue());
                }
            });

            int lowIndex = (int) Math.floor(middle);
            int highIndex = lowIndex;
            double diff = middle - lowIndex;
            Long timestamp = chunkValues.get(lowIndex - 1).getKey();
            if (diff != 0 && lowIndex + 1 <= chunkValues.size()) {
                timestamp = Math.max(chunkValues.get(lowIndex - 1).getKey(), chunkValues.get(lowIndex).getKey());
                highIndex = lowIndex + 1;
            }
            Number low = chunkValues.get(lowIndex - 1).getValue();
            Number high = chunkValues.get(highIndex - 1).getValue();
            Number med = low;
            if (diff != 0) {
                med = NumberOperations.add(low, NumberOperations.multiply(diff, NumberOperations.subtract(high, low)));
            }
            return new SimpleEntry<Long, Number>(timestamp, med);
        case SAMPLE:
            throw new IllegalStateException("Shouldn't be looking at chunks with random sample");
        case ZIMSUM:
            Number zimsum = 0;
            for (Map.Entry<Long, Number> elem : chunkValues) {
                if (elem.getValue() != null) {
                    zimsum = NumberOperations.add(zimsum, elem.getValue());
                }
            }
            return new SimpleEntry<Long, Number>(chunkValues.get(0).getKey(), zimsum);
        case SUM:
            Number sum = 0;
            for (Map.Entry<Long, Number> elem : chunkValues) {
                sum = NumberOperations.add(sum, elem.getValue());
            }
            return new SimpleEntry<Long, Number>(chunkValues.get(0).getKey(), sum);
        case COUNT:
            int count = 0;
            for (Map.Entry<Long, Number> elem : chunkValues) {
                if (elem.getValue() != null) {
                    count++;
                }
            }
            return new SimpleEntry<Long, Number>(chunkValues.get(0).getKey(), count);
        default:
            throw new IllegalArgumentException(
                    "Max Datapoint Transform does not support the filter algorithm " + filter);
        }
    }

    private Number[] getNumbers(List<Map.Entry<Long, Number>> chunkValues) {
        Number[] vals = new Number[chunkValues.size()];
        int i = 0;
        for (Map.Entry<Long, Number> val : chunkValues) {
            vals[i] = val.getValue();
            i++;
        }
        return vals;
    }

    private Map<Long, Number> sample(Map<Long, Number> originalDatapoints, int maxDps) {
        Map<Long, Number> filteredDatapoints = new HashMap<>();
        List<Long> timestamps = new ArrayList<>(originalDatapoints.keySet());
        Random random = new Random();

        for (int i = 0; i < maxDps; i++) {
            int index = random.nextInt(timestamps.size());
            Long time = timestamps.get(index);
            filteredDatapoints.put(time, originalDatapoints.get(time));
            timestamps.remove(index); // want distinct elements
        }

        return filteredDatapoints;
    }

    private Map<Long, Number> filter(Map<Long, Number> originalDatapoints, int maxDps, int n, int smallerChunks,
            FilterType filter, long minInterval) {
        if (filter == FilterType.SAMPLE) {
            return sample(originalDatapoints, maxDps);
        }

        Map<Long, Number> filteredDatapoints = new HashMap<>();
        TreeMap<Long, Number> sortedDatapoints = new TreeMap<>(Collections.reverseOrder());
        sortedDatapoints.putAll(originalDatapoints);
        int chunk = 0;
        int element = 0;
        List<Map.Entry<Long, Number>> chunkValues = new ArrayList<>();
        for (Map.Entry<Long, Number> entry : sortedDatapoints.entrySet()) {
            chunkValues.add(entry);
            element++;
            if ((chunk < smallerChunks && element >= n - 1) || element >= n) {
                // check that this chunk will span at least the requisite amount of time
                Long nextKey = sortedDatapoints.higherKey(entry.getKey());
                if (nextKey == null || (chunkValues.get(0).getKey() - nextKey >= minInterval)) {
                    Map.Entry<Long, Number> chunkRepresentative = updateChunk(filter, chunkValues);
                    filteredDatapoints.put(chunkRepresentative.getKey(), chunkRepresentative.getValue());
                    // start a new chunk
                    element = 0;
                    chunk++;
                    chunkValues = new ArrayList<>();
                }
            }
        }

        // handle any remaining values -- should only have remaining due to minInterval
        if (!chunkValues.isEmpty()) {
            Map.Entry<Long, Number> chunkRepresentative = updateChunk(filter, chunkValues);
            filteredDatapoints.put(chunkRepresentative.getKey(), chunkRepresentative.getValue());
        }
        return filteredDatapoints;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException(
                "Max Datapoint Transform must be used with a maximum number of datapoints");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(constants.size() >= 1,
                "Max Datapoint Transform requires at least a maximum number of datapoints");
        int maxDps = Integer.parseInt(constants.get(0));

        long minInterval = 0;
        FilterType filter = FilterType.AVG;
        if (constants.size() > 1) {
            try {
                // first check for a time window
                minInterval = getWindowInSeconds(constants.get(1)) * 1000;

                if (constants.size() > 2) {
                    filter = FilterType.getType(constants.get(2));
                }
            } catch (IllegalArgumentException iae) {
                filter = FilterType.getType(constants.get(1));
            }
        }

        for (Metric metric : metrics) {
            if (metric.getDatapoints().size() > maxDps) {
                int n = (int) Math.ceil(metric.getDatapoints().size() * 1.0 / maxDps);
                int smallerChunks = (maxDps * n) - metric.getDatapoints().size();
                metric.setDatapoints(filter(metric.getDatapoints(), maxDps, n, smallerChunks, filter, minInterval));
            }
        }

        return metrics;
    }

    @Override
    public List<Metric> transform(List<Metric>... metrics) {
        throw new UnsupportedOperationException("Max Datapoint Transform doesn't need a list of lists");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.MAX_DATAPOINT.name();
    }

    private long getWindowInSeconds(String window) {
        MetricReader.TimeUnit timeunit = null;

        try {
            timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));
            long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));
            return timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            throw new IllegalArgumentException("Fail to parse window size!");
        }
    }

    public enum FilterType {
        NTH("nth"),
        AVG("avg"),
        MAX("max"),
        MIN("min"),
        MEDIAN("median"),
        SAMPLE("sample"),
        ZIMSUM("zimsum"),
        SUM("sum"),
        DEV("deviation"),
        COUNT("count"),
        PERCENTILE("percentile");

        public final String filterName;

        private FilterType(String filterName) {
            this.filterName = filterName;
        }

        private static FilterType getType(String filterType) {
            if (filterType != null) {
                for (FilterType type : FilterType.values()) {
                    if (filterType.equalsIgnoreCase(type.getName())) {
                        return type;
                    }
                }
            }

            throw new IllegalArgumentException("Illegal type: " + filterType + ". Please provide a valid type.");
        }

        public String getName() {
            return filterName;
        }
    }
}
