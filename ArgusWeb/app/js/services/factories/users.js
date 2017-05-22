/**
 * Created by pfu on 5/1/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.users', [])
.factory('Users', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	return $resource(CONFIG.wsUrl + 'users/:option/:username',{},{
		getByUsername: {
			method: 'GET',
			params: {option: 'username'}
		}
	});
}]);