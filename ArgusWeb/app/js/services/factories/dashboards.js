/*global angular:false */
'use strict';

angular.module('argus.services.dashboards', [])
.factory('Dashboards', ['$resource', 'CONFIG', 'Auth', function ($resource, CONFIG, Auth) {
	return $resource(CONFIG.wsUrl + 'dashboards/:dashboardId', {}, {
		query: {method: 'GET', params: {dashboardId: ''}, isArray: true},
		update: {method: 'PUT'},
		getMeta: {method: 'GET', url: CONFIG.wsUrl + 'dashboards/meta', isArray: true},
		getPersonalDashboards: {method: 'GET', url: CONFIG.wsUrl + 'dashboards/meta?owner=' + Auth.getUsername(), isArray: true}
	});
}]);
