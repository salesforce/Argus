'use strict';
/*global angular:false */

angular.module('argus.directives.headerMenu', [])
.directive('headerMenu', ['Auth', function (Auth) {
	return {
		restrict: 'E',
		templateUrl: 'js/templates/headerMenu.html',
		scope: {},
		controller: ['$rootScope', '$scope', '$location', function ($rootScope, $scope, $location) {

			$rootScope.$on('$routeChangeSuccess', function (event, current) {
				$scope.activeTab = (current.$$route) ? current.$$route.activeTab : '';
			});

			var _absUrl = $location.absUrl();
			// No need to consider port, as in hybrid mode, mvp and old argus should run on the same port
      // set new alert url into scope
      $scope.alertUrl = _absUrl.replace(/\/argus\/.*$/, '/argusmvp/#/alerts');

			$scope.isLoggedIn = function () {
				return Auth.isLoggedIn();
			};

			$scope.currentUser = function () {
				return Auth.getUsername();
			};

			$scope.logout = function () {
				Auth.logout();
			};
		}],
		// link: function(scope, element, attribute) {
		// }
	};
}]);
