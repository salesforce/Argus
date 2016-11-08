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
	 
package com.salesforce.dva.argus.entity;

import com.salesforce.dva.argus.service.WardenService.PolicyCounter;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;

/**
 * The entity encapsulates information about whether a particular Argus sub-system has been enabled or disabled.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>USER</li>
 *   <li>POLICY_COUNTER</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>USER</li>
 *   <li>POLICY_COUNTER</li>
 *   <li>LIMIT</li>
 * </ul>
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "POLICY_LIMIT", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "counter" }))
@NamedQueries(
    {
        @NamedQuery(
            name = "PolicyLimit.findPolicyLimitByUserAndCounter", query = "SELECT r FROM PolicyLimit r WHERE r.user = :user AND r.counter = :counter"
        )
    }
)
public class PolicyLimit extends JPAEntity {

    //~ Instance fields ******************************************************************************************************************************

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    PrincipalUser user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PolicyCounter counter;
    @Basic(optional = false)
    @Column(name = "user_limit", nullable = false)
    double limit;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new PolicyLimit object.
     *
     * @param  creator  The user who created the policy limit.
     * @param  user     The user for which to set the limit. Cannot be null.
     * @param  counter  The policy limit counter for which to set the limit. Cannot be null.
     * @param  limit    The limit to set.
     */
    public PolicyLimit(PrincipalUser creator, PrincipalUser user, PolicyCounter counter, double limit) {
        super(creator);
        setUser(user);
        setCounter(counter);
        setLimit(limit);
    }

    /** Creates a new PolicyLimit object. */
    protected PolicyLimit() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Retrieves the policy limit object for a given user-counter combination.
     *
     * @param   em       The EntityManager to use.
     * @param   user     The user for which to retrieve the policy limit.
     * @param   counter  The counter for which to retrieve the policy limit.
     *
     * @return  The policy limit object for the given user-counter combination. Null if no such record exists.
     */
    public static PolicyLimit findPolicyLimitByUserAndCounter(EntityManager em, PrincipalUser user, PolicyCounter counter) {
        TypedQuery<PolicyLimit> query = em.createNamedQuery("PolicyLimit.findPolicyLimitByUserAndCounter", PolicyLimit.class);

        try {
            query.setParameter("user", user);
            query.setParameter("counter", counter);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Retrieves the limit for a given user-counter combination.
     *
     * @param   em       The EntityManager to use.
     * @param   user     The user for which to retrieve the policy limit.
     * @param   counter  The counter for which to retrieve the policy limit.
     *
     * @return  The limit for the given user-counter combination. Default value of the counter is no such record exists.
     */
    public static double getLimitByUserAndCounter(EntityManager em, PrincipalUser user, PolicyCounter counter) {
        PolicyLimit pLimit = findPolicyLimitByUserAndCounter(em, user, counter);

        if (pLimit != null) {
            return pLimit.getLimit();
        }
        return counter.getDefaultValue();
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the user associated with the policy.
     *
     * @return  The user associated with the policy.
     */
    public PrincipalUser getUser() {
        return user;
    }

    /**
     * Sets the user associated with the policy.
     *
     * @param  user  The user associated with the policy. Cannot be null.
     */
    public void setUser(PrincipalUser user) {
        SystemAssert.requireArgument(user != null, "User cannot be null");
        this.user = user;
    }

    /**
     * Returns the policy counter. The policy counter indicates the policy to which this limit applies.
     *
     * @return  The policy counter.
     */
    public PolicyCounter getCounter() {
        return counter;
    }

    /**
     * Sets the policy counter to which this limit applies.
     *
     * @param  counter  The policy counter. Cannot be null.
     */
    public void setCounter(PolicyCounter counter) {
        SystemAssert.requireArgument(counter != null, "Policy Counter cannot be null");
        this.counter = counter;
    }

    /**
     * Returns the limit value.
     *
     * @return  The limit value.
     */
    public double getLimit() {
        return limit;
    }

    /**
     * Sets the limit value.
     *
     * @param  limit  The limit value.
     */
    public void setLimit(double limit) {
        SystemAssert.requireArgument(limit > 0, "Limit must be greater than 0");
        this.limit = limit;
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 11 * hash + Objects.hashCode(this.user);
        hash = 11 * hash + Objects.hashCode(this.counter);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final PolicyLimit other = (PolicyLimit) obj;

        if (!Objects.equals(this.user, other.user)) {
            return false;
        }
        if (this.counter != other.counter) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "PolicyLimit{" + "user=" + user + ", counter=" + counter + ", limit=" + limit + '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
