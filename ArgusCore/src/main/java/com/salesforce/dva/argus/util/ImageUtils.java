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

package com.salesforce.dva.argus.util;

import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Metric;
import org.apache.commons.lang.StringUtils;
import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for Image Service
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */
public class ImageUtils {

    private static String DEFAULT_IMAGE_XAXIS_LABEL="Time";
    private static String DEFAULT_IMAGE_YAXIS_LABEL="Value";
    private static int DEFAULT_IMAGE_WIDTH = 1100;
    private static int DEFAULT_IMAGE_HEIGHT = 550;
    private static int MAX_LEGENDS_TO_DISPLAY=5;


    public static byte[] getMetricsImage(List<Metric> metrics) throws Exception{
        ImageProperties imageProperties = new ImageProperties();
        setDefaultImageProperties(imageProperties);
        return getMetricsImage(metrics, imageProperties);
    }

    private static void setDefaultImageProperties(ImageProperties imageProperties) {
        imageProperties.setChartName(StringUtils.EMPTY);
        imageProperties.setImageWidth(DEFAULT_IMAGE_WIDTH);
        imageProperties.setImageHeight(DEFAULT_IMAGE_HEIGHT);
        imageProperties.setxAxisName(DEFAULT_IMAGE_XAXIS_LABEL);
        imageProperties.setyAxisName(DEFAULT_IMAGE_YAXIS_LABEL);
    }

    public static byte[] getMetricsImage(List<Metric> metrics, ImageProperties imageProperties) throws Exception{
        if(metrics != null && metrics.size()>0) {
            boolean legend = metrics.size() > MAX_LEGENDS_TO_DISPLAY ? false:true;
            List<TimeSeries> timeseries = convertToTimeSeries(metrics);
            TimeSeriesCollection dataset=new TimeSeriesCollection();
            for(TimeSeries series:timeseries) {
                dataset.addSeries(series);
            }

            JFreeChart timechart = ChartFactory.createTimeSeriesChart(imageProperties.getChartName(),
                    imageProperties.getxAxisName(), imageProperties.getyAxisName(), dataset,legend, true, true);
            timechart.getPlot().setBackgroundPaint(Color.WHITE);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ChartUtils.writeChartAsJPEG(outputStream, timechart, imageProperties.getImageWidth(), imageProperties.getImageHeight());
            return outputStream.toByteArray();
        }else {
            // This is for missing data notification
            return getImageWithText("Data does not exist for a given metric expression",imageProperties.getImageWidth(),imageProperties.getImageHeight());
        }
    }

    private static byte[] getImageWithText(String data,int width, int height) throws Exception{
        BufferedImage bufferedImage = new BufferedImage(width, height,BufferedImage.TYPE_INT_RGB);
        Graphics graphics = bufferedImage.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Arial Black", Font.BOLD, 20));
        graphics.drawString(data, width/4, height/4);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "jpg", outputStream);
        return outputStream.toByteArray();
    }

    //TODO: delete this method after final code changes
    /*public void testImage(List<Metric> metrics) throws Exception
    {
        boolean legend = metrics.size() > MAX_LEGENDS_TO_DISPLAY ? false:true;
        List<TimeSeries> timeseries = convertToTimeSeries(metrics);
        TimeSeriesCollection dataset=new TimeSeriesCollection();
        for(TimeSeries series:timeseries) {
            dataset.addSeries(series);
        }


        JFreeChart timechart = ChartFactory.createTimeSeriesChart(StringUtils.EMPTY,
                DEFAULT_IMAGE_XAXIS_LABEL, DEFAULT_IMAGE_YAXIS_LABEL, dataset,legend, true, true);
        timechart.getPlot().setBackgroundPaint(Color.WHITE);

        XYPlot plot = (XYPlot) timechart.getPlot();
        float lineWidth = 1.5f;

        ValueMarker marker = new ValueMarker(8,new ChartColor(255,232,232),new BasicStroke(lineWidth));
        marker.setLabel("Value here is "+ marker.getValue());
        marker.setLabelAnchor(RectangleAnchor.LEFT);
        marker.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
        plot.addRangeMarker(marker);

        XYTextAnnotation textAnnotaion = new XYTextAnnotation("Testing", 1558079974-30*1000, 7);
        plot.addAnnotation(textAnnotaion);

        plot.addDomainMarker(new IntervalMarker(1558079974-30*1000, 1558079974-20*1000, new ChartColor(240,206,206)));
        plot.addRangeMarker(new IntervalMarker(8,plot.getRangeAxis().getUpperBound(), new ChartColor(255,232,232)));

        XYItemRenderer r = plot.getRenderer();
        if (r instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
            renderer.setDefaultShapesVisible(true);
            renderer.setDefaultShapesFilled(true);
            renderer.setDrawSeriesLineAsPath(true);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ChartUtils.writeChartAsJPEG(outputStream, timechart, DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT);
    }*/

    private static List<TimeSeries> convertToTimeSeries(List<Metric> metrics){
        List<TimeSeries> result = new ArrayList<>();
        for(Metric metric:metrics) {
            TimeSeries timeSeries = new TimeSeries(getMetricDisplayName(metric));
            for(Map.Entry<Long, Double> entry : metric.getDatapoints().entrySet()){
                timeSeries.add(new Second(new Date(entry.getKey())), entry.getValue());
            }
            result.add(timeSeries);
        }
        return result;
    }

    private static String getMetricDisplayName(Metric metric) {
        StringBuilder result = new StringBuilder();
        result.append(metric.getScope()).append(':');
        result.append(metric.getMetric());
        if(metric.getTags().size()>0) {
            result.append(metric.getTags().toString());
        }
        return result.toString();
    }
}
