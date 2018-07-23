package com.salesforce.dva.argus.service.jpa;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.util.List;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Chart;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.ChartService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * Default implementation of the <tt>ChartService</tt> interface.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class DefaultChartService extends DefaultJPAService implements ChartService {

	private Logger _logger = LoggerFactory.getLogger(DefaultChartService.class);
	@Inject
    Provider<EntityManager> emf;
	
	@Inject
	public DefaultChartService(AuditService auditService, SystemConfiguration config) {
		super(auditService, config);
	}

	@Override
	@Transactional
	public Chart updateChart(Chart chart) {
		requireNotDisposed();
        requireArgument(chart != null, "Cannot update a null chart");

        EntityManager em = emf.get();
        Chart result = mergeEntity(em, chart);

        em.flush();
        _logger.debug("Updated chart to : {}", result);
        _auditService.createAudit("Updated chart : {0}", result, result);
        return result;
	}
	
	@Override
    @Transactional
    public void deleteChart(BigInteger id) {
        Chart chart = getChartByPrimaryKey(id);
		deleteChart(chart);
    }

	@Override
	@Transactional
	public void deleteChart(Chart chart) {
		requireNotDisposed();
        requireArgument(chart != null, "Cannot delete a null chart.");
        _logger.debug("Deleting chart {}.", chart);

        EntityManager em = emf.get();

        deleteEntity(em, chart);
        em.flush();
	}
	
	@Override
	@Transactional
	public void markChartForDeletion(Chart chart) {
		requireNotDisposed();
        requireArgument(chart != null, "Cannot delete a null chart.");
        _logger.debug("Marking chart for deletion {}.", chart);

        EntityManager em = emf.get();

		chart.setDeleted(true);

		Chart result = mergeEntity(em, chart);

		em.flush();
		_logger.debug("Set delete marker for chart : {}", result);
		_auditService.createAudit("Set delete marker for chart : {0}", result, result);
	}
	
	@Override
	@Transactional
	public Chart getChartByPrimaryKey(BigInteger id) {
		requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "Chart ID must be greater than zero.");

        Chart result = findEntity(emf.get(), id, Chart.class);

        _logger.debug("Query for chart having id {} resulted in : {}", id, result);
        return result;
	}

	@Override
	@Transactional
	public List<Chart> getChartsByOwner(PrincipalUser owner) {
		requireNotDisposed();
        requireArgument(owner != null, "Owner cannot be null");
        
        return Chart.getChartsByOwner(emf.get(), owner);
	}

	@Override
	@Transactional
	public List<Chart> getChartsForEntity(BigInteger entityId) {
		requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Associated entity id for this chart cannot be null "
        		+ "and must be greater than zero.");
        
        return Chart.getChartsForEntity(emf.get(), entityId);
	}
	
	@Override
	@Transactional
	public List<Chart> getChartsByOwnerForEntity(PrincipalUser owner, BigInteger entityId) {
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null");
		requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Associated entity id for this chart cannot be null "
        		+ "and must be greater than zero.");
        
        //return Chart.getChartsForEntity(emf.get(), entityId);
		return Chart.getChartsByOwnerForEntity(emf.get(), owner, entityId);
	}
	
	@Override
	@Transactional
	public JPAEntity getAssociatedEntity(BigInteger entityId) {
		requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Associated entity id for this chart cannot be null "
        		+ "and must be greater than zero.");
        
        return findEntity(emf.get(), entityId, JPAEntity.class);
	}

}
