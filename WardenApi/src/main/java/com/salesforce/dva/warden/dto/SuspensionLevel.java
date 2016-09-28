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
 * DOCUMENT ME!
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonTypeName("suspensionlevel")
public class SuspensionLevel extends Entity {

    //~ Instance fields ******************************************************************************************************************************

    BigInteger policy_id;
    Integer level_number;
    Integer infraction_count;
    BigInteger suspension_time;

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public BigInteger getPolicy_id() {
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
    public Integer getLevelNumber() {
        return level_number;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  level_number  DOCUMENT ME!
     */
    public void setLevelNumber(Integer level_number) {
        this.level_number = level_number;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public Integer getInfractionCount() {
        return infraction_count;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  infraction_count  DOCUMENT ME!
     */
    public void setInfractionCount(Integer infraction_count) {
        this.infraction_count = infraction_count;
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public BigInteger getSuspensionTime() {
        return suspension_time;
    }

    /**
     * DOCUMENT ME!
     *
     * @param  suspension_time  DOCUMENT ME!
     */
    public void setSuspensionTime(BigInteger suspension_time) {
        this.suspension_time = suspension_time;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((infraction_count == null) ? 0 : infraction_count.hashCode());
        result = prime * result + ((level_number == null) ? 0 : level_number.hashCode());
        result = prime * result + ((policy_id == null) ? 0 : policy_id.hashCode());
        result = prime * result + ((suspension_time == null) ? 0 : suspension_time.hashCode());
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

        SuspensionLevel other = (SuspensionLevel) obj;

        if (infraction_count == null) {
            if (other.infraction_count != null) {
                return false;
            }
        } else if (!infraction_count.equals(other.infraction_count)) {
            return false;
        }
        if (level_number == null) {
            if (other.level_number != null) {
                return false;
            }
        } else if (!level_number.equals(other.level_number)) {
            return false;
        }
        if (policy_id == null) {
            if (other.policy_id != null) {
                return false;
            }
        } else if (!policy_id.equals(other.policy_id)) {
            return false;
        }
        if (suspension_time == null) {
            if (other.suspension_time != null) {
                return false;
            }
        } else if (!suspension_time.equals(other.suspension_time)) {
            return false;
        }
        return true;
    }

    @Override
    public Object createExample() {
        SuspensionLevel result = new SuspensionLevel();

        result.setPolicyId(BigInteger.ONE);
        result.setLevelNumber(1);
        result.setInfractionCount(4);
        result.setSuspensionTime(BigInteger.TEN);
        return null;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
