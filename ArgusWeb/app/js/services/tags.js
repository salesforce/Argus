angular.module('argus.services.tags', [])
.service('Tags', ['CONFIG', '$http', '$q', function(CONFIG, $http, $q) {
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
