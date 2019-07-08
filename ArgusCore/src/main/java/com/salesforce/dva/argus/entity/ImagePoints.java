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

package com.salesforce.dva.argus.entity;


import org.apache.commons.lang.StringUtils;
import java.io.Serializable;

/**
 * This class represents the points in XY graph as well as the properties related to that
 */
public class ImagePoints implements Cloneable, Serializable {

    /** The first value. */
    private double firstPoint;

    /** The second value. */
    private double secondPoint;

    /** The label */
    private String label = StringUtils.EMPTY;

    /** The Color */
    private ImageProperties.ImageColors color = ImageProperties.DEFAULT_COLOR;

    /**
     *
     * @param firstPoint    Represents first point in the XY Axis
     * @param secondPoint   Represents second point in the XY Axis
     * @param label         Represents label associated with the points
     */
    public ImagePoints(double firstPoint, double secondPoint, String label) {
        this.firstPoint = firstPoint;
        this.secondPoint = secondPoint;
        this.label = label;
    }

    /**
     *
     * @param firstPoint    Represents first point in the XY Axis
     * @param secondPoint   Represents second point in the XY Axis
     * @param color         Represents color associated with the points
     */
    public ImagePoints(double firstPoint, double secondPoint, ImageProperties.ImageColors color) {
        this.firstPoint = firstPoint;
        this.secondPoint = secondPoint;
        this.color = color;
    }

    /**
     *
     * @param firstPoint    Represents first point in the XY Axis
     * @param secondPoint   Represents second point in the XY Axis
     * @param label         Represents label associated with the points
     * @param color         Represents color associated with the points
     */
    public ImagePoints(double firstPoint, double secondPoint, String label, ImageProperties.ImageColors color) {
        this.firstPoint = firstPoint;
        this.secondPoint = secondPoint;
        this.label = label;
        this.color = color;
    }

    /**
     * Gets first point in the XY Axis
     * @return  first point in the XY Axis
     */
    public double getFirstPoint() {
        return firstPoint;
    }

    /**
     * Sets first point in the XY Axis
     * @param firstPoint
     */
    public void setFirstPoint(double firstPoint) {
        this.firstPoint = firstPoint;
    }

    /**
     * Gets second point in the XY Axis
     * @return  second point in the XY Axis
     */
    public double getSecondPoint() {
        return secondPoint;
    }

    /**
     * Sets second point in the XY Axis
     * @param secondPoint
     */
    public void setSecondPoint(double secondPoint) {
        this.secondPoint = secondPoint;
    }

    /**
     * Gets the label associated with the points
     * @return Label is returned
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label associated with the points
     * @param label
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Gets the color associated with the points
     * @return Color associated
     */
    public ImageProperties.ImageColors getColor() {
        return color;
    }

    /**
     * Sets the color associated with the points
     * @param color Color associated
     */
    public void setColor(ImageProperties.ImageColors color) {
        this.color = color;
    }
}
