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
import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.ImageService;
import com.salesforce.dva.argus.service.ImageStorageService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.ImageUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

public class DefaultImageService extends DefaultService implements ImageService {


    //~ Instance fields ******************************************************************************************************************************

    private final ImageStorageService imageStorageService;
    private static Logger logger = LoggerFactory.getLogger(DefaultImageService.class);

    /**
     *
     * @param imageStorageService   The storage service used to perform image operations. Cannot be null.
     * @param config                System Configuration
     */
    @Inject
    protected DefaultImageService(ImageStorageService imageStorageService,SystemConfiguration config) {
        super(config);
        requireArgument(imageStorageService != null, "The image storage service cannot be null.");
        this.imageStorageService=imageStorageService;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Generates an image for the given list of metrics
     *
     * @param metrics           List of metrics for which image is generated
     * @param imageProperties   Properties of the image that need to be generated
     * @return Returns Byte Array of the JPEG Image or null
     */
    @Override
    public byte[] generateImage(List<Metric> metrics, ImageProperties imageProperties){

        try {
            return ImageUtils.getMetricsImage(metrics,imageProperties);
        }
        catch (IOException exception)
        {
            logger.error("Exception while generating the Image",exception);
            throw new SystemException("Exception while generating the Image",exception);
        }


    }

    /**
     * Saves the image byte array in an sync or async fashion
     *
     * @param imageBytes Byte Array of the JPEG image to be saved
     * @param sync       saves image synchronously or asynchronously
     * @return Returns a Unique Id for this image or null
     */

    @Override
    public String storeImage(byte[] imageBytes, boolean sync) {

        requireArgument((imageBytes != null && imageBytes.length>0), "imageBytes cannot be null or Empty");
        // ImageId is the Md5 Hash of the imageBytes
        String imageId= ImageUtils.convertBytesToMd5Hash(imageBytes);
        imageStorageService.putImage(imageId,imageBytes,sync);
        return imageId;
    }


    /**
     * Generates and Saves the image in an sync or async fashion for the given list of metrics
     *
     * @param metrics           List of metrics for which JPEG image is generated
     * @param imageProperties   Properties of the image that need to be generated
     * @param sync              saves image synchronously or asynchronously
     * @return Pair Object with first argument contains Unique Id for image and second argument contains associated JPEG image byte array
     */
    @Override
    public Pair<String,byte[]> generateAndStoreImage(List<Metric> metrics, ImageProperties imageProperties, boolean sync) {

        try {
            byte[] imageBytes = ImageUtils.getMetricsImage(metrics,imageProperties);
            String imageId = ImageUtils.convertBytesToMd5Hash(imageBytes);
            imageStorageService.putImage(imageId,imageBytes,sync);
            Pair<String,byte[]> imageObject = Pair.of(imageId,imageBytes);
            return imageObject;
        }
        catch (IOException exception)
        {
            logger.error("Exception while generating the Image",exception);
            throw new SystemException("Exception while generating the Image",exception);
        }

    }

    /**
     * Returns image byte Array for the given imageId
     *
     * @param imageId Key of the image
     * @return Associated JPEG image is returned
     */
    @Override
    public byte[] getImageById(String imageId) {

        requireArgument((imageId != null && imageId.length()>0), "imageId cannot be null or Empty");
        return imageStorageService.getImage(imageId);
    }
}
