angular.module('argus.services.triggersmap', [])
.factory('TriggersMap', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/notifications/:notificationId/triggers/:triggerId', {}, {
            map: {method: 'POST'},
            unmap: {method: 'DELETE'}
        });
    }]);
