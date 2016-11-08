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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;

/**
 * The entity encapsulates information about the suspension policy/levels for a given subsystem.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>SUB-SYSTEM</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>SUB-SYSTEM</li>
 *   <li>LEVELS</li>
 * </ul>
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "SUBSYSTEM_SUSPENSION_LEVELS", uniqueConstraints = @UniqueConstraint(columnNames = { "subSystem" }))
@NamedQueries(
    {
        @NamedQuery(
            name = "SubsystemSuspensionLevels.findBySubsystem", query = "SELECT r FROM SubsystemSuspensionLevels r WHERE r.subSystem = :subSystem"
        )
    }
)
public class SubsystemSuspensionLevels extends JPAEntity {

    //~ Instance fields ******************************************************************************************************************************

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SubSystem subSystem;
    @ElementCollection
    @Column(nullable = false)
    Map<Integer, Long> levels = new TreeMap<>();

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new SubsystemSuspensionLevels object.
     *
     * @param  creator    The creator of this record.
     * @param  subSystem  The subsystem for which to set suspension levels. Cannot be null.
     * @param  levels     The suspension levels for this subsystem. Cannot be null.
     */
    public SubsystemSuspensionLevels(PrincipalUser creator, SubSystem subSystem, Map<Integer, Long> levels) {
        super(creator);
        setSubSystem(subSystem);
        setLevels(levels);
    }

    /** Creates a new SubsystemSuspensionLevels object. */
    protected SubsystemSuspensionLevels() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Retrieves the SubsystemSuspensionLevels object for the given subsystem.
     *
     * @param   em         The EntityManager to use.
     * @param   subSystem  The subsystem for which to retrieve suspension level.
     *
     * @return  The SubsystemSuspensionLevels object for the given subsystem. Throws a runtime exception if no such record exists. This is because
     *          suspension levels for non-existent subsystems must never be queried.
     */
    public static SubsystemSuspensionLevels findBySubsystem(EntityManager em, SubSystem subSystem) {
        SystemAssert.requireArgument(em != null, "Entity manager can not be null.");
        SystemAssert.requireArgument(subSystem != null, "Subsystem cannot be null.");

        TypedQuery<SubsystemSuspensionLevels> query = em.createNamedQuery("SubsystemSuspensionLevels.findBySubsystem",
            SubsystemSuspensionLevels.class);

        try {
            query.setParameter("subSystem", subSystem);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            Map<Integer, Long> levels = new HashMap<>();

            levels.put(1, 60 * 60 * 1000L);
            levels.put(2, 10 * 60 * 60 * 1000L);
            levels.put(3, 24 * 60 * 60 * 1000L);
            levels.put(4, 3 * 24 * 60 * 60 * 1000L);
            levels.put(5, 10 * 24 * 60 * 60 * 1000L);

            SubsystemSuspensionLevels suspensionLevels = new SubsystemSuspensionLevels(null, subSystem, levels);

            return em.merge(suspensionLevels);
        }
    }

    /**
     * Retrieves the suspension levels for the given subsystem.
     *
     * @param   em         The EntityManager to use.
     * @param   subSystem  The subsystem for which to retrieve suspension levels.
     *
     * @return  The suspension levels for the given subsystem.
     */
    public static Map<Integer, Long> getSuspensionLevelsBySubsystem(EntityManager em, SubSystem subSystem) {
        return findBySubsystem(em, subSystem).getLevels();
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the subsystem for the suspension.
     *
     * @return  The subsystem for the suspension.
     */
    public SubSystem getSubSystem() {
        return subSystem;
    }

    /**
     * Sets the subsystem for the suspension.
     *
     * @param  subSystem  The subsystem. Cannot be null.
     */
    public void setSubSystem(SubSystem subSystem) {
        SystemAssert.requireArgument(subSystem != null, "Sub-system cannot be null");
        this.subSystem = subSystem;
    }

    /**
     * Returns a map of infraction count thresholds to suspension time in milliseconds.
     *
     * @return  The infraction count to suspension time map.
     */
    public Map<Integer, Long> getLevels() {
        return levels;
    }

    /**
     * Sets the suspension time in milliseconds for the corresponding infraction count.
     *
     * @param  levels  The map of infraction counts to suspension times.
     */
    public void setLevels(Map<Integer, Long> levels) {
        SystemAssert.requireArgument(levels != null, "Levels cannot be null");
        this.levels.clear();
        this.levels.putAll(levels);
    }

    /**
     * Adds a suspension level.
     *
     * @param  infractionCount        The infraction count threshold.
     * @param  timeToSuspendInMillis  The suspension time in milliseconds.
     */
    public void addLevel(int infractionCount, long timeToSuspendInMillis) {
        this.levels.put(infractionCount, timeToSuspendInMillis);
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 31 * hash + Objects.hashCode(this.subSystem);
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

        final SubsystemSuspensionLevels other = (SubsystemSuspensionLevels) obj;

        if (this.subSystem != other.subSystem) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "SubsystemSuspensionLevels{" + "subSystem=" + subSystem + ", levels=" + levels + '}';
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
