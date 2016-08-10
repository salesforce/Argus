angular.module('argus.controllers.main', [])
.controller('Main', ['$rootScope', '$scope', '$location', 'Auth', function ($rootScope, $scope, $location, Auth) {

    $rootScope.$on('$routeChangeSuccess', function (event, current, previous) {
        $scope.activeTab = (current.$$route) ? current.$$route.activeTab : '';
    });

    $scope.login = function (username, password) {
    	if(username.indexOf("@") != -1) {
            username = username.substring(0, username.indexOf("@"));
        }
        Auth.login(username, password);
    };

    $scope.logout = function () {
        Auth.logout();
    };

    $scope.getRemoteUser = function () {
        var user = Auth.remoteUser();
        if (user) {
            return user.userName;
        } else {
            return null;
        }
    };

    $scope.isLoggedIn = function () {
        return Auth.isLoggedIn();
    };

    $scope.isPrivileged = function () {
        return Auth.isPrivileged();
    };
}]);