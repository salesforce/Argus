angular.module('argus.services.alerts', [])
.factory('Alerts', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId', {}, {
            query: {method: 'GET', params: {alertId: ''}, isArray: true},
            update: {method: 'PUT'}
        });
    }]);