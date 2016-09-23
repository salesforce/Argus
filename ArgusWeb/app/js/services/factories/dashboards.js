angular.module('argus.services.dashboards', [])
.factory('Dashboards', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'dashboards/:dashboardId', {}, {
        query: {method: 'GET', params: {dashboardId: ''}, isArray: true},
        update: {method: 'PUT'}
    });
}]);
