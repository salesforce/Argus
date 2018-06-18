package com.salesforce.dva.argus.service;

import java.math.BigInteger;
import java.util.List;

import com.salesforce.dva.argus.entity.Chart;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.PrincipalUser;

/**
 * Provides methods to create, update, read and delete chart entities.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public interface ChartService extends Service {
	
	//~ Methods **************************************************************************************************************************************
	
	/**
     * Updates a chart, creating it if necessary.
     *
     * @param   chart  The chart to update. Cannot be null.
     *
     * @return  The updated chart. Will never return null.
     */
	Chart updateChart(Chart chart);
	
	/**
     * Deletes a chart given its primary key ID.
     *
     * @param  id  The primary key ID. Cannot be null and must be a positive non-zero number.
     */
	void deleteChart(BigInteger id);
	
	/**
     * Deletes a chart.
     *
     * @param  chart  The chart to delete. Cannot be null.
     */
	void deleteChart(Chart chart);
	
	/**
     * Marks a chart for deletion.
     *
     * @param  chart  The chart to delete. Cannot be null.
     */
	void markChartForDeletion(Chart chart);
	
	/**
     * Retrieves a chart based on the primary key ID.
     *
     * @param   id	The primary key ID. Cannot be null and must be a positive non-zero number.
     *
     * @return  The chart or null if no chart exists for the given primary key ID.
     */
	Chart getChartByPrimaryKey(BigInteger id);
	
	/**
     * Retrieves a list of charts owned by a user.
     *
     * @param   owner	The user for which to retrieve charts. Cannot be null.
     *
     * @return  A list of charts owned by this user or an empty list if none are owned.
     */
	List<Chart> getChartsByOwner(PrincipalUser owner);
	
	/**
     * Retrieves a list of charts associated with a given entity.
     *
     * @param   entityId	The associated entityId for which to retrieve charts. Cannot be null.
     *
     * @return  A list of charts associated with the given entity or 
     * 			an empty list if there are no charts associated with this entity.
     */
	List<Chart> getChartsForEntity(BigInteger entityId);
	
	/**
     * Retrieves a list of charts owned by a user and associated with a given entity.
     *
     * @param   owner		The user for which to retrieve charts. Cannot be null.
     * @param   entityId	The associated entityId for which to retrieve charts. Cannot be null.
     *
     * @return  A list of charts associated with the given entity or 
     * 			an empty list if there are no charts associated with this entity.
     */
	List<Chart> getChartsByOwnerForEntity(PrincipalUser owner, BigInteger entityId);

	/**
     * Retrieves the associated entity based on the primary key ID.
     *
     * @param   entityId	The primary key ID. Cannot be null and must be a positive non-zero number.
     *
     * @return  The entity or null if no entity exists for the given primary key ID.
     */
	JPAEntity getAssociatedEntity(BigInteger entityId);

}
