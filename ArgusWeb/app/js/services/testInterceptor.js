/**
 * Created by pfu on 3/15/17.
 */
'use strict';
/*global angular:false */

//just for test and manipulate http request/response
angular.module('argus.services.testInterceptor',[])
.factory('TestInterceptor', ['$q', '$location', 'Storage', function($q, $location, Storage){
	return {
		'request' : function(config){
			Storage; //just for breakpoints in chrome
			return config;
		},
		'response' : function(response){
			Storage;
			var testcode=0;//for the breakpoint
			if(testcode==1){
				return $q.reject(response);
			}
			return response;
		},
		'responseError' : function(response){
			Storage;
			var testcode=0;//for the breakpoint
			if(testcode==1){
				return $q.resolve(response);
			}
			return $q.reject(response);
		}
	};
}]);