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
	 
package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms the name of one or more metrics.<br/>
 * <tt>ALIAS(<expr>, <regex>, <type>)</tt>
 *
 * @param   metrics  The list of metrics to evaluate. Cannot be null or empty.
 * @param   expr     The expression used to transform the names. This can be either a regular expression search and replace or a string literal.
 *                   Cannot be null.
 * @param   type     One of 'regex' or 'literal' Cannot be null.
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
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Alias Transform cannot be performed without an alias expression and an alias type");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        List<Metric> aliasMetricList = new ArrayList<Metric>();

        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 2, "Alias Transform must provide exactly two constants.");
        SystemAssert.requireArgument(REGRE.equals(constants.get(1)) || LITERAL.equals(constants.get(1)),
            "Alias Transform can only performed for a regurlar expression or a string literal.");
        if (constants.get(1).equals(REGRE)) {
            SystemAssert.requireArgument(constants.get(0).matches(SEARCH_REPLACE_FORM), "Please provide a valid search/replace form!");
        }

        String searchRegex = "";
        String replaceText = "";
        String type = constants.get(1);

        if (REGRE.equals(type)) {
            searchRegex = constants.get(0).split("/")[1];
            replaceText = constants.get(0).split("/")[2];
        } else if (LITERAL.equals(type)) {
            searchRegex = ".+";
            replaceText = constants.get(0);
        }
        for (Metric metric : metrics) {
            String name = metric.getMetric();
            String newName = name.replaceAll(searchRegex, replaceText);

            metric.setMetric(newName);
            aliasMetricList.add(metric);
        }
        return aliasMetricList;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ALIAS.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Alias doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
