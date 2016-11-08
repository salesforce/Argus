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

import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
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

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * The entity encapsulates information about whether a particular Argus sub-system has been enabled or disabled.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>SERVICE</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>SERVICE</li>
 *   <li>ENABLED</li>
 * </ul>
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@Table(name = "SERVICE_MANAGEMENT_RECORD", uniqueConstraints = @UniqueConstraint(columnNames = { "service" }))
@NamedQueries(
    { @NamedQuery(name = "ServiceManagementRecord.findByService", query = "SELECT r FROM ServiceManagementRecord r WHERE r.service = :service") }
)
public class ServiceManagementRecord extends JPAEntity {

    //~ Instance fields ******************************************************************************************************************************

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Service service;
    @Basic(optional = false)
    @Column(name = "enabled", nullable = false)
    boolean enabled;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a ServiceManagementRecord object.
     *
     * @param  creator  The creator of this record.
     * @param  service  The service to manage. Cannot be null.
     * @param  enabled  Whether the service is enabled or disabled.
     */
    public ServiceManagementRecord(PrincipalUser creator, Service service, boolean enabled) {
        super(creator);
        setService(service);
        setEnabled(enabled);
    }

    /** Creates a ServiceManagementRecord object. */
    protected ServiceManagementRecord() {
        super(null);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns a record for the specified service.
     *
     * @param   em       The entity manager to use. Cannot be null.
     * @param   service  The service for which to obtain the record. Cannot be null.
     *
     * @return  The management record for the specified service or null if no record exists.
     */
    public static ServiceManagementRecord findServiceManagementRecord(EntityManager em, Service service) {
        requireArgument(em != null, "Entity manager can not be null.");
        requireArgument(service != null, "Service cannot be null.");

        TypedQuery<ServiceManagementRecord> query = em.createNamedQuery("ServiceManagementRecord.findByService", ServiceManagementRecord.class);

        try {
            query.setParameter("service", service);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /**
     * Determine a given service's enability.
     *
     * @param   em       The EntityManager to use.
     * @param   service  The service for which to determine enability.
     *
     * @return  True or false depending on whether the service is enabled or disabled.
     */
    public static boolean isServiceEnabled(EntityManager em, Service service) {
        ServiceManagementRecord record = findServiceManagementRecord(em, service);

        return record == null ? true : record.isEnabled();
    }

    /**
     * Updates the ServiceManagementRecord entity.
     *
     * @param   em      Entity manager. Cannot be null.
     * @param   record  serviceManagementRecord object. Cannot be null.
     *
     * @return  updated ServiceManagementRecord entity.
     */
    @Transactional
    public static ServiceManagementRecord updateServiceManagementRecord(EntityManager em, ServiceManagementRecord record) {
        SystemAssert.requireArgument(em != null, "Entity manager can not be null.");
        SystemAssert.requireArgument(record != null, "ServiceManagementRecord cannot be null.");

        TypedQuery<ServiceManagementRecord> query = em.createNamedQuery("ServiceManagementRecord.findByService", ServiceManagementRecord.class);

        query.setParameter("service", record.getService());
        try {
            ServiceManagementRecord oldRecord = query.getSingleResult();

            oldRecord.setEnabled(record.isEnabled());
            return em.merge(oldRecord);
        } catch (NoResultException ex) {
            return em.merge(record);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the service associated with the record.
     *
     * @return  The service associated with the record.
     */
    public Service getService() {
        return service;
    }

    /**
     * Sets the service associated with the record.
     *
     * @param  service  The service associated with the record. Cannot be null.
     */
    public void setService(Service service) {
        SystemAssert.requireArgument(service != null, "Service cannot be null.");
        this.service = service;
    }

    /**
     * Indicates whether the service is enabled.
     *
     * @return  True if enabled or false if it is disabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the associated service.
     *
     * @param  enabled  True if enabled or false if it is disabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int hashCode() {
        int hash = 7;

        hash = 67 * hash + Objects.hashCode(this.service);
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

        final ServiceManagementRecord other = (ServiceManagementRecord) obj;

        if (this.service != other.service) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ServiceManagementRecord{" + "service=" + service + ", enabled=" + enabled + '}';
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The list of interfaces that are supported by the management service.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Service {

        MONITORING("Monitoring"),
        WARDEN("Warden"),
        SCHEDULING("Scheduling");

        private String _name;

        /**
         * Creates a new Service object.
         *
         * @param  name  The name of the object.
         */
        Service(String name) {
            _name = name;
        }

        /**
         * Returns the name of the object.
         *
         * @return  Returns the name of the service.
         */
        public String getName() {
            return _name;
        }

        /**
         * Sets the name of the object.
         *
         * @param  name  The name of the associated service.
         */
        public void setName(String name) {
            _name = name;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
