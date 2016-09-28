angular.module('argus.services.batches', ['ngResource'])
.factory('Batches', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'batches/:batchId', {}, {
        query: {method: 'GET', params: {batchId: ''}}
    });
}]);