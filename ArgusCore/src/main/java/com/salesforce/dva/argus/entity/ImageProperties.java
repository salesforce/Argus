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

import org.apache.commons.lang3.tuple.Triple;

import java.awt.Color;
import java.util.List;

public class ImageProperties {


    private int imageWidth;
    private int imageHeight;
    private String chartName;
    private String xAxisName;
    private String yAxisName;
    private List<Triple<Long,Long,ImageColors>> shadeXAxisArea;
    private List<Triple<Long,Long,ImageColors>> shadeYAxisArea;
    private List<Triple<Long,Long,String>> labelPoints;

    public int getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(int imageHeight) {
        this.imageHeight = imageHeight;
    }

    public String getChartName() {
        return chartName;
    }

    public void setChartName(String chartName) {
        this.chartName = chartName;
    }

    public String getxAxisName() {
        return xAxisName;
    }

    public void setxAxisName(String xAxisName) {
        this.xAxisName = xAxisName;
    }

    public String getyAxisName() {
        return yAxisName;
    }

    public void setyAxisName(String yAxisName) {
        this.yAxisName = yAxisName;
    }

    public List<Triple<Long, Long, ImageColors>> getShadeXAxisArea() {
        return shadeXAxisArea;
    }

    public void setShadeXAxisArea(List<Triple<Long, Long, ImageColors>> shadeXAxisArea) {
        this.shadeXAxisArea = shadeXAxisArea;
    }

    public List<Triple<Long, Long, ImageColors>> getShadeYAxisArea() {
        return shadeYAxisArea;
    }

    public void setShadeYAxisArea(List<Triple<Long, Long, ImageColors>> shadeYAxisArea) {
        this.shadeYAxisArea = shadeYAxisArea;
    }

    public List<Triple<Long, Long, String>> getLabelPoints() {
        return labelPoints;
    }

    public void setLabelPoints(List<Triple<Long, Long, String>> labelPoints) {
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
        VERY_LIGHT_MAGENTA(new Color(0xFF, 0x80, 0xFF));


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
