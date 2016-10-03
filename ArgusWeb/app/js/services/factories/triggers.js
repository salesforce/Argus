angular.module('argus.services.triggers', [])
.factory('Triggers', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/triggers/:triggerId', {}, {
            query: {method: 'GET', params: {triggerId: ''}, isArray: true},
            save: {method: 'POST', isArray: true},
            update: {method: 'PUT'}
        });
    }]);