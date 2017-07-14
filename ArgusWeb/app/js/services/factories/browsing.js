/*global angular:false */
'use strict';

angular.module('argus.services.browsing', [])
.factory('Browsing', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	//TODO: if a different WS URL is used for metric browsing, change it here
	return $resource(CONFIG.wsUrl + 'discover/metrics/browsing', {}, {
		query: {method: 'GET', isArray: true}
	});
}]);
