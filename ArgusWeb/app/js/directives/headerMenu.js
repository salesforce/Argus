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

      var _absUrl = $location.absUrl(),
        _alertUrl;

      // set routes
      if ( _absUrl.lastIndexOf(':') !== -1 ) {
        _alertUrl = _absUrl.substr(0, _absUrl.lastIndexOf(':'));

        if ( _absUrl.lastIndexOf('localhost') !== -1 ) {
          // LOCALHOST.
          _alertUrl = _alertUrl + ':8000/#/alerts';
        } else {
          // QA
          _alertUrl = _alertUrl + ':8080/argusmvp/index.html#/alerts';
        }
      } else {
        // PROD
        _alertUrl = _absUrl.replace("argus", "argusmvp");
      }

      // set new alert url into scope
      $scope.alertUrl = _alertUrl;

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
