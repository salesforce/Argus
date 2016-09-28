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
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((expiration_timestamp == null) ? 0 : expiration_timestamp.hashCode());
        result = prime * result + ((infraction_timestamp == null) ? 0 : infraction_timestamp.hashCode());
        result = prime * result + ((policy_id == null) ? 0 : policy_id.hashCode());
        result = prime * result + ((user_id == null) ? 0 : user_id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        Infraction other = (Infraction) obj;

        if (expiration_timestamp == null) {
            if (other.expiration_timestamp != null) {
                return false;
            }
        } else if (!expiration_timestamp.equals(other.expiration_timestamp)) {
            return false;
        }
        if (infraction_timestamp == null) {
            if (other.infraction_timestamp != null) {
                return false;
            }
        } else if (!infraction_timestamp.equals(other.infraction_timestamp)) {
            return false;
        }
        if (policy_id == null) {
            if (other.policy_id != null) {
                return false;
            }
        } else if (!policy_id.equals(other.policy_id)) {
            return false;
        }
        if (user_id == null) {
            if (other.user_id != null) {
                return false;
            }
        } else if (!user_id.equals(other.user_id)) {
            return false;
        }
        return true;
    }

    @Override
    public Object createExample() {
        Infraction result = new Infraction();

        result.setPolicyId(BigInteger.ONE);
        result.setUserId(BigInteger.ONE);
        result.setInfractionTimestamp((long) 1);
        result.setExpirationTimestamp((long) 10);
        return null;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
