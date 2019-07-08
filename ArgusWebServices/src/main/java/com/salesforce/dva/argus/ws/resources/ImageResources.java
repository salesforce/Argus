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

package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.service.ImageService;
import com.salesforce.dva.argus.ws.annotation.Description;
import org.apache.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("/images")
@Description("Provides methods to retrieve images")
public class ImageResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private ImageService imageService = system.getServiceFactory().getImageService();

    //~ Methods **************************************************************************************************************************************

    /**
     *
     * @param req       The HTTP request.
     * @param imageId   Id of the image to retrieve
     * @return          Byte Array of the JPG image
     */
    @GET
    @Path("/id/{imageid}")
    @Produces("image/jpg")
    @Description("Returns a JPG image")
    public Response getImageById(@Context HttpServletRequest req,
                                       @PathParam("imageid") String imageId) {
        if (imageId == null || imageId.isEmpty()) {
            throw new WebApplicationException("imageid cannot be null or Empty", Response.Status.BAD_REQUEST);
        }
        validateAndGetOwner(req, null);
        try {
            byte[] image = imageService.getImageById(imageId);
            if(image !=null && image.length>0) {
                return Response.ok(image).build();
            }else {
                return Response.status(HttpStatus.SC_NOT_FOUND).build();
            }
        }
        catch (Exception ex)
        {
            throw new WebApplicationException(ex.getMessage(), Response.Status.INTERNAL_SERVER_ERROR);
        }

    }
}
