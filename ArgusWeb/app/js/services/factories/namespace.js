angular.module('argus.services.namespace', [])
.factory('Namespace', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	return $resource(CONFIG.wsUrl + 'namespace/:namespaceId', {}, {
        update: {method: 'PUT'}
    });
}]);