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

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.Identifiable;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.math.BigInteger;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.Root;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Provides basic methods to create, read, update and delete JPA entities.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public abstract class DefaultJPAService extends DefaultService {

	//~ Instance fields ******************************************************************************************************************************

	protected final AuditService _auditService;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultJPAService object.
	 *
	 * @param  auditService  The audit service. Can be null if audits are not required.
	 * @param config The system configuration.  Cannot be null.
	 */
	@Inject
	protected DefaultJPAService(AuditService auditService, SystemConfiguration config) {
		super(config);
		_auditService = auditService;
	}

	//~ Methods **************************************************************************************************************************************

	/**
	 * Persists an entity to the database.
	 *
	 * @param   <E>     The entity type.
	 * @param   em      The entity manager to use. Cannot be null.
	 * @param   entity  The entity to persist. Cannot be null.
	 *
	 * @return  The persisted entity having all updates applied.
	 */
	public <E extends Identifiable> E mergeEntity(EntityManager em, E entity) {
		requireArgument(em != null, "The entity manager cannot be null.");
		requireArgument(entity != null, "The entity cannot be null.");
		E ret = em.merge(entity);
		return ret;
	}

	/**
	 * Removes an entity from the database.
	 *
	 * @param  <E>     The entity type.
	 * @param  em      The entity manager to use. Cannot be null.
	 * @param  entity  The entity to remove. Cannot be null.
	 */
	protected <E extends Identifiable> void deleteEntity(EntityManager em, E entity) {
		requireArgument(em != null, "The entity manager cannot be null.");
		requireArgument(entity != null, "The entity cannot be null.");
		if (!em.contains(entity)) {
			Identifiable attached = findEntity(em, entity.getId(), entity.getClass());
			em.remove(attached);
		} else {
			em.remove(entity);
		}
	}

	protected <E extends Identifiable, T> void _deleteGlobalRecordsForType(E entity, EntityManager em, Class<T> clazz) {
		if(JPAEntity.class.isAssignableFrom(entity.getClass())) {
			JPAEntity jpaEntity = JPAEntity.class.cast(entity);
			CriteriaBuilder cb = em.getCriteriaBuilder();
			CriteriaDelete<T> update = cb.createCriteriaDelete(clazz);
			Root<T> from = update.from(clazz);

			update.where(cb.equal(from.get("entity"), jpaEntity));
			em.createQuery(update).executeUpdate();
		}else {
			throw new IllegalArgumentException("Entity - " + entity.getClass() + " is not of type JPAEntity");
		}
	}

	/**
	 * Locates an entity based on it's primary key value.
	 *
	 * @param   <E>   The type of the entity.
	 * @param   em    The entity manager to use. Cannot be null.
	 * @param   id    The primary key of the entity. Cannot be null and must be a positive non-zero number.
	 * @param   type  The runtime type of the entity. Cannot be null.
	 *
	 * @return  The entity or null if no entity exists for the primary key.
	 */
	protected <E extends Identifiable> E findEntity(EntityManager em, BigInteger id, Class<E> type) {
		requireArgument(em != null, "The entity manager cannot be null.");
		requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be positive and non-zero");
		requireArgument(type != null, "The entity cannot be null.");
		em.getEntityManagerFactory().getCache().evictAll();
		return em.find(type, id);
	}

	/**
	 * Returns a list of entities of the given type that are marked for deletion, but have not yet been physically deleted.
	 *
	 * @param   <E>   	The entity type.
	 * @param   em    	The entity manager to use.  Cannot be null.
	 * @param   type  	The runtime type of the values to return.
	 * @param	limit	The number of entities to find. If -1, finds all such entities.
	 *
	 * @return  The list of entities marked for deletion.  Will never return null, but may be empty.
	 */
	protected <E extends Identifiable> List<E> findEntitiesMarkedForDeletion(EntityManager em, Class<E> type, final int limit) {
		requireArgument(em != null, "The entity manager cannot be null.");
		requireArgument(type != null, "The entity cannot be null.");
		requireArgument(limit == -1 || limit > 0, "Limit if not -1, must be greater than 0.");

		em.getEntityManagerFactory().getCache().evictAll();
		return JPAEntity.findEntitiesMarkedForDeletion(em, type, limit);
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
