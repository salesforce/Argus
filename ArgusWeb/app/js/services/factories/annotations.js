angular.module('argus.services.annotations', [])
.factory('Annotations', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'annotations', {}, {
        query: {method: 'GET', isArray: true}
    });
}]);
