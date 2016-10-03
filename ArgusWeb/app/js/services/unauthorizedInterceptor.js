angular.module('argus.services.interceptor', [])
.factory("UnauthorizedInterceptor", ['$q', '$location', 'Storage', function ($q, $location, Storage) {
    return {
        responseError: function (rejection) {
            if (rejection.status === 401 || rejection.status === 0) {
                var url = rejection.config.url;
                var suffix = '/login';
                if (url.indexOf(suffix, url.length - suffix.length) === -1) {
                    var target = Storage.get('target');
                    Storage.reset();
                    Storage.set('target', target);
                    $location.path('/login');
                    return;
                }
            }
            return $q.reject(rejection);
        }
    };
}]);
