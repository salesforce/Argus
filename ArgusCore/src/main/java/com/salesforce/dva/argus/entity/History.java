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

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static org.joda.time.DateTimeConstants.MILLIS_PER_WEEK;

import java.io.Serializable;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

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
import javax.persistence.Lob;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.UniqueConstraint;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * It encapsulates the information about job execution.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@NamedQueries(
    {
        @NamedQuery(
        	name = "History.findByJob", 
        	query = "SELECT a FROM History a WHERE a.entityId = :entityId order by a.creationTime DESC"),
        @NamedQuery(
            name = "History.findByJobAndStatus",
            query = "SELECT a FROM History a WHERE a.entityId = :entityId and a.jobStatus = :jobStatus order by a.creationTime DESC"
        ), 
        @NamedQuery(
            name = "History.cullExpired", 
            query = "DELETE FROM History AS h WHERE H.creationTime < :expiryTime"
        )
    }
)
@Table(indexes = { @Index(columnList = "entityId") }, uniqueConstraints = @UniqueConstraint(columnNames = { "entityId", "creationTime" }))
public class History implements Serializable, Identifiable {

    //~ Instance fields ******************************************************************************************************************************

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Basic(optional = false)
    @Column(nullable = false, updatable = false)
	private BigInteger id;
	
    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    private BigInteger entityId;
    @Basic(optional = false)
    @Column(nullable = false, updatable = false)
    private Long creationTime;
    @Lob
    private String message;
    @Basic(optional = false)
    @Column(nullable = false)
    private String hostName;
    @Enumerated(EnumType.STRING)
    private JobStatus jobStatus;
    private long executionTime;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new History object.
     *
     * @param  message    Describes the job status in detail.
     * @param  hostname   The host where the job is executed. Cannot be null.
     * @param  entityId   The entity id of the job for which this history is created. Cannot be null.
     * @param  jobStatus  Status of the job.
     */
    public History(String message, String hostname, BigInteger entityId, JobStatus jobStatus) {
        this(message, hostname, entityId, jobStatus, 0);
    }

    /**
     * Creates a new History object.
     *
     * @param  message        Describes the job status in detail.
     * @param  hostname       The host where the job is executed. Cannot be null.
     * @param  entityId   	  The entity id of the job for which this history is created. Cannot be null.
     * @param  jobStatus      Status of the job.
     * @param  executionTime  The job execution time in MS.
     */
    public History(String message, String hostname, BigInteger entityId, JobStatus jobStatus, long executionTime) {
        this(message, hostname, entityId, jobStatus, executionTime, System.currentTimeMillis());
    }
    
    /**
     * Creates a new History object.
     *
     * @param  message        Describes the job status in detail.
     * @param  hostname       The host where the job is executed. Cannot be null.
     * @param  entityId   	  The entity id of the job for which this history is created. Cannot be null.
     * @param  jobStatus      Status of the job.
     * @param  executionTime  The job execution time in ms.
     * @param  creationTime   The history object creation timestamp.
     */
    public History(String message, String hostname, BigInteger entityId, JobStatus jobStatus, long executionTime, long creationTime) {
        setMessage(message);
        setHostName(hostname);
        setEntityId(entityId);
        setJobStatus(jobStatus);
        setExecutionTime(executionTime);
        setCreationTime(creationTime);
    }

    /** Creates a new History object. */
    protected History() { }

    public static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>() {

        @Override
        protected SimpleDateFormat initialValue() {
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");

            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            return sdf;
        }
    };

    //~ Methods **************************************************************************************************************************************

    /**
     * Finds all history records for the given job.
     *
     * @param   em     	Entity manager. Cannot be null.
     * @param   jobId   The job for which the history will be returned. Cannot be null.
     * @param   limit  	The number of results to return.
     *
     * @return  History belongs to given entity.
     */
    public static List<History> findHistoryByJob(EntityManager em, BigInteger jobId, int limit) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(jobId != null, "The jobId cannot be null.");
        requireArgument(limit > 0, "Limit must be a positive integer.");

        TypedQuery<History> query = em.createNamedQuery("History.findByJob", History.class);
        query.setMaxResults(limit);
        
        try {
            query.setParameter("entityId", jobId);
            return query.getResultList();
        } catch (NoResultException ex) {
            return new ArrayList<History>(0);
        }
    }

    /**
     * Finds all history records for the given job and job status.
     *
     * @param   em         Entity manager. Cannot be null.
     * @param   jobId      The job for which the history will be returned. Cannot be null.
     * @param   limit      The number of results to return.
     * @param   jobStatus  The status of the job. Cannot be null.
     *
     * @return  History belongs to given job.
     */
    public static List<History> findHistoryByJobAndStatus(EntityManager em, BigInteger jobId, int limit, JobStatus jobStatus) {
        requireArgument(em != null, "Entity manager cannot be null.");
        requireArgument(jobId != null, "The jobId cannot be null.");
        requireArgument(limit > 0, "Limit must be a positive integer.");

        TypedQuery<History> query = em.createNamedQuery("History.findByJobAndStatus", History.class);
        query.setMaxResults(limit);
        
        try {
            query.setParameter("entityId", jobId);
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

        query.setParameter("expiryTime", System.currentTimeMillis() - (2 * MILLIS_PER_WEEK));
        return query.executeUpdate();
    }

    //~ Methods **************************************************************************************************************************************

	@Override
	public BigInteger getId() {
		return id;
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
     * Returns the job history record creation time.
     *
     * @return  The timestamp when job history record was created
     */
    public Long getCreationTime() {
        return creationTime;
    }
    
    /**
     * Returns the job history record creation time.
     *
     * @param creationTime  Record creation time
     */
    public void setCreationTime(Long creationTime) {
    	requireArgument(creationTime != null, "Creation Time cannot be null.");
        this.creationTime = creationTime;
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
    public BigInteger getEntityId() {
        return entityId;
    }

    /**
     * Sets the JPA entity.
     *
     * @param entityId  The entity ID
     */
    public void setEntityId(BigInteger entityId) {
        this.entityId = entityId;
    }

    public void appendMessageNUpdateHistory(String message, JobStatus jobStatus, long executionTime) {
        String oldMessage = getMessage();
        setMessage(oldMessage + addDateToMessage(message));
        if(jobStatus != null) {
            setJobStatus(jobStatus);
        }
        setExecutionTime(executionTime);
    }

    @Override
    public String toString() {
        return "History{" + "creationTime=" + getCreationTime() + ", message= Too large to display here, hostName=" +
            getHostName() + ", jobStatus=" + getJobStatus() + ", jobId=" + getEntityId() + "}";
    }
    
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((creationTime == null) ? 0 : creationTime.hashCode());
		result = prime * result
				+ ((entityId == null) ? 0 : entityId.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		History other = (History) obj;
		if (creationTime == null) {
			if (other.creationTime != null)
				return false;
		} else if (!creationTime.equals(other.creationTime))
			return false;
		if (entityId == null) {
			if (other.entityId != null)
				return false;
		} else if (!entityId.equals(other.entityId))
			return false;
		return true;
	}

    public static String addDateToMessage(String message) {
        return MessageFormat.format("\n {0} : {1}", DATE_FORMATTER.get().format(new Date()), message);
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
        SKIPPED("Job skipped."),
        SUCCESS("Job successfully completed."),
        FAILURE("Job failed."),
        ERROR("Exception occurred."),
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
