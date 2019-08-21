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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.salesforce.dva.argus.util.ImageUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Used to serialize and deserialize image byte array
 *
 */
public class ImageRecord {

    private String imageId;
    private byte[] imageBytes;
    private static ObjectMapper mapper = new ObjectMapper();
    private static SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");


    public ImageRecord(String imageId,byte[] imageBytes) {
        this.imageId=imageId;
        this.imageBytes=imageBytes;
    }
    public String getImageId() {
        return imageId;
    }

    public byte[] getImageBytes() {
        return imageBytes;
    }

    public static class IndexSerializer extends JsonSerializer<ImageRecord> {


        @Override
        public void serialize(ImageRecord record, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            // Encoding image byte array to Base64 to store in ES
            String imageBlob = ImageUtils.encodeBytesToBase64(record.getImageBytes());
            Long currentTimestamp = System.currentTimeMillis();
            String indexType = ElasticSearchImageService.imageIndexType;
            jgen.writeRaw("{ \"index\" : {\"_index\" : \"" + getImageIndex(currentTimestamp) + "\",\"_type\": \"" + indexType + "\",\"_id\" : \"" + record.getImageId() + "\"}}");
            jgen.writeRaw(System.lineSeparator());
            Map<String, String> fieldsData = new HashMap<>();
            fieldsData.put(ImageRecordType.IMAGEBLOB.getName(), imageBlob);
            fieldsData.put(ImageRecordType.MTS.getName(), Long.toString(currentTimestamp));
            jgen.writeRaw(mapper.writeValueAsString(fieldsData));
            jgen.writeRaw(System.lineSeparator());
        }


        /**
         * Creating new index for every day
         * @param  currentTimeStamp Current Time Stamp
         * @return Index Name based on TimeStamp
         */
        protected String getImageIndex(Long currentTimeStamp) {
            Date currentDate = new Date(currentTimeStamp);
            String indexNameToAppend = String.format("%s-%s", ElasticSearchImageService.imageIndexTemplatePatternStart, formatter.format(currentDate));
            return indexNameToAppend;
        }
    }

    public static class Deserializer extends JsonDeserializer<ImageRecord> {

        @Override
        public ImageRecord deserialize(JsonParser jp, DeserializationContext context)
                throws IOException {

            List<byte[]> records = new ArrayList<>();
            JsonNode rootNode = jp.getCodec().readTree(jp);
            JsonNode hits = rootNode.get("hits").get("hits");
            if(JsonNodeType.ARRAY.equals(hits.getNodeType())) {
                records = new ArrayList<>(hits.size());
                Iterator<JsonNode> iter = hits.elements();
                while(iter.hasNext()) {
                    JsonNode hit = iter.next();
                    JsonNode source = hit.get("_source");
                    JsonNode imageBlob = source.get(ImageRecordType.IMAGEBLOB.getName());
                    byte[] decodedImage = ImageUtils.decodeBase64ToBytes(imageBlob.asText());
                    records.add(decodedImage);
                }
            }

            if (records.size()>0)
            {
                return new ImageRecord(null,records.get(0));
            }
            else
            {
                return new ImageRecord(null,null);
            }

        }
    }

    /**
     * Indicates the Image record field to be used for matching.
     *
     */
    public static enum ImageRecordType {

        /** Image Blob Field */
        IMAGEBLOB("imageblob"),
        /** Modified Time Stamp Field */
        MTS("mts");

        private String _name;

        private ImageRecordType(String name) {
            _name = name;
        }

        /**
         * Returns a given record type corresponding to the given name.
         *
         * @param   name  The case sensitive name to match against.  Cannot be null.
         *
         * @return  The corresponding record type or null if no matching record type exists.
         */
        @JsonCreator
        public static ImageRecordType fromName(String name) {
            for (ImageRecordType type : ImageRecordType.values()) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Illegal record type: " + name);
        }

        /**
         * Returns the record type name.
         *
         * @return  The record type name.
         */
        public String getName() {
            return _name;
        }
    }
}
