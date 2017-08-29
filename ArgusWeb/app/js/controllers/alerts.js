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
.controller('Alerts', ['Auth', '$scope', 'growl', 'Alerts', 'TableListService', 'Storage', function (Auth, $scope, growl, Alerts, TableListService, Storage) {

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
		usersList: [],
		sharedList: [],
		privilegedList: []
	};
	var remoteUsername = Auth.getUsername();
	var userPrivileged = Auth.isPrivileged();
	var sessionStoredList;

	$scope.getAlerts = function (selectedTab) {
		if ($scope.alertsLoaded) {
			// when only user's alerts are loaded but shared tab is chosen: need to start a new API call
			if (selectedTab === 2 && !sessionStoredList.loadedEverything) {
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

	function updateAlertListInSessionStorage (alertLists) {
		if (Storage.roughSizeOfObject(alertLists) > 2000000) { // limit list size to be 2MB
			Storage.compressData(alertLists).then(function(compressedData) {
				sessionStoredList.cachedCompressedData = compressedData;
			});
		} else {
			sessionStoredList.cachedData = alertLists;
		}
	}

	function setAlertsAfterLoading (selectedTab) {
		$scope.alertsLoaded = true;
		$scope.getAlerts(selectedTab);
	}

	function getAllAlerts () {
		Alerts.getMeta().$promise.then(function(alerts) {
			alertLists = TableListService.getListUnderTab(alerts, remoteUsername, userPrivileged);
			setAlertsAfterLoading($scope.selectedTab);
			sessionStoredList.loadedEverything = true;
			updateAlertListInSessionStorage(alertLists);
		});
	}

	function getUsersAlerts () {
		Alerts.getNonSharedAlerts().$promise.then(function(alerts) {
			alertLists = TableListService.getListUnderTab(alerts, remoteUsername, userPrivileged);
			setAlertsAfterLoading(1);
			sessionStoredList.loadedEverything = false;
			updateAlertListInSessionStorage(alertLists);
		});
	}

	function updateList () {
		$scope.getAlerts($scope.selectedTab);
		if (sessionStoredList.cachedCompressedData !== '') {
			// dont update session storage if list is compressed; if the user leaves this page, just re query everything again
			sessionStoredList.emptyData = true;
		} else {
			sessionStoredList.cachedData = alertLists;
		}
	}

	$scope.refreshAlerts = function () {
		sessionStoredList.cachedData = {};
		sessionStoredList.cachedCompressedData = '';
		delete $scope.alerts;
		$scope.alertsLoaded = false;
		if ($scope.selectedTab === 2) {
			getAllAlerts();
		} else {
			getUsersAlerts();
		}
	};

	$scope.addAlert = function () {
		var alert = {
			name: 'new-alert-' + Date.now(),
			expression: '-1h:scope:metric{tagKey=tagValue}:avg',
			cronEntry: '0 */4 * * *',
			shared: $scope.selectedTab === 2
		};
		growl.info('Creating "' + alert.name + '"...');
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
		growl.info('Deleting "' + alert.name + '"...');
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
			growl.info('Updating "' + alert.name + '"...');
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

	// see if there is anything in the session storage
	sessionStoredList = Storage.getSessionList('alerts');
	if (sessionStoredList === undefined) {
		Storage.initializeSessionList('alerts');
		sessionStoredList = Storage.getSessionList('alerts');
	}
	// set up view based on data in session storage or query data
	if (sessionStoredList.emptyData || sessionStoredList.selectedTab === undefined) {
		if ($scope.selectedTab === 2) {
			getAllAlerts();
		} else {
			getUsersAlerts();
		}
		sessionStoredList.emptyData = false;
	} else {
		if (sessionStoredList.cachedCompressedData !== '') {
			Storage.decompressData(sessionStoredList.cachedCompressedData).then(function(decompressedData) {
				alertLists = decompressedData;
				setAlertsAfterLoading($scope.selectedTab);
			});
		} else {
			alertLists = sessionStoredList.cachedData;
			setAlertsAfterLoading($scope.selectedTab);
		}
		$scope.selectedTab = sessionStoredList.selectedTab;
	}
}]);
