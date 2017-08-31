/*global angular:false */

angular.module('argus.services.search', [])
.service('SearchService', ['$http', 'CONFIG', function($http, CONFIG) {
	this.search = function(searchParams) {
		if (!searchParams) return;

		// TODO: refactor api call to a separate factory for metric queries
		var request = $http({
			method: 'GET',
			url: CONFIG.wsUrl + 'discover/metrics/schemarecords',
			params: searchParams,
			timeout: 30000
		});

		return request;
	};

	this.processResponse = function(response) {
		return response.data;
	};
}]);
