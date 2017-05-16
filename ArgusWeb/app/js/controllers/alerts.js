/*! Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 *      Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 *      Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 *      Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
'use strict';
/*global angular:false */

angular.module('argus.controllers.alerts', ['ngResource'])
.controller('Alerts', ['Auth', '$scope', 'growl', 'Alerts', '$sessionStorage', 'TableListService', function (Auth, $scope, growl, Alerts, $sessionStorage, TableListService) {

	$scope.colName = {
		id:'ID',
		name:'Name',
		cronEntry:'CRON Entry',
		createdDate:'Created',
		modifiedDate:'Last Modified',
		ownerName:'Owner',
		state: 'State'
	};
	$scope.properties = {
		title: 'Alert',
		type: 'alerts'
	};
	$scope.tabNames = {
		userPrivileged: Auth.isPrivileged(),
		firstTab: Auth.getUsername() + '\'s Alerts',
		secondTab: 'Shared Alerts',
		thirdTab: 'All Other Alerts'
	};
	$scope.alerts = [];
	$scope.alertsLoaded = false;

	var alertLists = {
		sharedList: [],
		usersList: [],
		privilegedList: []
	};
	var remoteUsername = Auth.getUsername();
	var userPrivileged = Auth.isPrivileged();

	$scope.getAlerts = function (selectedTab) {
		if ($scope.alertsLoaded) {
			// when only user's alerts are loaded but shared tab is chosen: need to start a new API call
			if (selectedTab === 2 && !$sessionStorage.alerts.loadedEverything) {
				delete $scope.alerts;
				$scope.alertsLoaded = false;
				getAllAlerts();
			} else {
				switch (selectedTab) {
					case 2:
						$scope.alerts = alertLists.sharedList;
						break;
					case 3:
						if (userPrivileged) {
							$scope.alerts = alertLists.privilegedList;
							break;
						}
						break;
					default:
						$scope.alerts = alertLists.usersList;
				}
			}
		}
	};

	function setAlertsAfterLoading (selectedTab) {
		$scope.alertsLoaded = true;
		$scope.getAlerts(selectedTab);
	}

	function getAllAlerts () {
		Alerts.getMeta().$promise.then(function(alerts) {
			alertLists = TableListService.getListUnderTab(alerts, remoteUsername, userPrivileged);
			$sessionStorage.alerts.cachedData = alertLists;
			$sessionStorage.alerts.loadedEverything = true;
			setAlertsAfterLoading($scope.selectedTab);
		});
	}

	function getUsersAlerts () {
		Alerts.getUsers().$promise.then(function(alerts) {
			alertLists = TableListService.getListUnderTab(alerts, remoteUsername, userPrivileged);
			$sessionStorage.alerts.cachedData = alertLists;
			$sessionStorage.alerts.loadedEverything = false;
			setAlertsAfterLoading(1);
		});
	}

	function updateList () {
		$sessionStorage.alerts.cachedData = alertLists;
		$scope.getAlerts($scope.selectedTab);
	}

	$scope.refreshAlerts = function () {
		delete $sessionStorage.alerts.cachedData;
		delete $scope.alerts;
		$scope.alertsLoaded = false;
		$scope.selectedTab === 2? getAllAlerts(): getUsersAlerts();
	};

	$scope.addAlert = function () {
		var alert = {
			name: 'new-alert-' + Date.now(),
			expression: '-1h:scope:metric{tagKey=tagValue}:avg',
			cronEntry: '0 */4 * * *',
			shared: $scope.selectedTab === 2
		};
		Alerts.save(alert, function (result) {
			// update both scope and session alerts
			result.expression = '';
			alertLists = TableListService.addItemToTableList(alertLists, 'alerts', result, remoteUsername, userPrivileged);
			updateList();
			growl.success('Created "' + alert.name + '"');
		}, function () {
			growl.error('Failed to create "' + alert.name + '"');
		});
	};

	$scope.removeAlert = function (alert) {
		Alerts.delete({alertId: alert.id}, function () {
			alertLists = TableListService.deleteItemFromTableList(alertLists, 'alerts', alert, remoteUsername, userPrivileged);
			updateList();
			growl.success('Deleted "' + alert.name + '"');
		}, function () {
			growl.error('Failed to delete "' + alert.name + '"');
		});
	};

	$scope.enableAlert = function (alert, enabled) {
		if (alert.enabled !== enabled) {
			Alerts.get({alertId: alert.id}, function(updated) {
				updated.enabled = enabled;
				Alerts.update({alertId: alert.id}, updated, function () {
					alert.enabled = enabled;
					updateList();
					growl.success((enabled ? 'Enabled "' : 'Disabled "') + alert.name + '"');
				}, function () {
					growl.error('Failed to ' + (enabled ? 'enable "' : 'disable "') + alert.name + '"');
				});
			});
		}
	};

	if ($sessionStorage.alerts === undefined) $sessionStorage.alerts = {};
	if ($sessionStorage.alerts.cachedData !== undefined && $sessionStorage.alerts.selectedTab !== undefined) {
		alertLists = $sessionStorage.alerts.cachedData;
		$scope.selectedTab = $sessionStorage.alerts.selectedTab;
		setAlertsAfterLoading($scope.selectedTab);
	} else {
		$scope.selectedTab === 2? getAllAlerts(): getUsersAlerts();
	}


}]);
