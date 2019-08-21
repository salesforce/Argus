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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.ImageUtils;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ElasticSearchImageServiceTest {

    private String createSucessReply = String.join("\n",
            "{" +
                    "    \"took\": 55," +
                    "    \"errors\": false," +
                    "    \"items\": [" +
                    "        {" +
                    "            \"create\": {" +
                    "                \"_index\": \"argus-image\"," +
                    "                \"_id\": \"cdfd12850d42746257f8217899647c8b\"," +
                    "                \"_version\": 1," +
                    "                \"result\": \"created\"," +
                    "                \"_shards\": {" +
                    "                    \"total\": 2," +
                    "                    \"successful\": 1," +
                    "                    \"failed\": 0" +
                    "                }," +
                    "                \"created\": true," +
                    "                \"status\": 201" +
                    "            }" +
                    "        }" +
                    "    ]" +
                    "}");
    private String getReply = String.join("\n",
            "{",
            "    \"took\": 9,",
            "    \"timed_out\": false,",
            "    \"_shards\": {",
            "        \"total\": 6,",
            "        \"successful\": 6,",
            "        \"skipped\": 0,",
            "        \"failed\": 0",
            "    },",
            "    \"hits\": {",
            "        \"total\": 1,",
            "        \"max_score\": 1.0,",
            "        \"hits\": [",
            "               {",
            "                   \"_index\": \"argus-image-2019-06-11\",",
            "                   \"_id\": \"0FD3DBEC9730101BFF92ACC820BEFC34\",",
            "                   \"_score\": 1.0,",
            "                   \"_source\": {",
            "                       \"imageblob\": \"VGVzdCBzdHJpbmc=\",",
            "                       \"mts\": \"1560798017039\"",
            "                     }",
            "               }",
            "         ]",
            "    }",
            "}");

    private String imageIdQuery = String.join("\n",
            "{",
            "  \"query\": {",
            "    \"ids\": {",
            "      \"values\": [",
            "        \"0FD3DBEC9730101BFF92ACC820BEFC34\"",
            "      ]",
            "    }",
            "  }",
            "}");

    private RestClient restClient;
    private static SystemConfiguration systemConfig;
    private static ElasticSearchImageService elasticSearchImageService;
    private static ObjectMapper mapper = new ObjectMapper();

    private String convertToPrettyJson(String jsonString) {
        JsonParser parser = new JsonParser();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonElement el = parser.parse(jsonString);
        return gson.toJson(el);
    }

    @BeforeClass
    public static void setUpClass() {
        Properties config = new Properties();
        systemConfig = new SystemConfiguration(config);
        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
        elasticSearchImageService = new ElasticSearchImageService(systemConfig, mockedMonitor, mockedElasticSearchUtils);
    }

    @Test
    public void testImageRecordMapper() throws IOException {
        ImageRecord.IndexSerializer imageIndexSerializer= new ImageRecord.IndexSerializer();
        mapper = ElasticSearchImageService.getImageObjectMapper(imageIndexSerializer);

        String byteString = "Test string";
        String imageId = ImageUtils.convertBytesToMd5Hash(byteString.getBytes());
        ImageRecord imageRecord = new ImageRecord(imageId,byteString.getBytes());
        String serialized = mapper.writeValueAsString(imageRecord);
        String[] lines = serialized.split("\\r?\\n");


        String expectedIndexName=imageIndexSerializer.getImageIndex(System.currentTimeMillis());
        String expectedDocumentId = ImageUtils.convertBytesToMd5Hash(imageRecord.getImageBytes());
        String expectedImageBlob = ImageUtils.encodeBytesToBase64(imageRecord.getImageBytes());
        JsonNode line1 = mapper.readTree(lines[0]);
        String actualIndexName = line1.get("index").get("_index").asText();
        String actualDocumentId = line1.get("index").get("_id").asText();

        assertEquals(expectedIndexName, actualIndexName);
        assertEquals(expectedDocumentId, actualDocumentId);

        JsonNode line2 = mapper.readTree(lines[1]);
        String actualImageBlob = line2.get("imageblob").asText();

        assertEquals(expectedImageBlob, actualImageBlob);

    }

    @Test
    public void testPutImageIndex() throws IOException {
        ElasticSearchImageService spyService = _initializeSpyService(elasticSearchImageService, createSucessReply, createSucessReply);

        String byteString = "Test string";
        String imageId = ImageUtils.convertBytesToMd5Hash(byteString.getBytes());
        spyService.putImage(imageId,byteString.getBytes(),true);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);

        verify(restClient, times(1)).performRequest(requestCaptor.capture());

        Request capturedRequest = requestCaptor.getValue();
        String indexJson = EntityUtils.toString(capturedRequest.getEntity());
        String expectedURL = "_bulk";
        assertEquals(expectedURL, capturedRequest.getEndpoint());

        String[] lines = indexJson.split("\\r?\\n");

        ImageRecord.IndexSerializer imageIndexSerializer= new ImageRecord.IndexSerializer();
        String id = ImageUtils.convertBytesToMd5Hash(byteString.getBytes());
        ImageRecord imageRecord = new ImageRecord(id,byteString.getBytes());

        String expectedIndexName=imageIndexSerializer.getImageIndex(System.currentTimeMillis());
        String expectedDocumentId = ImageUtils.convertBytesToMd5Hash(imageRecord.getImageBytes());
        String expectedImageBlob = ImageUtils.encodeBytesToBase64(imageRecord.getImageBytes());
        JsonNode line1 = mapper.readTree(lines[0]);
        String actualIndexName = line1.get("index").get("_index").asText();
        String actualDocumentId = line1.get("index").get("_id").asText();

        assertEquals(expectedIndexName, actualIndexName);
        assertEquals(expectedDocumentId, actualDocumentId);

        JsonNode line2 = mapper.readTree(lines[1]);
        String actualImageBlob = line2.get("imageblob").asText();

        assertEquals(expectedImageBlob, actualImageBlob);

    }

    @Test
    public void testGetImage() throws IOException {

        ElasticSearchImageService spyService = _initializeSpyService(elasticSearchImageService, getReply, getReply);


        byte[] expectedBytes = "Test string".getBytes();
        byte[] actualBytes = spyService.getImage("0FD3DBEC9730101BFF92ACC820BEFC34");
        assertArrayEquals(expectedBytes, actualBytes);
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(restClient, times(1)).performRequest(requestCaptor.capture());
        Request capturedRequest = requestCaptor.getValue();

        String queryJson = convertToPrettyJson(EntityUtils.toString(capturedRequest.getEntity()));
        assertEquals(imageIdQuery, queryJson);
        String expectedURL = "/argus-image-*/_search";
        assertEquals(expectedURL, capturedRequest.getEndpoint());
    }

    @Test
    public void testDoExtractResponse() throws Exception {
        final String message = "this is a test";
        BasicHttpEntity entity = new BasicHttpEntity();
        try(ByteArrayInputStream bis = new ByteArrayInputStream(message.getBytes())) {
            entity.setContent(bis);
        }
        catch (IOException e) {
            throw e;
        }

        String responseMessage = ElasticSearchUtils.doExtractResponse(200, entity);
        assertEquals("expect the entity to be equal after extraction", message, responseMessage);
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testDoExtractResponse400() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Status code: 400");
        ElasticSearchUtils.doExtractResponse(400, null);
    }

    @Test
    public void testDoExtractResponse500() {
        expectedException.expect(SystemException.class);
        expectedException.expectMessage("Status code: 500");
        ElasticSearchUtils.doExtractResponse(500, null);
    }

    private ElasticSearchImageService _initializeSpyService(ElasticSearchImageService service,
                                                                 String firstReply, String secondReply) {

        restClient = mock(RestClient.class);
        service.setESRestClient(restClient);
        ElasticSearchImageService spyService = spy(service);

        doAnswer(new Answer() {
            private int count = 0;
            public Object answer(InvocationOnMock invocation) {
                count++;
                if (count == 1) {
                    return firstReply;
                }

                return secondReply;
            }
        }).when(spyService).extractResponse(any());

        return spyService;
    }

}
