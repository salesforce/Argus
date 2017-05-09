'use strict';
/*global angular:false */

angular.module('argus.controllers.main', [])
.controller('Main', ['$scope', '$location', 'Auth', '$sessionStorage', function ($scope, $location, Auth, $sessionStorage) {

	$scope.login = function (username, password) {
		if(username.indexOf('@') != -1) {
			username = username.substring(0, username.indexOf('@'));
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

	// delete session cache when the entire page is reloaded i.e. refresh the tab
	$sessionStorage.$reset();
}]);