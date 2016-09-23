angular.module('argus.services.storage', [])
.factory('Storage', ['$rootScope', '$localStorage', function ($rootScope, $localStorage) {
    $rootScope.storage = $localStorage;
    return {
        set: function (key, value) {
            $rootScope.storage[key] = value;
        },
        get: function (key) {
            var result = $rootScope.storage[key];
            return angular.isDefined(result) ? result : null;
        },
        clear: function (key) {
            delete $rootScope.storage[key];
        },
        reset: function () {
            $rootScope.storage.$reset();
        }
    };
}]);