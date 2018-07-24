'use strict';
/*global angular:false, jwt_decode:false */

angular.module('argus.services.auth', [])
.factory('Auth', ['$resource', '$location', 'CONFIG', 'growl', 'Storage', 'Users', function ($resource, $location, CONFIG, growl, Storage, Users) {

	var refreshPath = 'v2/auth/token/refresh';

	return {
		isUrlValid: function isUrlValid(url){
			if(!url) return false;
			url = url.trim();
			if(url.indexOf('/') === 0) return true;
			//remove port number and compare
			var urlWithOutPort = url.replace(/:[0-9]+/, '');
			var wsWithOutPort = CONFIG.wsUrl.replace(/:[0-9]+/, '');
			return urlWithOutPort.indexOf(wsWithOutPort) === 0;
		},

		login: function (username, password) {
			var creds = {
				username: username,
				password: password
			};
			$resource(CONFIG.wsUrl + 'v2/auth/login', {}, {}).save(creds, function (result) {

				//-------Token Based Authentication----------
				//save tokens
				Storage.set('accessToken', result.accessToken);
				Storage.set('refreshToken', result.refreshToken);

				Users.getByUsername({username: jwt_decode(result.accessToken).sub}, function(user){
					Storage.set('user', user);
					var target = Storage.get('target');
					$location.path(target === null || target === '/login' ? '/' : target);
				});
			}, function () {
				// Storage.reset();
				// remove this because if login fails, user should be able to login again and get redirected
				growl.error('Login failed');
			});
		},
		logout: function () {
			Storage.reset();
			$resource(CONFIG.wsUrl + 'v2/auth/logout', {}, {}).get({}, function () {
				growl.info('You are now logged out');
				//-------Token Based Authentication----------
				//remove token
				// Storage.clear('accessToken');
				// Storage.clear('refreshToken');

				$location.path('/login');
			}, function () {
				growl.error('Logout failed');
			});
		},
		setTarget: function (target) {
			Storage.set('target', target);
		},
		getTarget: function () {
			return Storage.get('target');
		},
		remoteUser: function () {
			return Storage.get('user');
		},
		getUsername: function() {
			var user = this.remoteUser();
			if (user) {
				return user.userName;
			} else {
				return null;
			}
		},
		isLoggedIn: function () {
			return this.remoteUser() !== null;
		},
		isPrivileged: function () {
			var user = this.remoteUser();
			return (user) ? user.privileged : null;
		},
		isDisabled: function (item) {
			var user = Storage.get('user');
			return !(user && (user.privileged || user.userName === item.ownerName));
		},
		getRefreshPath: function(){
			return refreshPath;
		},
		refreshToken: function(){
			var creds = {
				refreshToken: Storage.get('refreshToken')
			};
			return $resource(CONFIG.wsUrl + refreshPath, {}, {}).save(creds, function(data){
				Storage.set('accessToken', data.accessToken);
			}, function(error){
				growl.error(error);
			});
		}
	};

}]);