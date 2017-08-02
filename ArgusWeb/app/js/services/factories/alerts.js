/*global angular:false */
'use strict';

angular.module('argus.services.alerts', [])
.factory('Alerts', ['$resource', 'CONFIG',
	function ($resource, CONFIG) {
		return $resource(CONFIG.wsUrl + 'alerts/:alertId', {}, {
			query: {method: 'GET', params: {alertId: ''}, isArray: true},
			update: {method: 'PUT'},
			getMeta: {method: 'GET', url: CONFIG.wsUrl + 'alerts/meta?shared=true', isArray: true},
			getNonSharedAlerts: {method: 'GET', url: CONFIG.wsUrl + 'alerts/meta?shared=false', isArray: true}
		});
	}]);
