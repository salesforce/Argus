/*global angular:false */

angular.module('argus.services.storage', [])
.factory('Storage', ['$rootScope', '$localStorage', '$sessionStorage', function ($rootScope, $localStorage, $sessionStorage) {
	$rootScope.storage = $localStorage;
	return {
		set: function (key, value) {
			$rootScope.storage[key] = value;
		},
		get: function (key) {
			var result = $rootScope.storage[key];
			return angular.isDefined(result) ? result : null;
		},
		clear: function (key) {
			delete $rootScope.storage[key];
		},
		reset: function () {
			//delete user info, but preserve the storage of preferences
			delete $rootScope.storage['user'];
			delete $rootScope.storage['target'];
			$sessionStorage.$reset();
		},
		resetAll: function() {
			$rootScope.storage.$reset();
			$sessionStorage.$reset();
		}
	};
}]);