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
package com.salesforce.dva.argus.service.jpa;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import javax.persistence.EntityManager;
import javax.persistence.OptimisticLockException;

import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.DistributedSchedulingLock;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DistributedSchedulingLockService;
import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import com.salesforce.dva.argus.system.SystemConfiguration;

public class DefaultDistributedSchedulingLockService extends DefaultJPAService implements DistributedSchedulingLockService{
	/**
	 * Default implementation of Distributed Scheduling Lock Service
	 * 
	 * @author Raj Sarkapally rsarkapally@salesforce.com
	 */

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	@Inject
	Provider<EntityManager> emf;
	AlertService _alertService;

	//~ Constructors *********************************************************************************************************************************

	@Inject
	protected DefaultDistributedSchedulingLockService(AuditService auditService, SystemConfiguration config, AlertService alertService) {
		super(auditService, config);
		_alertService=alertService;
	}

	//~ Methods **************************************************************************************************************************************


	@Override
	public DistributedSchedulingLock updateNGetDistributedScheduleByType(LockType type,int jobsBlockSize, long schedulingRefreshInterval) {
		requireNotDisposed();
		requireArgument(type != null, "Lock type cannot be null.");

		EntityManager em = emf.get();
		while(true){
			try{
				DistributedSchedulingLock distributedSchedulingLock = DistributedSchedulingLock.updateNGetDistributedScheduleByType(em, type, jobsBlockSize,  schedulingRefreshInterval);
				return distributedSchedulingLock;
			}catch(OptimisticLockException ex){
				_logger.info("Optimistic lock exception " + ex.toString());  
			}catch(Throwable th){
				_logger.error("Optimistic lock exception " + th.toString());   
			}
		}

	}
}
