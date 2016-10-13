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
package com.salesforce.dva.warden.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.math.BigInteger;

/**
 * Infraction History Dto.
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonTypeName("infraction")
public class Infraction extends Entity {

    //~ Instance fields ******************************************************************************************************************************

    private BigInteger policy_id;
    private BigInteger user_id;
    private Long infraction_timestamp;
    private Long expiration_timestamp;
    private Double value;

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public BigInteger getPolicyId() {
        return policy_id;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  policy_id  DOCUMENT ME!
     */
    public void setPolicyId(BigInteger policy_id) {
        this.policy_id = policy_id;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public BigInteger getUserId() {
        return user_id;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  user_id  DOCUMENT ME!
     */
    public void setUserId(BigInteger user_id) {
        this.user_id = user_id;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Long getInfractionTimestamp() {
        return infraction_timestamp;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  infraction_timestamp  DOCUMENT ME!
     */
    public void setInfractionTimestamp(Long infraction_timestamp) {
        this.infraction_timestamp = infraction_timestamp;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Long getExpirationTimestamp() {
        return expiration_timestamp;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  expiration_timestamp  DOCUMENT ME!
     */
    public void setExpirationTimestamp(Long expiration_timestamp) {
        this.expiration_timestamp = expiration_timestamp;
    }

    @Override
    public Object createExample() {
        Infraction result = new Infraction();

        result.setPolicyId(BigInteger.ONE);
        result.setUserId(BigInteger.ONE);
        result.setInfractionTimestamp((long) 1);
        result.setExpirationTimestamp((long) 10);
        result.setValue(1.00);
        return null;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Infraction that = (Infraction) o;

        if (!policy_id.equals(that.policy_id)) return false;
        if (!user_id.equals(that.user_id)) return false;
        if (!infraction_timestamp.equals(that.infraction_timestamp)) return false;
        if (!expiration_timestamp.equals(that.expiration_timestamp)) return false;
        return getValue().equals(that.getValue());

    }

    @Override
    public int hashCode() {
        int result = policy_id.hashCode();
        result = 31 * result + user_id.hashCode();
        result = 31 * result + infraction_timestamp.hashCode();
        result = 31 * result + expiration_timestamp.hashCode();
        result = 31 * result + getValue().hashCode();
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
