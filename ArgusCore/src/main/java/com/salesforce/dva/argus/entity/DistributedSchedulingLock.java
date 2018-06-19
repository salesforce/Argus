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

import java.util.Objects;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.EntityTransaction;
import javax.persistence.Id;
import javax.persistence.OptimisticLockException;
import javax.persistence.Table;
import javax.persistence.Version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import com.salesforce.dva.argus.service.alert.AlertDefinitionsCache;

/**
 * DistributedSchedulingLock object uses database record to distribute the jobs across schedulers. 
 * 
 * @author Raj Sarkapally rsarkapally@salesforce.com
 *
 */
@Entity
@Table(name = "DISTRIBUTED_SCHEDULING_LOCK")

public class DistributedSchedulingLock { 

	//~ Instance fields ******************************************************************************************************************************

	@Id
	@Column(name = "id", nullable = false)
	private Long id;

	@Basic(optional = false)
	@Column(name = "job_count", nullable = false)
	private Long jobCount;
	@Basic(optional = false)
	@Column(name = "current_index", nullable = false)
	private int currentIndex;
	@Basic(optional = false)
	@Column(name = "current_schedule_end_time", nullable = false)
	private Long nextScheduleStartTime;

	@Version
	@Column(name = "VERSION")
	private Integer version;

	private static Logger _logger =  LoggerFactory.getLogger(DistributedSchedulingLock.class);

	//~ Constructors *********************************************************************************************************************************

	/** To be used by the persistence infrastructure only. */
	protected DistributedSchedulingLock(){
	}

	/**
	 * Constructor used to construct the specific lock types.
	 *
	 * @param  type  The type of the lock specified as a number. Must be greater than zero.
	 * @param  note  The note to associate with the lock.
	 */
	public DistributedSchedulingLock(long type){
		this.id=type;
	}

	//~ Methods **************************************************************************************************************************************

	/**
	 * Obtains a distributed schedule object of a given type.
	 *
	 * @param   em          The entity manager to use. Cannot be null.
	 * @param   type        The scheduling type represented as a long value.
	 * @return  The distributed schedule object of a given type.
	 */
	public static DistributedSchedulingLock getDistributedScheduleByType(EntityManager em, long type) {
		requireArgument(em != null, "Entity manager can not be null.");
		try {
			return em.find(DistributedSchedulingLock.class, type);
		} catch (EntityNotFoundException ex) {
			return null;
		}
	}

	/**
	 * Obtains a distributed schedule object of a given type.
	 *
	 * @param   em          The entity manager to use. Cannot be null.
	 * @param   type        The scheduling type represented as a long value.
	 * @param   jobsBlockSize   The no of jobs each scheduler schedules.
	 * @param   schedulingRefreshInterval The time in millis scheduler refresh jobs
	 * @return  The distributed schedule object.
	 */
	public static DistributedSchedulingLock updateNGetDistributedScheduleByType(EntityManager em, LockType type, int jobsBlockSize, long schedulingRefreshInterval) throws OptimisticLockException {
		EntityTransaction tx = null;

		long id=type.ordinal()+1;
		try {
			tx = em.getTransaction();
			tx.begin();

			DistributedSchedulingLock distributedSchedulingLock = getDistributedScheduleByType(em, id);
			if(distributedSchedulingLock == null){
				distributedSchedulingLock = new DistributedSchedulingLock(id);
				distributedSchedulingLock.setCurrentIndex(jobsBlockSize);
				distributedSchedulingLock.setNextScheduleStartTime(_toBeginOfMinute(System.currentTimeMillis()+schedulingRefreshInterval)); 
				_logger.info("Setting the first schedule start time to {} , refresh interval - {}", distributedSchedulingLock.getNextScheduleStartTime(), schedulingRefreshInterval);
				distributedSchedulingLock.setJobCount(getTotalEnabledJobCount(em, distributedSchedulingLock.getNextScheduleStartTime() - schedulingRefreshInterval, type)); 
				distributedSchedulingLock = em.merge(distributedSchedulingLock);
				em.flush();
			}else if(System.currentTimeMillis() >= distributedSchedulingLock.getNextScheduleStartTime()){
				distributedSchedulingLock.setCurrentIndex(jobsBlockSize);
				distributedSchedulingLock.setJobCount(getTotalEnabledJobCount(em, distributedSchedulingLock.getNextScheduleStartTime(), type)); 
				distributedSchedulingLock.setNextScheduleStartTime(_toBeginOfMinute(System.currentTimeMillis()+schedulingRefreshInterval));
				_logger.info("Setting the next schedule start time to {} , refresh interval - {}", distributedSchedulingLock.getNextScheduleStartTime(), schedulingRefreshInterval);
				distributedSchedulingLock = em.merge(distributedSchedulingLock);
				em.flush();
			}else{
				if((distributedSchedulingLock.getCurrentIndex()-jobsBlockSize) < distributedSchedulingLock.getJobCount()){
					distributedSchedulingLock.setCurrentIndex(distributedSchedulingLock.getCurrentIndex() + jobsBlockSize); 
					_logger.info("Setting current index to {} , refresh interval - {}", distributedSchedulingLock.getCurrentIndex(), schedulingRefreshInterval);
					distributedSchedulingLock = em.merge(distributedSchedulingLock);	
					em.flush();
				}
			}

			tx.commit();
			return distributedSchedulingLock;

		} catch (OptimisticLockException ex) {
			if (tx != null && tx.isActive()) {
				tx.rollback();
			}
			throw ex;
		}

	}

	private static long getTotalEnabledJobCount(EntityManager em, long schedulingStartTimeMillis, LockType type){
		switch(type){
		case ALERT_SCHEDULING:
			return AlertDefinitionsCache.getEnabledAlertsForMinute(schedulingStartTimeMillis).size();
		default:
			return 0;
		}
	}

	private static long _toBeginOfMinute(long millis){
		return millis-(millis % (60*1000));
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getJobCount() {
		return jobCount;
	}

	public void setJobCount(Long jobCount) {
		this.jobCount = jobCount;
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public void setCurrentIndex(int currentIndex) {
		this.currentIndex = currentIndex;
	}

	public Long getNextScheduleStartTime() {
		return nextScheduleStartTime;
	}

	public void setNextScheduleStartTime(Long nextScheduleStartTime) {
		this.nextScheduleStartTime = nextScheduleStartTime;
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

		final DistributedSchedulingLock other = DistributedSchedulingLock.class.cast(obj);

		if (!Objects.equals(this.id, other.id)) {
			return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "DistributedSchedulingLock{" + "id=" + id + ", jobCount=" + jobCount + ", currentIndex=" + currentIndex + ", nextScheduleStartTime=" + nextScheduleStartTime + '}';
	}
}
