angular.module('argus.directives.headerMenu', [])
.directive('headerMenu', ['Auth', function (Auth) {
    "use strict";
    return {
        restrict: 'E',
        templateUrl: 'js/templates/headerMenu.html',
        scope: {},
        controller: ['$rootScope', '$scope', function ($rootScope, $scope) {

            $rootScope.$on('$routeChangeSuccess', function (event, current, previous) {
                $scope.activeTab = (current.$$route) ? current.$$route.activeTab : '';
            });

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
        link: function(scope, element, attribute) {
        }
    }
}]);