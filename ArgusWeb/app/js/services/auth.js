angular.module('argus.services.auth', [])
.factory('Auth', ['$resource', '$location', 'CONFIG', 'growl', 'Storage', function ($resource, $location, CONFIG, growl, Storage) {
    return {
        login: function (username, password) {
            var creds = {
                username: username,
                password: password
            };
            $resource(CONFIG.wsUrl + 'auth/login', {}, {}).save(creds, function (result) {
                Storage.set('user', result);
                var target = Storage.get('target');
                $location.path(target === null || target === '/login' ? '/' : target);
            }, function (error) {
                Storage.reset();
                growl.error('Login failed');
            });
        },
        logout: function () {
            Storage.reset();
            $resource(CONFIG.wsUrl + 'auth/logout', {}, {}).get({}, function (result) {
                growl.info('You are now logged out');
                $location.path('/login');
            }, function (error) {
                growl.error('Logout failed');
            });
        },
        setTarget: function (target) {
            Storage.set('target', target);
        },
        getTarget: function () {
            return Storage.get('target');
        },
        remoteUser: function () {
            return Storage.get('user');
        },
        getUsername: function() {
            var user = this.remoteUser();
            if (user) {
                return user.userName;
            } else {
                return null;
            }
        },
        isLoggedIn: function () {
            return this.remoteUser() !== null;
        },
        isPrivileged: function () {
            var user = this.remoteUser();
            return (user) ? user.privileged : null;
        }
    };
}]);