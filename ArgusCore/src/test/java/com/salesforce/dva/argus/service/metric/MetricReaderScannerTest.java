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

package com.salesforce.dva.argus.service.metric;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.ServiceFactory;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.MetricReaderTest;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;
import org.mockito.Matchers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MetricReaderScannerTest {
	
	//private static String prefix = "USE::SCANNER(";
	private static String suffix = "::USESCANNER";
	
	private void testValid(String expression1, String expression2) {
		if (!MetricReader.isValid(expression1)) {
			fail(expression1);
		}
		if (!MetricReader.isValid(expression2)) {
			fail(expression2);
		}
	}

	@Test
	public void testExampleWithTagsAndNamespaceAndEndTimeRawMetric() {
		String expression = "123000:234000:na1:app_record.count{tak=tagv}:avg:namepsace";
		String expression2 =  expression + suffix;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleWithTagsAndEndTimeWithoutNamespaceRawMetric() {
		String expression = "123000:234000:na1:app_record.count{tak=tagv}:avg";
		String expression2 = expression + suffix;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleWithTagsWithoutEndTimeAndNamespaceRawMetric() {
		String expression = "-1h:na1:app_record.count{tak=tagv}:avg";
		String expression2 = expression + suffix;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleWithoutTagsAndEndTimeAndNamespaceRawMetric() {
		String expression = "-1h:na1:app_record.count:avg";
		String expression2 = expression + suffix;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleWithTagsAndEndTimeWithoutNamespaceMaxTransform() {
		String func = "MAX";
		String params = "(123000:-1m:na1:app_record.count{tagk=tagv}:avg:15m-avg,123000:234000:na1:app_record.count{tak=tagv}:avg)";
		
		String expression = func + params;
		String expression2 =  func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleWithTagsAndEndTimeAndDownsamplerWithoutNamespaceCountTransform() {
		String func = "COUNT";
		String params = "(123000:234000:na1:app_record.count{tagk=tagv1|tagv2}:avg:15m-avg)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleAbsolute() {
		String func = "ABSOLUTE";
		String params = "(123000:234000:na1:app_record.count{tagk=tagv1|tagv2}:avg:15m-avg)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testExampleRange() {
		String func = "RANGE";
		String params = "(123000:-1m:na1:app_record.count{tagk=tagv}:avg:15m-avg,123000:234000:na1:app_record.count{tak=tagv}:avg)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testNormalize() {
		String func = "NORMALIZE";
		String params = "(123000:234000:na1:app_record.count{tagk=*}:avg:15m-avg)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testBasicQueriesWithWildcard() {
		Properties props = readFile("MetricReaderTest.testWildcardExpressions.properties");
		
		for (Object input : props.values()) {
			String expression = (String) input;
			String expression2 = expression + suffix;
			testValid(expression, expression2);
		}
	}
	
	@Test
	public void testSumWithConstantNullAndTen() {
		String func = "SUM";
		String params = "(-20h:na1:app_record.count{tagk=tagv}:avg:15m-avg,123000:234000:na1:app_record.count{tak=tagv}:avg, #null#)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
		
		params = "(123000:234000:na1:app_record.count{tagk=tagv1|tagv2}:avg:15m-avg, #10#)";
		expression = func + params;
		expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testDiffWithConstant() {
		String func = "DIFF";
		String params = "(123000:234000:na1:app_record.count{tagk=tagv1|tagv2}:avg:15m-avg, #10#)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testPropagateWithConstantNull() {
		String func = "PROPAGATE";
		String params = "(123000:-1m:na1:app_record.count{tagk=tagv}:avg:15m-avg,123000:234000:na1:app_record.count{tak=tagv}:avg, #null#)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}
	
	@Test
	public void testMovingWithConstant() {
		String func = "MOVING";
		String params = "(123000:234000:na1:app_record.count{tagk=*}:avg:15m-avg, #10#)";
		
		String expression = func + params;
		String expression2 = func + suffix + params;
		testValid(expression, expression2);
	}

    @Test
    public void testFunctionsWithConstant() {
        Properties props = readFile("MetricReaderTest.testFunctionsWithConstant.properties");

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String expression = (String) entry.getValue();

            if (!MetricReader.isValid(expression)) {
                fail(entry.toString());
            }
        }
    }
    
    @Test
    public void testSortWithConstant() {
    	String func = "SORT";
    	String params = "(123000:-1m:na1:app_record.count{tagk=tagv}:avg:15m-avg,123000:234000:na1:app_record.count{tak=tagv}:avg, #null#)";
    	
    	String expression = func + params;
    	String expression2 = func + suffix + params;
    	testValid(expression, expression2);
    }
    
    @Test
    public void testDownsampleWithConstant() {
    	String func = "DOWNSAMPLE";
    	String params = "(123000:234000:na1:app_record.count{tagk=tagv1|tagv2}:avg:15m-avg, #10#)";
    	
    	String expression = func + params;
    	String expression2 = func + suffix + params;
    	testValid(expression, expression2);
    }
    
    @Test
    public void testFillWithConstant() {
    	String func = "FILL";
    	String params = "(123000:-1m:na1:app_record.count{tagk=tagv}:avg:15m-avg,123000:234000:na1:app_record.count{tak=tagv}:avg, #null#)";
    	
    	String expression = func + params;
    	String expression2 = func + suffix + params;
    	testValid(expression, expression2);
    }
    
    @Test
    public void testConsecutiveWithConstant() {
    	String func = "CONSECUTIVE";
    	String params = "(123000:234000:na1:app_record.count{tagk=*}:avg:15m-avg, #10s#,#10s#)";
    	
    	String expression = func + params;
    	String expression2 = func + suffix + params;
    	testValid(expression, expression2);
    }
    
    @Test
    public void testInvalidMissingAggregator() {
    	String expression = "IDENTITY(123000:234000:na1:app_record.count{tagk=tagv})" + suffix;
    	assert(!MetricReader.isValid(expression));
    }
    
    @Test
    public void testInvalidMissingScope() {
    	String expression = "IDENTITY(123000:234000:app_record.count{tagk=tagv}:avg)" + suffix;
    	assert(!MetricReader.isValid(expression));
    }
    
    @Test
    public void testInvalidIncorrectNamespace() {
    	String expression = "123000:234000:na1:app_record.count{tagk=tagv}:avg:15m-avg:123" + suffix;
    	assert(!MetricReader.isValid(expression));
    }
  
    private Properties readFile(String fileName) {
        if ((fileName != null) && !fileName.isEmpty()) {
            Properties result = new Properties();

            try(InputStream is = MetricReaderTest.class.getResourceAsStream(fileName)) {
                result.load(is);
            } catch (IOException ex) {
                throw new SystemException(ex);
            }
            return result;
        }
        return null;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
