angular.module('argus.services.asyncMetrics', ['ngResource'])
.factory('AsyncMetrics', ['$http', 'CONFIG', function ($http, CONFIG) {
    return {
        create: function (params) {
            return $http({
                url: CONFIG.wsUrl + 'metrics/batch',
                method: 'GET',
                params: params
            });
        }
    };
}]);