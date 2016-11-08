angular.module('argus.services.notifications', [])
.factory('Notifications', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/notifications/:notificationId', {}, {
            query: {method: 'GET', params: {notificationId: ''}, isArray: true},
            save: {method: 'POST', isArray: true},
            update: {method: 'PUT'}
        });
    }]);