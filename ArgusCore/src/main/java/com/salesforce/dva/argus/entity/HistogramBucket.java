package com.salesforce.dva.argus.entity;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * HistogramBucket object that encompasses the lower, upper bound of this histogram bucket.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class HistogramBucket implements Serializable, Comparable<HistogramBucket> {
    private static final long serialVersionUID = 1L;
    private float lowerBound;
    private float upperBound;

    public HistogramBucket(int lowerBound, int upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public HistogramBucket(float lowerBound, float upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public HistogramBucket(String value) {
        String[] bounds = value.split(",");
        this.lowerBound = Float.parseFloat(bounds[0].trim());
        this.upperBound = Float.parseFloat(bounds[1].trim());
    }
    
    public float getLowerBound() {
        return lowerBound;
    }
    
    public float getUpperBound() {
        return upperBound;
    }

    @Override
    public int compareTo(HistogramBucket that) {
        if(this.equals(that)){
            return 0;
        } else {
            int lowerBoundCompare = Float.compare(this.lowerBound, that.lowerBound); 
            if(lowerBoundCompare !=0) return lowerBoundCompare;
            else return Float.compare(this.upperBound, that.upperBound);
        }
    }
    
    @Override
    @JsonValue
    public String toString() {
        return lowerBound + "," + upperBound;
    }
}