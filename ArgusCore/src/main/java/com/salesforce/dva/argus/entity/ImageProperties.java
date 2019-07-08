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
import java.awt.Color;
import java.util.List;

public class ImageProperties {


    private static final String DEFAULT_IMAGE_XAXIS_LABEL="Time";
    private static final String DEFAULT_IMAGE_YAXIS_LABEL="Value";
    private static final String DEFAULT_IMAGE_CHART_NAME = StringUtils.EMPTY;
    private static final int DEFAULT_IMAGE_WIDTH = 1100;
    private static final int DEFAULT_IMAGE_HEIGHT = 550;
    public static final ImageColors DEFAULT_COLOR = ImageColors.VERY_LIGHT_BLUE;

    private int imageWidth = DEFAULT_IMAGE_WIDTH;
    private int imageHeight = DEFAULT_IMAGE_HEIGHT;
    private String chartName = DEFAULT_IMAGE_CHART_NAME;
    private String xAxisName = DEFAULT_IMAGE_XAXIS_LABEL;
    private String yAxisName = DEFAULT_IMAGE_YAXIS_LABEL;
    private List<ImagePoints> shadeXAxisArea;
    private List<ImagePoints> shadeYAxisArea;
    private List<ImagePoints> labelPoints;


    /**
     * Gets the Image Width. Default value is 1100
     * @return  Returns the Image width
     */
    public int getImageWidth() {
        return imageWidth;
    }


    /**
     * Sets the Image Width of the JPG Image
     * @param imageWidth    imageWidth value
     */
    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }


    /**
     * Gets the Image Height. Default value is 550
     * @return  Returns the Image Height
     */
    public int getImageHeight() {
        return imageHeight;
    }

    /**
     * Sets the Image Height of the JPG Image
     * @param imageHeight   imageHeight Value
     */
    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    /**
     * Gets the Chart Name of the Image.By default the chart name is empty
     * @return  Returns the chart name
     */
    public String getChartName() {
        return chartName;
    }

    /**
     * Sets the Chart Name of the Image.
     * @param chartName Chart Name value
     */
    public void setChartName(String chartName) {
        this.chartName = chartName;
    }

    /**
     * Gets the X-Axis Name of the Image. By default the value is "Time"
     * @return  Returns the X-Axis Name
     */
    public String getxAxisName() {
        return xAxisName;
    }

    /**
     * Sets the X-Axis Name of the Image
     * @param xAxisName X-Axis Name
     */
    public void setxAxisName(String xAxisName) {
        this.xAxisName = xAxisName;
    }

    /**
     * Gets the Y-Axis Name of the Image. By default the value is "Value"
     * @return  Returns the Y-Axis Name
     */
    public String getyAxisName() {
        return yAxisName;
    }

    /**
     * Sets the Y-Axis Name of the Image
     * @param yAxisName Y-Axis Name
     */
    public void setyAxisName(String yAxisName) {
        this.yAxisName = yAxisName;
    }

    /**
     * Gets information related to List of ImagePoints that are used to shade the area parallel to X-Axis
     * @return  List of ImagePoints to Shade X-Axis
     */
    public List<ImagePoints> getShadeXAxisArea() {
        return shadeXAxisArea;
    }

    /**
     * Set the Information that is required to shade the area parallel to X-Axis.If y1==y2 then straight line will be drawn
     * @param shadeXAxisArea   List of ImagePoints with each ImagePoints represent y1,y2,label,Color
     */
    public void setShadeXAxisArea(List<ImagePoints> shadeXAxisArea) {
        this.shadeXAxisArea = shadeXAxisArea;
    }

    /**
     * Gets information related to List of ImagePoints that are used to shade the area parallel to Y-Axis
     * @return  List of ImagePoints to Shade Y-Axis
     */
    public List<ImagePoints> getShadeYAxisArea() {
        return shadeYAxisArea;
    }

    /**
     * Set the Information that is required to shade the area parallel to Y-Axis.If x1==x2 then straight line will be drawn
     * @param shadeYAxisArea    List of ImagePoints with each ImagePoints represent x1,x2,label,Color
     */
    public void setShadeYAxisArea(List<ImagePoints> shadeYAxisArea) {
        this.shadeYAxisArea = shadeYAxisArea;
    }

    /**
     * Gets List of ImagePoints to represent labels in XY axis
     * @return  List of ImagePoints to plot labels
     */
    public List<ImagePoints> getLabelPoints() {
        return labelPoints;
    }

    /**
     * Set the information that is required to label points in XY Axis
     * @param labelPoints   List of ImagePoints with each ImagePoints represent x1,y1,label,Color
     */
    public void setLabelPoints(List<ImagePoints> labelPoints) {
        this.labelPoints = labelPoints;
    }

    public enum ImageColors {

        VERY_DARK_RED(new Color(0x80, 0x00, 0x00)),
        DARK_RED(new Color(0xc0, 0x00, 0x00)),
        LIGHT_RED(new Color(0xFF, 0x40, 0x40)),
        VERY_LIGHT_RED(new Color(0xFF, 0x80, 0x80)),
        VERY_DARK_YELLOW(new Color(0x80, 0x80, 0x00)),
        DARK_YELLOW(new Color(0xC0, 0xC0, 0x00)),
        LIGHT_YELLOW(new Color(0xFF, 0xFF, 0x40)),
        VERY_LIGHT_YELLOW(new Color(0xFF, 0xFF, 0x80)),
        VERY_DARK_GREEN(new Color(0x00, 0x80, 0x00)),
        DARK_GREEN(new Color(0x00, 0xC0, 0x00)),
        LIGHT_GREEN(new Color(0x40, 0xFF, 0x40)),
        VERY_LIGHT_GREEN(new Color(0x80, 0xFF, 0x80)),
        VERY_DARK_CYAN(new Color(0x00, 0x80, 0x80)),
        DARK_CYAN(new Color(0x00, 0xC0, 0xC0)),
        LIGHT_CYAN(new Color(0x40, 0xFF, 0xFF)),
        VERY_LIGHT_CYAN(new Color(0x80, 0xFF, 0xFF)),
        VERY_DARK_BLUE(new Color(0x00, 0x00, 0x80)),
        DARK_BLUE(new Color(0x00, 0x00, 0xC0)),
        LIGHT_BLUE(new Color(0x40, 0x40, 0xFF)),
        VERY_LIGHT_BLUE(new Color(0x80, 0x80, 0xFF)),
        VERY_DARK_MAGENTA(new Color(0x80, 0x00, 0x80)),
        DARK_MAGENTA(new Color(0xC0, 0x00, 0xC0)),
        LIGHT_MAGENTA(new Color(0xFF, 0x40, 0xFF)),
        VERY_LIGHT_MAGENTA(new Color(0xFF, 0x80, 0xFF)),
        VERY_LIGHT_PINK(new Color(255, 230, 230));


        private Color color;

        ImageColors(Color color) {
            this.setColor(color);
        }

        public Color getColor() {
            return color;
        }

        public void setColor(Color color) {
            this.color = color;
        }
    }
}
