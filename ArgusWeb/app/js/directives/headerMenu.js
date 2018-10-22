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
      if ( !_absUrl.lastIndexOf('8080') ) {
        // PROD or WIP servers
        _alertUrl = _absUrl.replace("argus", "argusmvp");
      } else {
        if ( _absUrl.lastIndexOf(':') !== -1 ) {
          _alertUrl = _absUrl.substr(0, _absUrl.lastIndexOf(':'));
          if ( _absUrl.lastIndexOf('localhost') !== -1 ) {
            // LOCALHOST (dev)
            _alertUrl = _alertUrl + ':8000/#/alerts';
          } else {
            // DataViz QA or Argus QA (non-wip)
            _alertUrl = _alertUrl + ':8080/argusmvp/index.html#/alerts';
          }
        }
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
