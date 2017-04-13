/*global angular:false */

angular.module('argus.services.tags', [])
.service('Tags', ['CONFIG', '$http', function(CONFIG, $http) {
	this.getDropdownOptions = function(key) {
		var request = $http({
			method: 'GET',
			url: CONFIG.wsUrl + 'schema/tags',
			params: {
				tagk: key
			}
		});
		return request;
	};
}]);
