/*global angular:false */
'use strict';

angular.module('argus.services.browsing', [])
.factory('Browsing', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	//set metric browsing url to beta server
	var metricBrowsingUrl = CONFIG.wsUrl + 'discover/metrics/browsing';
	return $resource(metricBrowsingUrl, {}, {
		query: {method: 'GET', isArray: true}
	});
}]);
