angular.module('argus.services.metrics', [])
.factory('Metrics', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'metrics', {}, {
        query: {method: 'GET', isArray: true}
    });
}]);
