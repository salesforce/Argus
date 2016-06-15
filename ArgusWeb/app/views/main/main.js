/*! Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *   
 *      Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 *      Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 *      Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
'use strict';

/* App Module */

var argusMain = angular.module('argusMain', [
    'ngRoute',
    'ngAnimate',
    'ngStorage',
    'angular-growl',
    'angularUtils.directives.dirPagination',
    'argusAbout',
    'argusAlerts',
    'argusNamespace',
    'argusDashboards',
    'argusViewMetrics',
    'argusControls',
    'argusDashboardService',
    'argusViewElements',
    'argusLogin',
    'argusMockups',
    'argusSpinningWheel',
    'angulartics',
    'angulartics.piwik',
    'argusConfig',
    'ui.bootstrap',
    'ui.bootstrap.datetimepicker'
]);

argusMain.constant('VIEWELEMENT', {
    chart: 'chart',
    heatmap: 'heatmap',
    table: 'table'
});

argusMain.constant('CHARTTYPE', {
	line: 'line',
	area: 'area'
});

argusMain.constant('HEATMAPTYPE', {

});

argusMain.config(['$routeProvider', '$httpProvider', 'growlProvider', 'paginationTemplateProvider', '$analyticsProvider',
    function ($routeProvider, $httpProvider, growlProvider, paginationTemplateProvider, $analyticsProvider) {
        $httpProvider.defaults.withCredentials = true;
        $httpProvider.interceptors.push('UnauthorizedInterceptor');
        paginationTemplateProvider.setPath('bower_components/angular-utils-pagination/dirPagination.tpl.html');
        $routeProvider.
                when('/viewmetrics', {
                    templateUrl: 'views/viewmetrics/viewmetrics.html',
                    controller: 'ViewMetricsCtrl'
                }).
                when('/dashboards', {
                    templateUrl: 'views/dashboards/dashboard-list.html',
                    controller: 'DashboardListCtrl'
                }).
                when('/dashboards/:dashboardId', {
                    templateUrl: 'views/dashboards/dashboard-detail.html',
                    controller: 'DashboardDetailCtrl'
                }).
                when('/alerts', {
                    templateUrl: 'views/alerts/alert-list.html',
                    controller: 'AlertListCtrl'
                }).
                when('/alerts/:alertId', {
                    templateUrl: 'views/alerts/alert-detail.html',
                    controller: 'AlertDetailCtrl'
                }).
                when('/about', {
                    templateUrl: 'views/about/about.html',
                    controller: 'AboutDetailCtrl'
                }).
                when('/login', {
                    templateUrl: 'views/login/login.html',
                    controller: 'LoginCtrl'
                }).
                when('/topkheatmap', {
                    templateUrl: 'views/mockups/topkheatmap.html',
                    controller: 'TopkheatmapCtrl'
                }).
                when('/topkheatmaporg', {
                    templateUrl: 'views/mockups/topkheatmaporg.html',
                    controller: 'TopkheatmapCtrlOrg'
                }).
                when('/namespace', {
                    templateUrl: 'views/namespace/namespace.html',
                    controller: 'NamespaceCtrl'
                }).
                otherwise({
                    redirectTo: '/dashboards'
                });
        growlProvider.onlyUniqueMessages(false);
        growlProvider.globalDisableCloseButton(true);
        growlProvider.globalDisableCountDown(true);
        growlProvider.globalPosition('top-center');
        growlProvider.globalDisableIcons(true);
        growlProvider.globalTimeToLive(3000);
        
        $analyticsProvider.firstPageview(true); /* Records pages that don't use $state or $route */
        $analyticsProvider.withAutoBase(true);  /* Records full path */

    }]);

argusMain.run(['CONFIG', '$rootScope', '$location', '$route', 'Auth', 'growl', function (CONFIG, $rootScope, $location, $route, Auth, growl) {

        $rootScope.$on('$locationChangeStart', function (event, next, current) {
            var loggedIn = Auth.isLoggedIn();
            var target = Auth.getTarget();
            var path = $location.path();
            
            if(loggedIn) {
            	if(path === '/login') {
            		event.preventDefault();
            		Auth.setTarget(null);
            		$location.path(target === null ? '/dashboards' : target);
            	} else {
            		Auth.setTarget(path);
            	}
            } else if(!loggedIn && path !== '/login') {
            	console.log('DENY');
            	growl.error("You are not logged in.");
            	event.preventDefault();
            	Auth.setTarget(path);
            	$location.path('/login');
            } else if(!angular.isDefined(current)) {
            	event.preventDefault();
            	$route.reload();
            }
            
        });
        
        (function(config) {
			_paq.push([ "trackPageView" ]);
			_paq.push([ "enableLinkTracking" ]);
			_paq.push([ "setTrackerUrl", config.piwikUrl + "piwik.php" ]);
			_paq.push([ "setSiteId", config.piwikSiteId ]);
			var d = document, g = d.createElement("script"), s = d.getElementsByTagName("script")[0];
			g.type = "text/javascript";
			g.defer = true;
			g.async = true;
			g.src = config.piwikUrl + "piwik.js";
			s.parentNode.insertBefore(g, s);
		})(CONFIG);
        
    }]);

argusMain.controller('MainCtrl', ['$scope', 'Auth', '$location', function ($scope, Auth, $location) {

		/*
        $scope.$watch(Auth.remoteUser, function (value, oldValue) {
            if (angular.isUndefined(value) || value === null || (!value && oldValue)) {
                $location.path('/login');
            } else {
                var target = Auth.getTarget();
                $location.path(target === null || target === '/login' ? '/' : target);
            }
        }, true);
        */

        $scope.login = function (username, password) {
        	if(username.indexOf("@") != -1) {
        		username = username.substring(0, username.indexOf("@"));
        	}
            Auth.login(username, password);
        };

        $scope.logout = function () {
            Auth.logout();
        };

        $scope.getRemoteUser = function () {
            var user = Auth.remoteUser();
            if (user) {
                return user.userName;
            } else {
                return null;
            }
        };

        $scope.isLoggedIn = function () {
            return Auth.isLoggedIn();
        };

    }]);

argusMain.factory('Auth', ['$resource', '$location', 'CONFIG', 'growl', 'Storage', function ($resource, $location, CONFIG, growl, Storage) {
        return{
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
            isLoggedIn: function () {
                return this.remoteUser() !== null;
            }
        };
    }]);

argusMain.factory('Storage', ['$rootScope', '$localStorage', function ($rootScope, $localStorage) {
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

argusMain.factory("UnauthorizedInterceptor", ['$q', '$location', 'Storage', function ($q, $location, Storage) {
        return {
            responseError: function (rejection) {
                if(rejection.status === 401 || rejection.status === 0) {
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

argusMain.directive('ngConfirm', [function () {
        return {
            restrict: 'A',
            link: function (scope, element, attrs) {
                element.bind('click', function () {
                    var message = attrs.ngConfirm;
                    if (message && confirm(message)) {
                        scope.$apply(attrs.ngConfirmAction);
                    }
                });
            }
        };
    }]);

argusMain.directive('stopEvent', function () {
    return {
        restrict: 'A',
        link: function (scope, element, attr) {
            element.bind('click', function (e) {
                e.stopPropagation();
            });
        }
    };
});

argusMain.factory('History', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'audit/entity/:id', {id: '@id', limit: '20'}, {});
    }]);

argusMain.factory('JobExecutionDetails', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'history/job/:id', {id: '@id', limit: '20'}, {});
    }]);

argusMain.filter('urlencode', function () {
    return window.encodeURIComponent;
});
argusMain.filter('newline', function(){
    return function(data) {
        if (data && data.length > 0) {

           var  retvalue = data.replace(/</g, '&lt');
            retvalue = retvalue.replace(/>/g, '&gt');
            retvalue=retvalue.replace(/\n/g, '<br/>');
            return retvalue;
        } else return "";
    }
});
argusMain.filter('trustedhtml', ['$sce', function ($sce) {
        return function (text) {
            return $sce.trustAsHtml(text);
        };
    }]);
