angular.module('argus.services.history', [])
.factory('History', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'audit/entity/:id', {id: '@id', limit: '20'}, {});
}]);