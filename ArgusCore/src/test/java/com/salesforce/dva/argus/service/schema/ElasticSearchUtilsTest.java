package com.salesforce.dva.argus.service.schema;

import com.salesforce.dva.argus.system.SystemException;
import org.apache.http.entity.BasicHttpEntity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.*;

public class ElasticSearchUtilsTest {

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
}