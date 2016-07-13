package com.salesforce.dva.warden.client;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.params.HttpParams;
import org.junit.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.warden.client.HttpClient.RequestType;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.Policy.Aggregator;
import com.salesforce.dva.warden.dto.Policy.TriggerType;


public class DefaultWebServiceClientTest  {

	static class MockHttpResponse implements HttpResponse{

		@Override
		public ProtocolVersion getProtocolVersion() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public boolean containsHeader(String name) {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public Header[] getHeaders(String name) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Header getFirstHeader(String name) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Header getLastHeader(String name) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Header[] getAllHeaders() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void addHeader(Header header) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void addHeader(String name, String value) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setHeader(Header header) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setHeader(String name, String value) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setHeaders(Header[] headers) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void removeHeader(Header header) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void removeHeaders(String name) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public HeaderIterator headerIterator() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public HeaderIterator headerIterator(String name) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public HttpParams getParams() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setParams(HttpParams params) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public StatusLine getStatusLine() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setStatusLine(StatusLine statusline) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setStatusLine(ProtocolVersion ver, int code) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setStatusLine(ProtocolVersion ver, int code, String reason) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setStatusCode(int code) throws IllegalStateException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void setReasonPhrase(String reason) throws IllegalStateException {
			// TODO Auto-generated method stub
			
		}

		@Override
		public HttpEntity getEntity() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setEntity(HttpEntity entity) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public Locale getLocale() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void setLocale(Locale loc) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	interface MockedHttpEntity extends HttpEntity{
		public String getPayload();
	}
	
	static class MockedEntity implements MockedHttpEntity{
		

		@Override
		public boolean isRepeatable() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean isChunked() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public long getContentLength() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public Header getContentType() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Header getContentEncoding() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public InputStream getContent() throws IOException, IllegalStateException {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void writeTo(OutputStream outstream) throws IOException {
			// TODO Auto-generated method stub
			outstream.write(getPayload().getBytes("UTF-8"));
		}
		
		public String getPayload(){
			return null;
		}

		@Override
		public boolean isStreaming() {
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public void consumeContent() throws IOException {
			// TODO Auto-generated method stub
			
		}
	} 
	
	
	
	@Test
	public void testGetPolicies() throws Exception {
		HttpClient mockedHttpClient = Mockito.mock(HttpClient.class);
		mockedHttpClient.endpoint = "argusuri";
		mockedHttpClient.maxConn = 10;
		mockedHttpClient.connRequestTimeout = 10;
		mockedHttpClient.connTimeout = 10;
		
		List <Policy> actualPolicies = new ArrayList<Policy>();
		
		Policy dummyPolicyA = new Policy();		
		createDummyPolicy(dummyPolicyA);
		
		Policy dummyPolicyB = new Policy();		
		createDummyPolicy(dummyPolicyB);

        
		actualPolicies.add(dummyPolicyA);
		actualPolicies.add(dummyPolicyB);
		
		String payload = new ObjectMapper().writeValueAsString(actualPolicies);
		
		DefaultWebServiceClient webServiceClient = new DefaultWebServiceClient(mockedHttpClient); 
		
		webServiceClient = Mockito.spy(webServiceClient);
		Mockito.doReturn(true).when(webServiceClient).login("raj", "abc");		
		Mockito.doReturn(true).when(webServiceClient).logout();
		
		HttpResponse response = Mockito.mock(MockHttpResponse.class);
		MockedHttpEntity mockedEntity = new MockedEntity();
		mockedEntity = Mockito.spy(mockedEntity);
		Mockito.when(mockedEntity.getPayload()).thenReturn(payload);
		
		Mockito.when(response.getEntity()).thenReturn(mockedEntity );
		
		Mockito.when(mockedHttpClient.executeHttpRequest(RequestType.GET, "argusuri/policy", null)).thenReturn(response);
		
		assertEquals(webServiceClient.getPolicies(), actualPolicies);
	}

	private void createDummyPolicy(Policy dummyPolicyName) {
		dummyPolicyName.setId( BigInteger.ONE );
        dummyPolicyName.setCreatedById( BigInteger.ONE );
        dummyPolicyName.setCreatedDate( new Date(  ) );
        dummyPolicyName.setModifiedById( BigInteger.TEN );
        dummyPolicyName.setModifiedDate( new Date(  ) );

        dummyPolicyName.setService( "example-service" );
        dummyPolicyName.setName( "example-name" );
        dummyPolicyName.setOwners( Arrays.asList( "example-owners" ) );
        dummyPolicyName.setUsers( Arrays.asList( "example-users" ) );
        dummyPolicyName.setSubSystem( "example-subSystem" );
        dummyPolicyName.setMetricName( "example-metricName" );
        dummyPolicyName.setTriggerType( TriggerType.NOT_BETWEEN );
        dummyPolicyName.setAggregator( Aggregator.SUM );
        dummyPolicyName.setThresholds( Arrays.asList( 0.0 ) );
        dummyPolicyName.setTimeUnit( "5min" );
        dummyPolicyName.setDefaultValue( 0.0 );
       dummyPolicyName.setCronEntry( "0 */4 * * *" );
	}
																																																																																												
}
