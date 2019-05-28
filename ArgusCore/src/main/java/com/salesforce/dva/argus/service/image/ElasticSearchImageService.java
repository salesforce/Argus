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

package com.salesforce.dva.argus.service.image;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.ImageService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.metric.MetricQueryResult;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.ImageUtils;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import java.util.List;

/**
 * ElasticSearch implementation of Image Service to generate,store and query image.
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */

@Singleton
public class ElasticSearchImageService extends DefaultService implements ImageService{

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchImageService.class);

    protected final MonitorService monitorService;
    protected final MetricService metricService;

    @Inject
    protected ElasticSearchImageService(SystemConfiguration systemConfiguration,MonitorService monitorService, MetricService metricService) {
        super(systemConfiguration);
        this.monitorService = monitorService;
        this.metricService = metricService;
    }

    /**
     * Generates an image for the given list of metrics
     *
     * @param metrics           List of metrics for which image is generated
     * @param imageProperties   Properties of the image that need to be generated
     * @return Returns Byte Array of the JPEG Image
     */
    @Override
    public byte[] generateImage(List<Metric> metrics, ImageProperties imageProperties) {

        //TODO: This is the temporary logic until we do the actual implementation
        try {

            return ImageUtils.getMetricsImage(metrics);
        }
        catch (Exception e){
            logger.error("Error while generating the image"+ e);
            return null;
        }

    }

    /**
     * Saves the image byte array in an sync or async fashion
     *
     * @param imageBytes Byte Array of the JPEG image to be saved
     * @param sync       saves image synchronously or asynchronously
     * @return Returns a Unique Id for this image
     */

    @Override
    public String storeImage(byte[] imageBytes, boolean sync) {

        //In the current boiler implementation we are randomly generating the image id and not storing it
        return UUID.randomUUID().toString();
    }

    /**
     * Generates and Saves the image in an sync or async fashion for the given list of metrics
     *
     * @param metrics           List of metrics for which JPEG image is generated
     * @param imageProperties   Properties of the image that need to be generated
     * @param sync              saves image synchronously or asynchronously
     * @return Pair of Unique Id for image and JPEG image byte array is returned
     */
    @Override
    public Pair<String, byte[]> generateAndStoreImage(List<Metric> metrics, ImageProperties imageProperties, boolean sync) {

        //TODO: This is the temporary logic until we do the actual implementation
        Pair<String, byte[]> imageObject;
        try {
            imageObject = new Pair<String, byte[]>(UUID.randomUUID().toString(),ImageUtils.getMetricsImage(metrics));
            return imageObject;
        }

        catch(Exception e)
        {
            logger.error("Error while generating the image"+ e);
            return new Pair<String, byte[]>(null,null);
        }
    }

    /**
     * Returns an image for the given imageId
     *
     * @param imageId Key of the image
     * @return Associated JPEG image is returned
     */
    @Override
    public byte[] getImageById(String imageId) {

        //TODO: This is added temporarily for functionality of this method. But in actual implementation we would remove this.
        MetricQueryResult metricQueryResult = metricService.getMetrics("-1h:argus.jvm:mem.physical.free:avg");
        try {

            return ImageUtils.getMetricsImage(metricQueryResult.getMetricsList());
        }
        catch (Exception e){
            logger.error("Error while generating the image"+ e);
            return null;
        }
    }
}
