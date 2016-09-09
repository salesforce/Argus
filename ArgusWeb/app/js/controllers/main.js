angular.module('argus.controllers.main', [])
.controller('Main', ['$scope', '$location', 'Auth', function ($scope, $location, Auth) {

    $scope.login = function (username, password) {
    	if(username.indexOf("@") != -1) {
            username = username.substring(0, username.indexOf("@"));
        }
        Auth.login(username, password);
    };

    $scope.currentUser = function () {
        return Auth.getUsername();
    };

    $scope.isLoggedIn = function () {
        return Auth.isLoggedIn();
    };

    $scope.isPrivileged = function () {
        return Auth.isPrivileged();
    };
}]);