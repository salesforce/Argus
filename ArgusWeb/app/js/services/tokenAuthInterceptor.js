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
	function redirectToLogin(){
		var target = Storage.get('target');
		//remove user info and other stuff
		Storage.reset();
		Storage.set('target', target); // for redirect to previous url after relogin
		$location.path('/login');
	}
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

			if(response.status === 0){
				redirectToLogin();
			}else if(response.status !== 401 && (response.config.url === CONFIG.wsUrl + refreshPath)){
				var message = 'Your refresh token is invalid';
				growl.error(response.data && response.data.message || message); //-------Token Based Authentication----------
				redirectToLogin();
			}else if(response.status === 401){
				if(path === '/login'){
					//login fails, just return to login page
				}else if(!Storage.get('accessToken')|| !Storage.get('refreshToken')){
					Storage.set('loginError', 'accessToken or refreshToken missing');
				}else if(failRequestCounter > failRequestLimit){
					//prevent infinite loop
					//this might happen when you can get refreshToken but keeps getting 401 with new requests
					var ConfirmClick = $injector.get('ConfirmClick');
					ConfirmClick.openConfirmModal(
						'Number of fail requests exceeds limit!',
						'This might be caused by invalid webservice endpoint, please check your markup! <br>'
					);

					deferred.reject(response);
					return deferred.promise;

				}else if(response.config.url === CONFIG.wsUrl + refreshPath){
					message = 'Your refresh token has expired';
					growl.error(response.data && response.data.message || message );//-------Token Based Authentication----------
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
				redirectToLogin();
			}

			deferred.reject(response);
			return deferred.promise;
		}
	};
}]);