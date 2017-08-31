/*global angular:false */
'use strict';

angular.module('argus.services.metrics', [])
.factory('Metrics', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	return $resource(CONFIG.wsUrl + 'metrics', {}, {
		query: {method: 'GET', isArray: true},
		downloadCSV: {
			method: 'GET',
			headers: {'Accept': 'application/ms-excel'},
			// override angular http response default transform
			transformResponse: [function(data) {
				return [data];
			}],
			isArray: true
		}
	});
}]);
