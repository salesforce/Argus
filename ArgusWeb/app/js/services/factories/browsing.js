/*global angular:false */
'use strict';

angular.module('argus.services.browsing', [])
.factory('Browsing', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	return $resource(CONFIG.wsUrl + 'discover/metrics/browsing', {}, {
		query: {method: 'GET', isArray: true}
	});
}]);
