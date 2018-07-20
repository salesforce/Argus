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

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import java.math.BigInteger;
import java.util.List;

/**
 * Provides methods to create, update, read and delete Dashboard entities.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public interface DashboardService extends Service {

	//~ Methods **************************************************************************************************************************************

	/**
	 * Retrieves a Dashboard by its name and its owner(PrincipalUSer).
	 *
	 * @param   name   The name of the Dashboard to retrieve. Cannot be null of empty.
	 * @param   owner  The owner of the Dashboard to retrieve. Cannoy be null.
	 *
	 * @return  The Dashboard if one exists or null if no such Dashboard exists.
	 */
	Dashboard findDashboardByNameAndOwner(String name, PrincipalUser owner);

	/**
	 * Retrieves a Dashboard based on the primary key ID.
	 *
	 * @param   id  The primary key ID. Cannot be null and must be a positive non-zero number.
	 *
	 * @return  The Dashboard or null if no Dashboard exists for the give primary key ID.
	 */
	Dashboard findDashboardByPrimaryKey(BigInteger id);

	/**
	 * Retrieves a list of Dashboards owned by a user.
	 *
	 * @param   owner  The user for which to retrieve dashboards. Cannot be null.
	 * @param metadataOnly To return only the metadata or not
	 * @param   version The version of the dashboard to retrieve. It is either null or not empty
	 *
	 * @return  A list of Dashboards owner by this user or an empty list if none are owned.
	 */
	List<Dashboard> findDashboardsByOwner(PrincipalUser owner, boolean metadataOnly,String version);

	/**
	 * Returns a list of shared Dashboards.
	 * @param   metadataOnly    Get metadata only
	 * @param   owner           The owner of shared dashboards to filter on. If null no filtering applied
	 * @param   limit           The maximum number of rows to return. If null no filtering applied
     * @param   version         The version of the dashboard to retrieve. It is either null or not empty
	 * 
	 * @return  The list of all shared dashboards. Will never be null, but may be empty.
	 */
	List<Dashboard> findSharedDashboards(boolean metadataOnly, PrincipalUser owner, Integer limit, String version);

	/**
	 * Retrieves a list of all dashboards.
	 *
	 * @param   limit  The maximum number of records to return.
	 * @param metadataOnly 	Whether to return only metadata
	 * @param   version The version of the dashboard to retrieve. It is either null or not empty
	 *
	 * @return  The list of dashboards.
	 */
	List<Dashboard> findDashboards(Integer limit, boolean metadataOnly, String version);

	/**
	 * Updates a dashboard, creating it if necessary.
	 *
	 * @param   dashboard  The dashboard to update. Cannot be null.
	 *
	 * @return  The updated dashboard. Will never return null.
	 */
	Dashboard updateDashboard(Dashboard dashboard);

	/**
	 * Deletes a dashboard.
	 *
	 * @param  dashboard  The dashboard to delete. Cannot be null.
	 */
	void deleteDashboard(Dashboard dashboard);

	/**
	 * Deletes a dashboard given a name and its owner.
	 *
	 * @param  dashboardName  The name of the dashboard to delete. Cannot be null or empty.
	 * @param  owner          The owner of the dashboard to delete. Cannot be null.
	 */
	void deleteDashboard(String dashboardName, PrincipalUser owner);

	/**
	 * Deletes a dashboard given its primary key ID.
	 *
	 * @param  id  The primary key ID. Cannot be null and must be a positive non-zero number.
	 */
	void deleteDashboard(BigInteger id);

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
