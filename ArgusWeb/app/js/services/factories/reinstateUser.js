angular.module('argus.services.admin.reinstateuser', ['ngResource'])
.factory('ReinstateUser', ['$resource', 'CONFIG',
		function ($resource, CONFIG) {
				return $resource(CONFIG.wsUrl + 'management/reinstateuser', {}, {
						update: {method: 'PUT'}
				});
		}
]);