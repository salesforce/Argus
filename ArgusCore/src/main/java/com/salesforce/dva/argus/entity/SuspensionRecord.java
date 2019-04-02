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

import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
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
 * The entity encapsulates information about the suspension record for a given user and a sub-system.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>USER</li>
 *   <li>SUBSYSTEM</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>USER</li>
 *   <li>SUBSYSTEM</li>
 * </ul>
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "SUSPENSION_RECORD", uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "subSystem" }))
@NamedQueries(
    {
        @NamedQuery(
            name = "SuspensionRecord.findByUserAndSubsystem",
            query = "SELECT r FROM SuspensionRecord r WHERE r.user = :user AND r.subSystem = :subSystem"
        ), @NamedQuery(
            name = "SuspensionRecord.findByUser", query = "SELECT r FROM SuspensionRecord r WHERE r.user = :user"
        )
    }
)
public class SuspensionRecord extends JPAEntity {

    //~ Instance fields ******************************************************************************************************************************

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    PrincipalUser user;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SubSystem subSystem;
    @ElementCollection
    @Column(name = "tstamp")
    List<Long> infractionHistory = new ArrayList<>();
    @Basic
    long suspendedUntil = 0;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new SuspensionRecord object .
     *
     * @param  creator              The creator of the record.
     * @param  user                 The user for which to create this record. Cannot be null.
     * @param  subSystem            The subsystem for which to create this record. Cannot be null.
     * @param  infractionTimestamp  The timestamp when an infraction occurred. Must be greater than 0.
     */
    public SuspensionRecord(PrincipalUser creator, PrincipalUser user, SubSystem subSystem, long infractionTimestamp) {
        super(creator);
        setUser(user);
        setSubSystem(subSystem);
        addInfraction(infractionTimestamp);
    }

    /** Creates a new Suspension Record object. */
    protected SuspensionRecord() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Finds the number of user infractions for a subsystem that have occurred since the given start time.
     *
     * @param   em         The entity manager to use. Cannot be null.
     * @param   user       The user. Cannot be null.
     * @param   subSystem  The subsystem. If null, infractions for all subsystems are counted.
     * @param   startTime  The start time threshold.
     *
     * @return  The number of infractions.
     */
    public static int findInfractionCount(EntityManager em, PrincipalUser user, SubSystem subSystem, long startTime) {
        List<SuspensionRecord> records;

        if (subSystem == null) {
            records = findByUser(em, user);
        } else {
            SuspensionRecord record = findByUserAndSubsystem(em, user, subSystem);

            records = record == null ? new ArrayList<SuspensionRecord>(0) : Arrays.asList(new SuspensionRecord[] { record });
        }

        int count = 0;

        for (SuspensionRecord record : records) {
            List<Long> timestamps = record.getInfractionHistory();

            for (Long timestamp : timestamps) {
                if (timestamp > startTime) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Find the suspension record for a given user-subsystem combination.
     *
     * @param   em         The EntityManager to use.
     * @param   user       The user for which to retrieve record.
     * @param   subSystem  THe subsystem for which to retrieve record.
     *
     * @return  The suspension record for the given user-subsystem combination. Null if no such record exists.
     */
    public static SuspensionRecord findByUserAndSubsystem(EntityManager em, PrincipalUser user, SubSystem subSystem) {
        TypedQuery<SuspensionRecord> query = em.createNamedQuery("SuspensionRecord.findByUserAndSubsystem", SuspensionRecord.class);

        try {
            query.setParameter("user", user);
            query.setParameter("subSystem", subSystem);
            query.setHint("javax.persistence.cache.storeMode", "REFRESH");
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Find all suspension records for a given user.
     *
     * @param   em    The EntityManager to use.
     * @param   user  The user for which to retrieve records.
     *
     * @return  A list of suspension records for the given user. An empty list if no such records exist.
     */
    public static List<SuspensionRecord> findByUser(EntityManager em, PrincipalUser user) {
        TypedQuery<SuspensionRecord> query = em.createNamedQuery("SuspensionRecord.findByUser", SuspensionRecord.class);

        try {
            query.setParameter("user", user);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<>(0);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the suspended user.
     *
     * @return  The suspended user.
     */
    public PrincipalUser getUser() {
        return user;
    }

    /**
     * Sets the suspended user.
     *
     * @param  user  The suspended user. Cannot be null.
     */
    public void setUser(PrincipalUser user) {
        SystemAssert.requireArgument(user != null, "User cannot be set to null for a suspension record.");
        this.user = user;
    }

    /**
     * Returns the affected subsystem.
     *
     * @return  The subsystem.
     */
    public SubSystem getSubSystem() {
        return subSystem;
    }

    /**
     * Sets the affected subsystem.
     *
     * @param  subSystem  The subsystem.
     */
    public void setSubSystem(SubSystem subSystem) {
        SystemAssert.requireArgument(subSystem != null, "Subsystem cannot be set to null for a suspension record.");
        this.subSystem = subSystem;
    }

    /**
     * Returns the detailed infraction history.
     *
     * @return  The infraction history. Will not be null, but may be empty.
     */
    public List<Long> getInfractionHistory() {
        return infractionHistory;
    }

    /**
     * Sets the detailed infraction history.
     *
     * @param  history  The detailed infraction history. Cannot be null or empty.
     */
    public void setInfractionHistory(List<Long> history) {
        SystemAssert.requireArgument(history != null && !history.isEmpty(), "Infraction History cannot be set to null or empty.");
        this.infractionHistory = history;
    }

    /**
     * Returns the time at which the suspension expires.
     *
     * @return  The time at which the suspension expires.
     */
    public long getSuspendedUntil() {
        return suspendedUntil;
    }

    /**
     * Indicates if the user is suspended indefinitely.
     *
     * @return  True if the user is suspended indefinitely.
     */
    public boolean isSuspendedIndefinitely() {
        return suspendedUntil == -1;
    }

    /**
     * Sets the time at which the suspension expires.
     *
     * @param  suspendedUntil  The time at which the suspension expires. If the value is -1, the suspension is indefinite.
     */
    public void setSuspendedUntil(long suspendedUntil) {
        this.suspendedUntil = suspendedUntil;
    }

    /**
     * Adds an infraction to the history.
     *
     * @param  timestamp  The timestamp of the infraction.
     */
    public void addInfraction(long timestamp) {
        SystemAssert.requireArgument(timestamp > 0 || timestamp == -1, "Timestamp must be positive or -1.");
        this.infractionHistory.add(timestamp);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();

        result = prime * result + ((subSystem == null) ? 0 : subSystem.hashCode());
        result = prime * result + ((user == null) ? 0 : user.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        SuspensionRecord other = (SuspensionRecord) obj;

        if (subSystem != other.subSystem) {
            return false;
        }
        if (user == null) {
            if (other.user != null) {
                return false;
            }
        } else if (!user.equals(other.user)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "SuspensionRecord{" + "user=" + user + ", subSystem=" + subSystem + ", infractionHistory=" + infractionHistory + ", suspendedUntil=" +
            suspendedUntil + '}';
    }

    /**
     * Indicates whether a suspension is active.
     *
     * @return  True if a suspension is active.
     */
    public boolean isSuspended() {
        return System.currentTimeMillis() < getSuspendedUntil() || isSuspendedIndefinitely();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
