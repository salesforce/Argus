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

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Metric;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * Provides methods to generate,store and query image.
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */

public interface ImageService extends Service {


    /**
     * Generates an image for the given list of metrics
     *
     * @param metrics    List of metrics for which image is generated
     * @param properties Properties of the image that need to be generated
     * @return Returns Byte Array of the JPEG Image
     */

    byte[] generateImage(List<Metric> metrics, ImageProperties properties);

    /**
     * Saves the image byte array in an sync or async fashion
     *
     * @param imageBytes Byte Array of the JPEG image to be saved
     * @param sync       saves image synchronously or asynchronously
     * @return Returns a Unique Id for this image
     */

    String storeImage(byte[] imageBytes, boolean sync);


    /**
     * Generates and Saves the image in an sync or async fashion for the given list of metrics
     *
     * @param metrics    List of metrics for which JPEG image is generated
     * @param properties Properties of the image that need to be generated
     * @param sync       saves image synchronously or asynchronously
     * @return  Pair Object with first argument contains Unique Id for image and second argument contains associated JPEG image byte array
     */
    Pair<String,byte[]> generateAndStoreImage(List<Metric> metrics, ImageProperties properties, boolean sync);


    /**
     * Returns an image for the given imageId
     *
     * @param imageId Key of the image
     * @return Associated JPEG image is returned
     */
    byte[] getImageById(String imageId);
}
