/*
 * Copyright (c) 2017, Salesforce.com, Inc.
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
	 
package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.List;

/**
 * Transforms the name of one or more metrics.<br>
 * <tt>ALIAS(&lt;expr&gt;, &lt;regex&gt;, &lt;type&gt;)</tt>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class AliasTransform implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    // ~ Instance fields
    // ******************************************************************************************************************************
    private static final String REGRE = "regex";
    private static final String LITERAL = "literal";
    private static final String SEARCH_REPLACE_FORM = "^/[^/]*/[^/]*/$";

    //~ Methods **************************************************************************************************************************************

    // ~ Methods
    // **************************************************************************************************************************************
    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException("Alias Transform cannot be performed without an alias expression and an alias type");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        SystemAssert.requireArgument(constants != null && (constants.size() == 2 || constants.size() == 4), "Alias Transform must provide either 2 or 4 constants.");
        
        String aliasTypeForMetric = constants.get(1);
        
        SystemAssert.requireArgument(REGRE.equals(aliasTypeForMetric) || LITERAL.equals(aliasTypeForMetric), 
        		"Alias Transform can only performed for a regular expression or a string literal.");
        if (REGRE.equals(aliasTypeForMetric)) {
            SystemAssert.requireArgument(constants.get(0).matches(SEARCH_REPLACE_FORM), "Please provide a valid search/replace form!");
        }

        String metricSearchRegex = "";
        String metricReplaceText = "";
        String scopeSearchRegex = "";
        String scopeReplaceText = "";
        if (REGRE.equals(aliasTypeForMetric)) {
            metricSearchRegex = constants.get(0).split("/")[1];
            metricReplaceText = constants.get(0).split("/")[2];
        } else if (LITERAL.equals(aliasTypeForMetric)) {
            metricSearchRegex = ".+";
            metricReplaceText = constants.get(0);
        }

        if(constants.size() == 4) {
        	String aliasTypeForScope = constants.get(3);
        	SystemAssert.requireArgument(REGRE.equals(aliasTypeForScope) || LITERAL.equals(aliasTypeForScope), 
        			"Alias Transform can only performed for a regular expression or a string literal.");
        	if (REGRE.equals(aliasTypeForScope)) {
                SystemAssert.requireArgument(constants.get(2).matches(SEARCH_REPLACE_FORM), "Please provide a valid search/replace form!");
            }
        	
        	
            if (REGRE.equals(aliasTypeForScope)) {
            	scopeSearchRegex = constants.get(2).split("/")[1];
            	scopeReplaceText = constants.get(2).split("/")[2];
            } else if (LITERAL.equals(aliasTypeForScope)) {
            	scopeSearchRegex = ".+";
            	scopeReplaceText = constants.get(2);
            }
        }
        
        for (Metric metric : metrics) {
            String newMetricName = metric.getMetric().replaceAll(metricSearchRegex, metricReplaceText);
            metric.setMetric(newMetricName);
            
            if(constants.size() == 4) {
            	String newScopeName = metric.getScope().replaceAll(scopeSearchRegex, scopeReplaceText);
            	metric.setScope(newScopeName);
            }
        }
        
        return metrics;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ALIAS.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, @SuppressWarnings("unchecked") List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Alias doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
