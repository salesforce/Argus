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

import com.salesforce.dva.argus.entity.ImagePoints;
import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Metric;
import org.apache.commons.codec.digest.DigestUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Utility methods for Image Service
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */
public class ImageUtils {

    private static final int MAX_LEGENDS_TO_DISPLAY=5;
    private static final double DOUBLE_COMPARISON_MAX_DELTA = 0.000000000000001;
    private static final Font DEFAULT_FONT = new Font("Arial", Font.ITALIC, 12);
    private static final Font DEFAULT_NODATA_FONT = new Font("Arial", Font.ITALIC, 20);
    private static final BasicStroke DEFAULT_BASIC_STROKE = new BasicStroke(1.5f);
    private static final Color DEFAULT_BACKGROUND_COLOR = Color.white;
    private static final Color DEFAULT_FIRST_TIMESERIES_COLOR = Color.BLUE;


    public static byte[] getMetricsImage(List<Metric> metrics, ImageProperties imageProperties) throws IOException{

        if (imageProperties == null)
        {
            imageProperties = new ImageProperties();
        }
        if(metrics != null && metrics.size()>0) {
            boolean legend = metrics.size() > MAX_LEGENDS_TO_DISPLAY ? false:true;
            List<TimeSeries> timeseries = convertToTimeSeries(metrics);
            TimeSeriesCollection dataset=new TimeSeriesCollection();
            for(TimeSeries series:timeseries) {
                dataset.addSeries(series);
            }

            JFreeChart timechart = ChartFactory.createTimeSeriesChart(imageProperties.getChartName(),
                    imageProperties.getxAxisName(), imageProperties.getyAxisName(), dataset,legend, true, true);
            timechart.getPlot().setBackgroundPaint(DEFAULT_BACKGROUND_COLOR);

            XYPlot plot = (XYPlot) timechart.getPlot();

            // Overridding the range axis with new metric formatter
            NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
            MetricNumberFormat metricNumberFormat = new MetricNumberFormat();
            rangeAxis.setNumberFormatOverride(metricNumberFormat);

            if (imageProperties.getLabelPoints()!=null) {
                for (ImagePoints point : imageProperties.getLabelPoints()) {
                    XYTextAnnotation textAnnotaion = new XYTextAnnotation(point.getLabel(), point.getFirstPoint(), point.getSecondPoint());
                    textAnnotaion.setBackgroundPaint(DEFAULT_BACKGROUND_COLOR);
                    if (point.getColor()!=null) {
                        textAnnotaion.setPaint(point.getColor().getColor());
                    }
                    textAnnotaion.setFont(DEFAULT_FONT);
                    plot.addAnnotation(textAnnotaion);
                }
            }

            if (imageProperties.getShadeXAxisArea()!=null) {
                for (ImagePoints point : imageProperties.getShadeXAxisArea()) {
                    if (compareAlmostEqual(point.getFirstPoint(),point.getSecondPoint(),DOUBLE_COMPARISON_MAX_DELTA))
                    {
                        plot.addRangeMarker(getLineMarker(point));
                    }
                    else {
                        plot.addRangeMarker(getIntervalMarker(point), Layer.BACKGROUND);
                    }
                }
            }

            if (imageProperties.getShadeYAxisArea()!=null) {
                for (ImagePoints point : imageProperties.getShadeYAxisArea()) {
                    if (compareAlmostEqual(point.getFirstPoint(),point.getSecondPoint(),DOUBLE_COMPARISON_MAX_DELTA))
                    {
                        plot.addDomainMarker(getLineMarker(point));
                    }
                    else {
                        plot.addDomainMarker(getIntervalMarker(point), Layer.BACKGROUND);
                    }
                }
            }
            XYItemRenderer r = plot.getRenderer();
            // Setting the default color of the first time series to be BLUE
            r.setSeriesPaint( 0, DEFAULT_FIRST_TIMESERIES_COLOR );
            if (r instanceof XYLineAndShapeRenderer) {
                XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
                renderer.setDefaultShapesVisible(true);
                renderer.setDefaultShapesFilled(true);
                renderer.setDrawSeriesLineAsPath(true);
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ChartUtils.writeChartAsJPEG(outputStream, timechart, imageProperties.getImageWidth(), imageProperties.getImageHeight());
            return outputStream.toByteArray();
        }else {
            // This is for missing data notification
            return getImageWithText("Data does not exist for a given metric expression",imageProperties.getImageWidth(),imageProperties.getImageHeight());
        }
    }

    private static ValueMarker getLineMarker(ImagePoints point)
    {
        Color color;
        if (point.getColor()==null)
        {
            color = ImageProperties.DEFAULT_COLOR.getColor();
        }
        else {
            color = point.getColor().getColor();
        }
        ValueMarker marker = new ValueMarker(point.getFirstPoint(), color,DEFAULT_BASIC_STROKE);
        marker.setLabel(point.getLabel());
        marker.setLabelFont(DEFAULT_FONT);
        marker.setLabelBackgroundColor(DEFAULT_BACKGROUND_COLOR);
        marker.setLabelAnchor(RectangleAnchor.CENTER);
        marker.setLabelTextAnchor(TextAnchor.CENTER);
        return marker;
    }

    private static IntervalMarker getIntervalMarker(ImagePoints point)
    {
        Color color;
        if (point.getColor()==null)
        {
            color = ImageProperties.DEFAULT_COLOR.getColor();
        }
        else {
            color = point.getColor().getColor();
        }
        IntervalMarker marker = new IntervalMarker(point.getFirstPoint(), point.getSecondPoint(), color);
        marker.setLabel(point.getLabel());
        marker.setLabelFont(DEFAULT_FONT);
        marker.setLabelBackgroundColor(DEFAULT_BACKGROUND_COLOR);
        marker.setLabelAnchor(RectangleAnchor.CENTER);
        marker.setLabelTextAnchor(TextAnchor.CENTER);
        return marker;
    }

    private static byte[] getImageWithText(String data,int width, int height) throws IOException{
        BufferedImage bufferedImage = new BufferedImage(width, height,BufferedImage.TYPE_INT_RGB);
        Graphics graphics = bufferedImage.getGraphics();
        graphics.setColor(DEFAULT_BACKGROUND_COLOR);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(DEFAULT_NODATA_FONT);
        graphics.drawString(data, width/4, height/4);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "jpg", outputStream);
        return outputStream.toByteArray();
    }

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

    public static String convertBytesToMd5Hash(byte[] inputBytes)
    {
        String md5Hex = null;
        if (inputBytes!=null) {
            md5Hex = DigestUtils.md5Hex(inputBytes).toUpperCase();
        }
        return md5Hex;
    }

    public static String encodeBytesToBase64(byte[] inputBytes)
    {
        String encodedString = null;
        if (inputBytes!=null) {
            encodedString = Base64.getEncoder().encodeToString(inputBytes);
        }
        return encodedString;
    }

    public static byte[] decodeBase64ToBytes(String encodedString)
    {
        byte[] decodedBytes = null;
        if (encodedString!=null) {
            decodedBytes = Base64.getDecoder().decode(encodedString);
        }
        return decodedBytes;
    }

    public static boolean compareAlmostEqual(double x, double y, double delta) {
        return x == y  || Math.abs(x - y) < delta;
    }
}
