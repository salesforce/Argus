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
package com.salesforce.dva.warden.client;

import com.salesforce.dva.warden.SuspendedException;
import com.salesforce.dva.warden.WardenClient;
import com.salesforce.dva.warden.dto.Infraction;
import com.salesforce.dva.warden.dto.Policy;

import java.io.IOException;
import java.util.*;

/**
 * DOCUMENT ME!
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
public class DefaultWardenClient implements WardenClient {

    final Map<String, Infraction> _infractions;
    final Map<String, Double> _values;
    final WardenService _service;

    //~ Constructors *********************************************************************************************************************************
    // This is how the client talks to the server.
    public DefaultWardenClient(String endpoint) throws IOException{
        this(WardenService.getInstance(endpoint, 10));
    }

    /** Creates a new DefaultWardenClient object. */
     DefaultWardenClient(WardenService service) {
         _service = service;
        _infractions = Collections.synchronizedMap(new LinkedHashMap<String, Infraction>(){
            @Override
            protected boolean removeEldestEntry(Map.Entry <String, Infraction> eldest) {
                return eldest.getValue().getExpirationTimestamp().compareTo(System.currentTimeMillis()) == -1;
            }
        });

        _values = Collections.synchronizedMap(new HashMap<String, Double>());
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void register(List<Policy> policies, int port) {
        //login
        //pull in data from the server to populate infraction cache
        //update the server with the policy information
        //register for events
    }

    @Override
    public void unregister() {
        //unregister for events
        //logout
        //shutdown
    }

    @Override
    public void updateMetric(Policy policy, String user, double value) throws SuspendedException {
        _checkIsSuspended(policy, user);
        _updateLocalValue(policy, user, value, true);
    }

    @Override
    public void modifyMetric(Policy policy, String user, double delta) throws SuspendedException {
        _checkIsSuspended(policy, user);
        _updateLocalValue(policy, user, delta, false);
    }

    private void _checkIsSuspended(Policy policy, String user ) throws SuspendedException {
       Infraction infraction = _infractions.get(_createKey(policy, user));
        if (infraction != null && infraction.getExpirationTimestamp()>=System.currentTimeMillis()){
            throw new SuspendedException(policy, user, infraction.getExpirationTimestamp(), infraction.getValue());
        }


    }

     String _createKey(Policy policy, String user) {
        return policy.getId().toString() + ":" + user;
    }

    private void _updateLocalValue(Policy policy, String user, Double value, Boolean replace){
        String key = _createKey(policy, user);
        Double cachedValue = _values.get(key);

        if (cachedValue == null){
            cachedValue = replace ? value: policy.getDefaultValue()+value;
        } else {
          cachedValue = replace ? value : cachedValue + value;
        }

        _values.put(key, cachedValue);

    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
