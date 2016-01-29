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

import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityTransaction;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;

/**
 * An interlock object that uses a database record as a global semaphore. This entity has no publicly visible state. It uses unique ID constraints as
 * a locking mechanism. The action of obtaining a lock creates a record having a known primary key value and releasing the lock deletes the record.
 *
 * <p>Only a single lock may be obtained at any time across all JVM's in which the object is used. Any attempt to obtain a lock that is already in use
 * or to release a nonexistent lock will result in a runtime exception being thrown.</p>
 *
 * <p>Clients may clobber an existing lock by using the expiration threshold for a lock while obtaining it. If a lock of the requested type exists,
 * but is older than the expiration threshold indicated, a new lock is obtained. If this occurs, any processes depending on the old lock will fail
 * when attempting to release the lock. The default expiration threshold is 1 second, but may be overridden by the caller using the obtain lock
 * methods having an expiration parameter.</p>
 *
 * <p>Subclass implementations should define their own lock values. This can be done either using an enumeration, static variables or any other means
 * by which a unique integer is mapped to a lock type. For example:</p>
 * <code>private static final Long SCHEDULE_LOCK = 1l; private static final Long RUN_LOCK = 2l; private static final Long MAINTENANCE_LOCK = 3l;
 * </code>
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Entity
@Table(name = "GLOBAL_INTERLOCK")
public class GlobalInterlock implements Serializable {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalInterlock.class);

    //~ Instance fields ******************************************************************************************************************************

    @Id
    @Column(name = "id", nullable = false)
    private Long id;
    @Basic(optional = false)
    @Column(name = "lock_time", nullable = false)
    private Long lockTime;
    @Basic(optional = false)
    @Column(name = "ipaddr", nullable = false, length = 255)
    private String ipaddr;
    @Basic(optional = false)
    @Column(name = "note", nullable = false, length = 255)
    private String note;

    //~ Constructors *********************************************************************************************************************************

    /** To be used by the persistence infrastructure only. */
    protected GlobalInterlock() {
        this.ipaddr = SystemConfiguration.getHostname();
    }

    /**
     * Constructor used to construct the specific lock types.
     *
     * @param  type  The type of the lock specified as a number. Must be greater than zero.
     * @param  note  The note to associate with the lock.
     */
    protected GlobalInterlock(long type, String note) {
        this();
        this.id = type;
        this.note = note;
    }

    //~ Methods **************************************************************************************************************************************

    private static GlobalInterlock _findAndRefreshLock(EntityManager em, long type) {
        GlobalInterlock lock = em.find(GlobalInterlock.class, type);

        if (lock != null) {
            try {
                em.refresh(lock);
                lock = em.find(GlobalInterlock.class, type);
            } catch (EntityNotFoundException ex) {
                LOGGER.warn("Lock of type {} was expired by another process.", type);
                lock = null;
            }
        }
        return lock;
    }

    /**
     * Obtains a global lock of a given type.
     *
     * @param   em          The entity manager to use. Cannot be null.
     * @param   expiration  The time in milliseconds after which an existing lock may be clobbered and re-acquired.
     * @param   type        The application specific lock type represented as a long value.
     * @param   note        A descriptive note about the lock context.
     *
     * @return  The unique key to be used from clients when releasing the lock.
     *
     * @throws  GlobalInterlockException  If the lock cannot be obtained.
     */
    public static String obtainLock(EntityManager em, long expiration, long type, String note) {
        EntityTransaction tx = null;

        /* remove the existing lock if it's expired */
        try {
            long now = System.currentTimeMillis();

            tx = em.getTransaction();
            tx.begin();

            GlobalInterlock lock = _findAndRefreshLock(em, type);

            if (lock != null && now - lock.lockTime > expiration) {
                em.remove(lock);
                em.flush();
            }
            tx.commit();
        } catch (Exception ex) {
            LOGGER.warn("An error occurred trying to refresh the type {} lock: {}", type, ex.getMessage());
            LOGGER.debug(ex.getMessage(), ex);
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
        }

        /* attempt to obtain a lock */
        try {
            tx = em.getTransaction();
            tx.begin();

            GlobalInterlock lock = em.merge(new GlobalInterlock(type, note));

            em.flush();
            tx.commit();
            return Long.toHexString(lock.lockTime);
        } catch (Exception ex) {
            throw new GlobalInterlockException("Could not obtain " + type + " lock", ex);
        } finally {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
        }
    }

    /**
     * Releases a global lock of the indicated type if the supplied key is a match for the lock.
     *
     * @param   em    The entity manager factory to use. Cannot be null.
     * @param   type  The type of key to release.
     * @param   key   The key value obtained from the lock creation.
     *
     * @throws  GlobalInterlockException  If the lock cannot be released.
     */
    public static void releaseLock(EntityManager em, long type, String key) {
        EntityTransaction tx = null;

        /* remove the existing lock if it matches the key. */
        try {
            tx = em.getTransaction();
            tx.begin();

            GlobalInterlock lock = _findAndRefreshLock(em, type);

            if (lock == null) {
                throw new GlobalInterlockException("No lock of type " + type + " exists for key " + key + ".");
            }

            String ref = Long.toHexString(lock.lockTime);

            if (ref.equalsIgnoreCase(key)) {
                em.remove(lock);
                em.flush();
                tx.commit();
            } else {
                throw new GlobalInterlockException("This process doesn't own the type " + type + " lock having key " + key + ".");
            }
        } finally {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
        }
    }

    /**
     * Refreshes a global lock of the indicated type if the supplied key is a match for the lock.
     *
     * @param   em    The entity manager factory to use. Cannot be null.
     * @param   type  The type of key to release.
     * @param   key   The key value obtained from the lock creation.
     * @param   note  A descriptive note about the lock context.
     *
     * @return  The updated unique key to be used from clients when releasing the lock.
     *
     * @throws  GlobalInterlockException  If the lock cannot be refreshed.
     */
    public static String refreshLock(EntityManager em, long type, String key, String note) {
        EntityTransaction tx = null;

        /* refresh the existing lock if it matches the key. */
        try {
            tx = em.getTransaction();
            tx.begin();

            GlobalInterlock lock = _findAndRefreshLock(em, type);

            if (lock == null) {
                throw new GlobalInterlockException("No lock of type " + type + " exists for key " + key + ".");
            }

            String ref = Long.toHexString(lock.lockTime);

            if (ref.equalsIgnoreCase(key)) {
                lock.lockTime = System.currentTimeMillis();
                lock.note = note;
                em.merge(lock);
                em.flush();
                tx.commit();
                return Long.toHexString(lock.lockTime);
            }
            throw new GlobalInterlockException("This process doesn't own the type " + type + " lock having key " + key + ".");
        } finally {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
        }
    }

    //~ Methods **************************************************************************************************************************************

    /** Used to automatically update the timestamp of locks. */
    @PrePersist
    public void prePersist() {
        lockTime = System.currentTimeMillis();
    }

    @Override
    public int hashCode() {
        int hash = 3;

        hash = 59 * hash + Objects.hashCode(this.id);
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

        final GlobalInterlock other = GlobalInterlock.class.cast(obj);

        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "GlobalInterlock{" + "id=" + id + ", lockTime=" + lockTime + ", ipaddr=" + ipaddr + ", note=" + note + '}';
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * An interlock specific exception class.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static class GlobalInterlockException extends IllegalStateException {

        /**
         * Creates a new GlobalInterlockException.
         *
         * @param  msg  A descriptive message.
         *
         * @see    IllegalStateException(String).
         */
        public GlobalInterlockException(String msg) {
            super(msg);
        }

        /**
         * Creates a new GlobalInterlockException.
         *
         * @param  msg  A descriptive message.
         * @param  ex   The root cause of the exception.
         *
         * @see    IllegalStateException(String, Throwable).
         */
        public GlobalInterlockException(String msg, Throwable ex) {
            super(msg, ex);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
