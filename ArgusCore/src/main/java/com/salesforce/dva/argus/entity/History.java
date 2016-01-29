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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static org.joda.time.DateTimeConstants.MILLIS_PER_WEEK;

/**
 * It encapsulates the information about job execution.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@NamedQueries(
    {
        @NamedQuery(name = "History.findByJob", query = "SELECT a FROM History a WHERE a.entity = :entity order by a.createdDate DESC"),
        @NamedQuery(
            name = "History.findByJobAndStatus",
            query = "SELECT a FROM History a WHERE a.entity = :entity and a.jobStatus = :jobStatus order by a.createdDate DESC"
        ), @NamedQuery(
            name = "History.cullExpired", query = "DELETE FROM History AS h WHERE H.createdDate < :expirationDate"
        ), @NamedQuery(name = "History.cullOrphans", query = "DELETE FROM History AS h WHERE h.id IS NULL")
    }
)
@Table(indexes = { @Index(columnList = "entity_id") })
public class History implements Serializable, Identifiable {

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Id
    private BigInteger id;
    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createdDate;
    @Lob
    private String message;
    @Basic(optional = false)
    @Column(nullable = false)
    private String hostName;
    @ManyToOne(optional = true)
    @JoinColumn(nullable = true, name = "entity_id")
    private JPAEntity entity;
    @Enumerated(EnumType.STRING)
    private JobStatus jobStatus;
    private long waitTime;
    private long executionTime;
    @Basic(optional = false)
    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    protected Date modifiedDate;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new History object.
     *
     * @param  message    Describes the job status in detail.
     * @param  hostname   The host where the job is executed. Cannot be null.
     * @param  entity     The job entity. Cannot be null.
     * @param  jobStatus  Status of the job.
     */
    public History(String message, String hostname, JPAEntity entity, JobStatus jobStatus) {
        this(message, hostname, entity, jobStatus, 0, 0);
    }

    /**
     * Creates a new History object.
     *
     * @param  message        Describes the job status in detail.
     * @param  hostname       The host where the job is executed. Cannot be null.
     * @param  entity         The job entity. Cannot be null.
     * @param  jobStatus      Status of the job.
     * @param  waitTime       The job waiting time in MS .
     * @param  executionTime  The job execution time in MS.
     */
    public History(String message, String hostname, JPAEntity entity, JobStatus jobStatus, long waitTime, long executionTime) {
        setMessage(message);
        setHostName(hostname);
        setEntity(entity);
        setJobStatus(jobStatus);
        setWaitTime(waitTime);
        setExecutionTime(executionTime);
    }

    /** Creates a new History object. */
    protected History() { }

    //~ Methods **************************************************************************************************************************************

    /**
     * Finds all history records for the given job.
     *
     * @param   em     Entity manager. Cannot be null.
     * @param   job    The job for which the history will be returned. Cannot be null.
     * @param   limit  The number of results to return.
     *
     * @return  History belongs to given entity.
     */
    public static List<History> findHistoryByJob(EntityManager em, JPAEntity job, BigInteger limit) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(job != null, "The job cannot be null.");

        TypedQuery<History> query = em.createNamedQuery("History.findByJob", History.class);

        if (limit != null) {
            query.setMaxResults(limit.intValue());
        }
        try {
            query.setParameter("entity", job);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<History>(0);
        }
    }

    /**
     * Finds all history records for the given job and job status.
     *
     * @param   em         Entity manager. Cannot be null.
     * @param   job        The job for which the history will be returned. Cannot be null.
     * @param   limit      The number of results to return.
     * @param   jobStatus  The status of the job. Cannot be null.
     *
     * @return  History belongs to given job.
     */
    public static List<History> findHistoryByJobAndStatus(EntityManager em, JPAEntity job, BigInteger limit, JobStatus jobStatus) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(job != null, "The job cannot be null.");

        TypedQuery<History> query = em.createNamedQuery("History.findByJobAndStatus", History.class);

        if (limit != null) {
            query.setMaxResults(limit.intValue());
        }
        try {
            query.setParameter("entity", job);
            query.setParameter("jobStatus", jobStatus);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<History>(0);
        }
    }

    /**
     * Deletes the old job history.
     *
     * @param   em  Entity manager. Cannot be null.
     *
     * @return  no. of job history records deleted.
     */
    public static int deleteExpiredHistory(EntityManager em) {
        requireArgument(em != null, "Entity manager cannot be null.");

        Query query = em.createNamedQuery("History.cullExpired");

        query.setParameter("expirationDate", new Date(System.currentTimeMillis() - (2 * MILLIS_PER_WEEK)));
        return query.executeUpdate();
    }

    /**
     * Deletes the old job history.
     *
     * @param   em  Entity manager. Cannot be null.
     *
     * @return  no. of job history records deleted.
     */
    public static int deleteOrphans(EntityManager em) {
        requireArgument(em != null, "Entity manager cannot be null.");

        Query query = em.createNamedQuery("History.cullOrphans");

        return query.executeUpdate();
    }

    //~ Methods **************************************************************************************************************************************

    /** Updates the created date and modified date before the entity is written to DB. */
    @PrePersist
    @PreUpdate
    protected void preUpdate() {
        this.modifiedDate = new Date();
        if (this.createdDate == null) {
            this.createdDate = this.modifiedDate;
        }
    }

    /**
     * returns the job status,
     *
     * @return  The job status.
     */
    public JobStatus getJobStatus() {
        return jobStatus;
    }

    /**
     * Sets the job status.
     *
     * @param  jobStatus  The job status
     */
    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    /**
     * Returns job waiting time.
     *
     * @return  The job waiting time in MS.
     */
    public long getWaitTime() {
        return waitTime;
    }

    /**
     * Sets the waiting time.
     *
     * @param  waitTime  The job waiting time in MS.
     */
    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    /**
     * Returns the job execution time.
     *
     * @return  The job execution time.
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     * Sets the job execution time.
     *
     * @param  executionTime  The job execution time.
     */
    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }

    /**
     * Returns the Id.
     *
     * @return  The Id.
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Returns the job modified date.
     *
     * @return  The modified date.
     */
    public Date getModifiedDate() {
        return modifiedDate;
    }

    /**
     * Returns the job history record creation time.
     *
     * @return  The timestamp when job history record was created
     */
    public Date getCreatedDate() {
        return createdDate == null ? null : new Date(createdDate.getTime());
    }

    /**
     * Returns the exception message.
     *
     * @return  The exception message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the exception message.
     *
     * @param  message  The exception message. Cannot be null or empty.
     */
    public void setMessage(String message) {
        requireArgument(message != null && !message.isEmpty(), "Message cannot be null or empty.");
        this.message = message;
    }

    /**
     * returns the host name which encountered exception/error.
     *
     * @return  The host name.
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Sets the host name.
     *
     * @param  hostName  The host name. Cannot be null or empty.
     */
    public void setHostName(String hostName) {
        requireArgument(hostName != null && !hostName.isEmpty(), "Hostname cannot be null or empty.");
        this.hostName = hostName;
    }

    /**
     * Returns the JPA entity.
     *
     * @return  The JPA entity which caused exception. Cannot be null or empty.
     */
    public JPAEntity getEntity() {
        return entity;
    }

    /**
     * Sets the JPA entity.
     *
     * @param  entity  The JPA entity. Cannot be null or empty.
     */
    public void setEntity(JPAEntity entity) {
        this.entity = entity;
    }

    @Override
    public String toString() {
        return "History{" + "id=" + getId() + ", createdDate=" + getCreatedDate() + ", message= Too large to display here, hostName=" +
            getHostName() + ", jobStatus=" + getJobStatus() + ", jobId=" + (getEntity() == null ? null : getEntity().id) + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final History other = (History) obj;

        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;

        hash = 53 * hash + Objects.hashCode(this.id);
        return hash;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Describes the job status.
     *
     * @author  Raj Sarkapally (rsarkapally@salesforce.com)
     */
    public enum JobStatus {

        DEQUEUED("Job dequeued from the message queue."),
        STARTED("Job started."),
        SUCCESS("Job successfully completed."),
        FAILURE("Job failed."),
        ERROR("Exception occured."),
        INTERRUPTED("Job interrupted."),
        TIMED_OUT("Job timed out."),
        QUEUED("Job queued.");

        String description;

        private JobStatus(String description) {
            this.description = description;
        }

        /**
         * Converts a string to a job status type.
         *
         * @param   name  The job status.
         *
         * @return  The corresponding job status type.
         *
         * @throws  IllegalArgumentException  If no corresponding trigger type is found.
         */
        @JsonCreator
        public static JobStatus fromString(String name) {
            for (JobStatus t : JobStatus.values()) {
                if (t.toString().equalsIgnoreCase(name)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Job statust type does not exist.");
        }

        /**
         * Returns the job status description.
         *
         * @return  The job status description.
         */
        public String getDescription() {
            return description;
        }

        /**
         * Returns the job status.
         *
         * @return  The job status.
         */
        @JsonValue
        public String value() {
            return this.toString();
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
