package com.salesforce.dva.warden;

import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.SuspensionLevel;

/**
 * Created by jbhatt on 10/7/16.
 */
public class SuspendedException extends Exception {
    private final Policy _policy;
    private final String _user;
    private final Long _expires;
    private final Double _value;

    public SuspendedException(Policy policy, String user, Long expires, Double value){
       _policy = policy;
        _user = user;
        _expires = expires;
        _value = value;
    }

    public Policy getPolicy() {
        return _policy;
    }

    public String getUser() {
        return _user;
    }

    public Long getExpires() {
        return _expires;
    }

    public Double getValue() {
        return _value;
    }
}
