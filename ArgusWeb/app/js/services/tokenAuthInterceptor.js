/**
 * Created by pfu on 3/14/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.tokenAuthInterceptor',[])
.factory('TokenAuthInterceptor', ['$q', '$location', 'Storage', '$injector', 'CONFIG', 'growl', function($q, $location, Storage, $injector, CONFIG, growl){
	var refreshTokenRequest = null;
	var failRequestLimit = 50;
	var failRequestCounter = 0;
	return {
		'request' : function(config){
			config.headers = config.headers || {};
			var accessToken = Storage.get('accessToken');
			if (accessToken){
				config.headers.Authorization = 'Bearer ' + accessToken;
			}
			return config;
		},

		'responseError': function(response) {
			var Auth = $injector.get('Auth');
			var refreshPath = Auth.getRefreshPath();

			var path = $location.path();
			var deferred = $q.defer();

			var target = Storage.get('target');

			if(response.status === 0){
				Storage.reset();
				Storage.set('target', target);
				$location.path('/login');

			}else if(response.status === 401){
				if(path === '/login' ||
					!Storage.get('accessToken')||
					!Storage.get('refreshToken')
					){
					//login fails, just return to login page
					//no token found just return to login page
				}else if(failRequestCounter > failRequestLimit){
					//prevent infinite loop
					//this might happen when you can get refreshToken but keeps getting 401 with new requests
					var ConfirmClick = $injector.get('ConfirmClick');
					ConfirmClick.openConfirmModal(
						'Number of fail requests exceeds limit!',
						'This might be caused by invalid webservice endpoint, please check your markup! <br>'
					);

					deferred.reject();
					return deferred.promise;

				}else if(response.config.url === CONFIG.wsUrl + refreshPath){
					growl.error('You refresh token has expired');//-------Token Based Authentication----------
				}else{
					//accessToken fails, refresh accessToken
					failRequestCounter ++;
					if(!refreshTokenRequest){
						refreshTokenRequest =  Auth.refreshToken(); //preventing resending duplicate token refresh request
					}
					refreshTokenRequest.$promise.then(function () {
						refreshTokenRequest = null;
						//resend request
						$injector.get('$http')(response.config).then(function (resp) {
							deferred.resolve(resp);
							failRequestCounter = 0;
						}, function (error) {
							deferred.reject(error);
						});
					}, function(error){
						refreshTokenRequest = null;
						deferred.reject(error);
					});
					return deferred.promise;
				}
				//remove token and other stuff
				Storage.reset();
				Storage.set('target', target);
				$location.path('/login');
			}

			deferred.reject(response);
			return deferred.promise;
		}
	};
}]);