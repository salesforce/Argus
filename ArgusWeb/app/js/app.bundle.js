/******/ (function(modules) { // webpackBootstrap
/******/ 	// The module cache
/******/ 	var installedModules = {};

/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {

/******/ 		// Check if module is in cache
/******/ 		if(installedModules[moduleId])
/******/ 			return installedModules[moduleId].exports;

/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = installedModules[moduleId] = {
/******/ 			i: moduleId,
/******/ 			l: false,
/******/ 			exports: {}
/******/ 		};

/******/ 		// Execute the module function
/******/ 		modules[moduleId].call(module.exports, module, module.exports, __webpack_require__);

/******/ 		// Flag the module as loaded
/******/ 		module.l = true;

/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}


/******/ 	// expose the modules object (__webpack_modules__)
/******/ 	__webpack_require__.m = modules;

/******/ 	// expose the module cache
/******/ 	__webpack_require__.c = installedModules;

/******/ 	// identity function for calling harmony imports with the correct context
/******/ 	__webpack_require__.i = function(value) { return value; };

/******/ 	// define getter function for harmony exports
/******/ 	__webpack_require__.d = function(exports, name, getter) {
/******/ 		if(!__webpack_require__.o(exports, name)) {
/******/ 			Object.defineProperty(exports, name, {
/******/ 				configurable: false,
/******/ 				enumerable: true,
/******/ 				get: getter
/******/ 			});
/******/ 		}
/******/ 	};

/******/ 	// getDefaultExport function for compatibility with non-harmony modules
/******/ 	__webpack_require__.n = function(module) {
/******/ 		var getter = module && module.__esModule ?
/******/ 			function getDefault() { return module['default']; } :
/******/ 			function getModuleExports() { return module; };
/******/ 		__webpack_require__.d(getter, 'a', getter);
/******/ 		return getter;
/******/ 	};

/******/ 	// Object.prototype.hasOwnProperty.call
/******/ 	__webpack_require__.o = function(object, property) { return Object.prototype.hasOwnProperty.call(object, property); };

/******/ 	// __webpack_public_path__
/******/ 	__webpack_require__.p = "";

/******/ 	// Load entry module and return exports
/******/ 	return __webpack_require__(__webpack_require__.s = 70);
/******/ })
/************************************************************************/
/******/ ([
/* 0 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


/* App Module */
angular.module('argus.config', [])
.config(['$routeProvider', '$httpProvider', 'growlProvider', 'paginationTemplateProvider', '$analyticsProvider',
    function ($routeProvider, $httpProvider, growlProvider, paginationTemplateProvider, $analyticsProvider) {
        $httpProvider.defaults.withCredentials = true;
        $httpProvider.interceptors.push('UnauthorizedInterceptor');
        paginationTemplateProvider.setPath('bower_components/angular-utils-pagination/dirPagination.tpl.html');
        $routeProvider.
            when('/viewmetrics', {
                templateUrl: 'js/templates/viewmetrics.html',
                controller: 'ViewMetrics',
                label: 'Metrics',
                activeTab: 'metrics',
                reloadOnSearch: false
            }).
            when('/batches', {
                templateUrl: 'js/templates/batches.html',
                controller: 'BatchExpressions',
                activeTab: 'batches'
            }).
            when('/dashboards', {
                templateUrl: 'js/templates/dashboard-list.html',
                controller: 'Dashboards',
                label: 'Dashboard List',
                activeTab: 'dashboards'
            }).
            when('/dashboards/:dashboardId', {
                templateUrl: 'js/templates/dashboard-detail.html',
                controller: 'DashboardsDetail',
                label: '{{dashboards.dashboardId}}',
                activeTab: 'dashboards',
                reloadOnSearch: false
            }).
            when('/alerts', {
                templateUrl: 'js/templates/alert-list.html',
                controller: 'Alerts',
                label: 'Alerts List',
                activeTab: 'alerts'
            }).
            when('/alerts/:alertId', {
                templateUrl: 'js/templates/alert-detail.html',
                controller: 'AlertsDetail',
                label: '{{alerts.alertId}}',
                activeTab: 'alerts'
            }).
            when('/about', {
                templateUrl: 'js/templates/about.html',
                controller: 'About',
                label: 'About Argus',
                activeTab: 'about'
            }).
            when('/admin', {
                templateUrl: 'js/templates/admin.html',
                controller: 'Admin',
                activeTab: 'admin'
            }).
            when('/login', {
                templateUrl: 'js/templates/login.html',
                controller: 'Login',
                label: 'User Login',
                activeTab: ''
            }).
            when('/namespace', {
                templateUrl: 'js/templates/namespace.html',
                controller: 'Namespace',
                label: 'Namespace',
                activeTab: 'namespace'
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
}])

.run(['CONFIG', '$rootScope', '$location', '$route', 'Auth', 'growl', function (CONFIG, $rootScope, $location, $route, Auth, growl) {

    $rootScope.$on('$locationChangeStart', function (event, next, current) {
        var loggedIn = Auth.isLoggedIn();
        var target = Auth.getTarget();
        var path = $location.path();

        if (loggedIn) {
        	if (path === '/login') {
        		event.preventDefault();
        		Auth.setTarget(null);
        		$location.path(target === null ? '/dashboards' : target);
        	} else {
        		Auth.setTarget(path);
        	}
        } else if (!loggedIn && path !== '/login') {
        	console.log('DENY');
        	growl.error("You are not logged in.");
        	event.preventDefault();
        	Auth.setTarget(path);
        	$location.path('/login');
        } else if (!angular.isDefined(current)) {
        	event.preventDefault();
        	$route.reload();
        }
    });
}]);


/***/ }),
/* 1 */
/***/ (function(module, exports) {

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
angular.module("argus.urlConfig", [])
.constant('CONFIG', {
    version: '2.4.0-SNAPSHOT',
    wsUrl: 'https://argus-ws.data.sfdc.net:443/argusws/',
    emailUrl: 'https://mail.google.com/mail/?view=cm&fs=1&tf=1&to=argus-dev@salesforce.com',
    feedUrl: 'https://groups.google.com/a/salesforce.com/forum/?hl=en#!forum/argus-user',
    wikiUrl: 'https://github.com/salesforce/Argus/wiki',
    issueUrl: 'https://groups.google.com/a/salesforce.com/forum/?hl=en#!forum/argus-dev',
    templatePath: ''
});


/***/ }),
/* 2 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.controllers.about', [])
.controller('About', ['$scope', 'CONFIG',
    function ($scope, CONFIG) {
        $scope.config = CONFIG;
    }]);


/***/ }),
/* 3 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.controllers.admin', ['ngResource'])
.controller('Admin', ['$scope', 'CONFIG', 'Auth', 'growl', 'ReinstateUser',
		function ($scope, CONFIG, Auth, growl, ReinstateUser) {
				$scope.config = CONFIG;

				// check for admin, otherwise don't render view
				$scope.isPrivileged = Auth.isPrivileged();

        $scope.submitUser = function () {
						if ($scope.username === '' || $scope.subsystem === '') return;

						var userInfo = {
								username: $scope.username,
								subsystem: $scope.subsystem
						};

						// submit request to reinstate a specific user
						ReinstateUser.update(userInfo, function (result) {
								// reset $scope values after submission
								// $scope.username = "";
								// $scope.subsystem = "";

								growl.success('User: "' + result + '" successfully reinstated!"');
						}, function (error) {
								growl.error( true ? error.data.message : error.statusText);
						});
				};
		}
]);


/***/ }),
/* 4 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.controllers.alerts', ['ngResource'])
.controller('Alerts', ['Auth', '$scope', 'growl', 'Alerts', '$sessionStorage', 'TableListService', function (Auth, $scope, growl, Alerts, $sessionStorage, TableListService) {

    $scope.colName = {
        id:'ID',
        name:'Name',
        cronEntry:'CRON Entry',
        createdDate:'Created',
        modifiedDate:'Last Modified',
        ownerName:'Owner',
        state: "State"
    };
    $scope.properties = {
        title: "Alert",
        type: "alerts"
    };
    $scope.tabNames = {
        firstTab: Auth.getUsername() + "'s Alerts",
        secondTab: 'Shared Alerts'
    };
    $scope.alerts = [];
    $scope.alertsLoaded = false;

    var alertLists = {
        sharedList: [],
        usersList: []
    };
    var remoteUsername = Auth.getUsername();


    $scope.getAlerts = function (shared) {
        $sessionStorage.alerts.shared = shared;
        if ($scope.alertsLoaded) {
            // when only user's alerts are loaded but shared tab is chosen: need to start a new API call
            if (shared && !$sessionStorage.alerts.loadedEverything) {
                $scope.alertsLoaded = false;
                getAllAlerts();
            } else {
                $scope.alerts = shared ? alertLists.sharedList : alertLists.usersList;
            }
        }
    };

    function setAlertsAfterLoading (alerts, shared) {
        alertLists.sharedList = TableListService.getListUnderTab(alerts, true, remoteUsername);
        alertLists.usersList = TableListService.getListUnderTab(alerts, false, remoteUsername);
        $scope.alertsLoaded = true;
        $scope.getAlerts(shared);
    }

    function getAllAlerts () {
        Alerts.getMeta().$promise.then(function(alerts) {
            $sessionStorage.alerts.cachedData = alerts;
            $sessionStorage.alerts.loadedEverything = true;
            setAlertsAfterLoading(alerts, $scope.shared);
        });
    }

    function getUsersAlerts () {
        Alerts.getUsers().$promise.then(function(alerts) {
            $sessionStorage.alerts.cachedData = alerts;
            $sessionStorage.alerts.loadedEverything = false;
            setAlertsAfterLoading(alerts, false);
        });
    }

	$scope.refreshAlerts = function () {
        delete $sessionStorage.alerts.cachedData;
        delete $scope.alerts;
        $scope.alertsLoaded = false;
        $scope.shared? getAllAlerts(): getUsersAlerts();
	};

    $scope.addAlert = function () {
        var alert = {
            name: 'new-alert-' + Date.now(),
            expression: "-1h:scope:metric{tagKey=tagValue}:avg",
            cronEntry: "0 */4 * * *"
        };
        Alerts.save(alert, function (result) {
            // update both scope and session alerts
            result.expression = "";
            alertLists = TableListService.addItemToTableList(alertLists, 'alerts', result, remoteUsername);
            $scope.getAlerts($scope.shared);
            growl.success('Created "' + alert.name + '"');
        }, function (error) {
            growl.error('Failed to create "' + alert.name + '"');
        });
    };

    $scope.removeAlert = function (alert) {
        Alerts.delete({alertId: alert.id}, function (result) {
            alertLists = TableListService.deleteItemFromTableList(alertLists, 'alerts', alert, remoteUsername);
            $scope.getAlerts($scope.shared);
            growl.success('Deleted "' + alert.name + '"');
        }, function (error) {
            growl.error('Failed to delete "' + alert.name + '"');
        });
    };

    $scope.enableAlert = function (alert, enabled) {
        if (alert.enabled !== enabled) {
            Alerts.get({alertId: alert.id}, function(updated) {
                updated.enabled = enabled;
                Alerts.update({alertId: alert.id}, updated, function (result) {
                    alert.enabled = enabled;
                    $sessionStorage.cachedAlerts = $scope.alerts;
                    growl.success((enabled ? 'Enabled "' : 'Disabled "') + alert.name + '"');
                }, function (error) {
                    growl.error('Failed to ' + (enabled ? 'enable "' : 'disable "') + alert.name + '"');
                });
            });
        }
    };

    if ($sessionStorage.alerts === undefined) $sessionStorage.alerts = {};
    if ($sessionStorage.alerts.cachedData !== undefined && $sessionStorage.alerts.shared !== undefined) {
        var alerts = $sessionStorage.alerts.cachedData;
        setAlertsAfterLoading(alerts, $sessionStorage.alerts.shared);
    } else {
        $scope.shared? getAllAlerts(): getUsersAlerts();
    }


}]);


/***/ }),
/* 5 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.controllers.alerts.detail', ['ngResource'])
.controller('AlertsDetail', ['$scope', '$routeParams', '$location', 'growl', 'Alerts', 'Triggers', 'Notifications', 'History', 'TriggersMap', 'JobExecutionDetails', '$sessionStorage', 'Auth',
    function ($scope, $routeParams, $location, growl, Alerts, Triggers, Notifications, History, TriggersMap, JobExecutionDetails, $sessionStorage, Auth) {
        $scope.alertNotEditable = true;
        $scope.isAlertDirty = function () {
            return !angular.equals($scope.alert, $scope.unmodifiedAlert);
        };

        $scope.isTriggerDirty = function () {
            return !angular.equals($scope.triggers, $scope.unmodifiedTriggers);
        };

        $scope.isNotificationDirty = function () {
            return !angular.equals($scope.notifications, $scope.unmodifiedNotifications);
        };

        $scope.updateAlert = function () {
            if ($scope.isAlertDirty()) {
                var alert = $scope.alert;

                Alerts.update({alertId: alert.id}, alert, function (result) {
                    $scope.unmodifiedAlert = angular.copy(alert);
                    growl.success(('Updated "') + alert.name + '"');
                    $scope.fetchHistory();
                    $scope.fetchJobExecutionDetails();
                    // remove existing session storage for update
                    if ($sessionStorage.alerts !== undefined)delete $sessionStorage.alerts.cachedData;
                }, function (error) {
                    growl.error('Failed to update "' + alert.name + '"');
                });
            }
        };

        $scope.updateTriggers = function () {
            if ($scope.isTriggerDirty()) {
                var triggers = $scope.triggers;
                for (var i = 0; i < triggers.length; i++) {
                    $scope.updateTrigger(triggers[i]);
                }
                $scope.fetchHistory();
                $scope.fetchJobExecutionDetails();
            }
        };

        $scope.updateTrigger = function (trigger) {
            var toUpdate = $scope.unmodifiedTriggers.filter(function (element) {
                return element.id === trigger.id;
            });

            if (toUpdate.length === 1 && !angular.equals(toUpdate[0], trigger)) {
                Triggers.update({alertId: trigger.alertId, triggerId: trigger.id}, trigger, function (result) {
                    $scope.unmodifiedTriggers.filter(function (element) {
                        if (element.id === trigger.id) {
                            angular.copy(trigger, element);
                            growl.success('Updated trigger "' + trigger.id + '".');
                        }
                    });
                }, function (error) {
                    growl.error('Failed to update "' + trigger.name + '"');
                });
            }
        };

        $scope.updateNotifications = function () {
            if ($scope.isNotificationDirty()) {
                var notifications = $scope.notifications;
                for (var i = 0; i < notifications.length; i++) {
                    $scope.updateNotification(notifications[i]);
                }
                $scope.fetchHistory();
                $scope.fetchJobExecutionDetails();
            }
        };

        $scope.updateNotification = function (notification) {
            var toUpdate = $scope.unmodifiedNotifications.filter(function (element) {
                return element.id === notification.id;
            });

            if (toUpdate.length === 1 && !angular.equals(toUpdate[0], notification)) {
                Notifications.update({alertId: notification.alertId, notificationId: notification.id}, notification, function (result) {
                    $scope.unmodifiedNotifications.filter(function (element) {
                        if (element.id === notification.id) {
                            angular.copy(notification, element);
                            growl.success('Updated notification "' + notification.id + '".');
                        }
                    });
                    $scope.mapTriggers(notification);
                }, function (error) {
                    growl.error('Failed to update "' + notification.name + '"');
                });
            }
        };

        // Restore the unmodified version.
        $scope.resetTriggers = function () {
            $scope.triggers = angular.copy($scope.unmodifiedTriggers);
        };

        $scope.resetAlert = function () {
            $scope.alert = angular.copy($scope.unmodifiedAlert);
        };

        // Restore the unmodified version.
        $scope.resetNotifications = function () {
            $scope.notifications = angular.copy($scope.unmodifiedNotifications);
        };

        $scope.addTrigger = function () {
            var trigger = {
                name: 'new-trigger-' + Date.now(),
                type: "GREATER_THAN",
                threshold: 0,
                secondaryThreshold: 0,
                inertia: 0,
                alertId: $scope.alert.id
            };

            Triggers.save({alertId: $scope.alert.id}, trigger, function (result) {
                angular.copy(result, $scope.triggers);
                $scope.unmodifiedTriggers = angular.copy($scope.triggers);
                $scope.fetchHistory();
                $scope.fetchJobExecutionDetails();
                growl.success('Created "' + trigger.name + '"');
            }, function (error) {
                growl.error('Failed to create "' + trigger.name + '"');
            });
        };

        $scope.addNotification = function () {
            var notification = {
                name: 'new-notification-' + Date.now(),
                notifierName: "com.salesforce.dva.argus.service.alert.notifier.EmailNotifier",
                subscriptions: [],
                metricsToAnnotate: [],
                cooldownPeriod: 0,
                sractionable:false,
                alertId: $scope.alert.id
            };

            Notifications.save({alertId: $scope.alert.id}, notification, function (result) {
                angular.copy(result, $scope.notifications);
                $scope.unmodifiedNotifications = angular.copy($scope.notifications);
                $scope.fetchHistory();
                $scope.fetchJobExecutionDetails();
                growl.success('Created "' + notification.name + '"');
            }, function (error) {
                growl.error('Failed to create "' + notification.name + '"');
            });
        };

        $scope.removeTrigger = function (trigger) {
            Triggers.delete({alertId: trigger.alertId, triggerId: trigger.id}, function (result) {
                $scope.triggers = $scope.triggers.filter(function (element) {
                    return element.id !== trigger.id;
                });
                $scope.unmodifiedTriggers = angular.copy($scope.triggers);
                $scope.fetchHistory();
                $scope.fetchJobExecutionDetails();
                growl.success('Deleted "' + trigger.name + '"');
            }, function (error) {
                growl.error('Failed to delete "' + trigger.name + '"');
            });
        };

        $scope.removeNotification = function (notification) {
            Notifications.delete({alertId: notification.alertId, notificationId: notification.id}, function (result) {
                $scope.notifications = $scope.notifications.filter(function (element) {
                    return element.id !== notification.id;
                });
                $scope.unmodifiedNotifications = angular.copy($scope.notifications);
                $scope.fetchHistory();
                $scope.fetchJobExecutionDetails();
                growl.success('Deleted "' + notification.name + '"');
            }, function (error) {
                growl.error('Failed to delete "' + notification.name + '"');
            });
        };

        $scope.mapTriggers = function (notification) {
            for (var i = 0; i < $scope.unmodifiedTriggers.length; i++) {
                var trigger = $scope.unmodifiedTriggers[i];
                TriggersMap.unmap({alertId: notification.alertId, notificationId: notification.id, triggerId: trigger.id});
            }
            for (var i = 0; i < notification.triggersIds.length; i++) {
                var trigger = notification.triggersIds[i];
                TriggersMap.map({alertId: notification.alertId, notificationId: notification.id, triggerId: trigger}, null);
            }
            $scope.fetchHistory();
            $scope.fetchJobExecutionDetails();
        };

        $scope.isTabSelected = function (tab) {
            return $scope.selectedTab === tab;
        };

        $scope.selectTab = function (tab) {
            $scope.selectedTab = tab;
        };

        $scope.fetchHistory = function() {
            History.query({id: $scope.alertId}, function (history) {
                $scope.history = history;
            }, function (error) {
                growl.error('Failed to get history for alert "' + $scope.alertId + '"');
            });
        };

        $scope.fetchJobExecutionDetails = function() {
            JobExecutionDetails.query({id: $scope.alertId}, function (jobExecutionDetails) {
                $scope.jobExecutionDetails = jobExecutionDetails;
                $scope.jobExecutionDetailsError='';
            }, function (error) {
                if(error.status==404){
                    $scope.jobExecutionDetailsError = ' No alert evaluation details found.';
                }else if(error.status==403){
                    $scope.jobExecutionDetailsError = 'You are not authorized to view this data.';
                }
                else {
                    $scope.jobExecutionDetailsError = error.statusText;
                    growl.error('Failed to get the job execution details "' + $scope.jobId + '"');
                }
            });
        };

        $scope.triggerTypes = [
            {label: '=', value: 'EQUAL'},
            {label: '!=', value: 'NOT_EQUAL'},
            {label: '>', value: 'GREATER_THAN'},
            {label: '<', value: 'LESS_THAN'},
            {label: '>=', value: 'GREATER_THAN_OR_EQ'},
            {label: '<=', value: 'LESS_THAN_OR_EQ'},
            {label: '<>', value: 'BETWEEN'},
            {label: '><', value: 'NOT_BETWEEN'}
        ];

        $scope.notificationTypes = [
            {label: 'Audit', value: 'com.salesforce.dva.argus.service.alert.notifier.AuditNotifier'},
            {label: 'Mail', value: 'com.salesforce.dva.argus.service.alert.notifier.EmailNotifier'},
            {label: 'GOC++', value: 'com.salesforce.dva.argus.service.alert.notifier.GOCNotifier'},
            {label: 'Gus', value: 'com.salesforce.dva.argus.service.alert.notifier.GusNotifier'}
        ];

        $scope.getTriggerIds = function () {
            var ids = [];
            var triggers = $scope.unmodifiedTriggers;
            for (var i = 0; i < triggers.length; i++) {
                ids.push(triggers[i].id);
            }
            return ids;
        };

        $scope.alertId = $routeParams.alertId;
        $scope.triggers = [];
        $scope.notifications = [];
        $scope.unmodifiedTriggers = [];
        $scope.unmodifiedNotifications = [];

        if ($scope.alertId > 0) {
            Alerts.get({alertId: $scope.alertId}, function (alert) {
                $scope.alert = alert;
                $scope.alertNotEditable = Auth.isDisabled(alert);
                $scope.unmodifiedAlert = angular.copy(alert);
            }, function (error) {
                growl.error('Failed to get alert "' + $scope.alertId + '"');
                $location.path('/alerts');
            });

            Triggers.query({alertId: $scope.alertId}, function (triggers) {
                $scope.triggers = triggers;
                $scope.unmodifiedTriggers = angular.copy(triggers);
            }, function (error) {
                growl.error('Failed to get triggers for alert "' + $scope.alertId + '"');
            });

            Notifications.query({alertId: $scope.alertId}, function (notifications) {
                $scope.notifications = notifications;
                $scope.unmodifiedNotifications = angular.copy(notifications);
            }, function (error) {
                growl.error('Failed to get notifications for alert "' + $scope.alertId + '"');
            });
            $scope.fetchHistory();
            $scope.fetchJobExecutionDetails();
        } else {
            growl.error('Failed to get alert "' + $routeParams.alertId + '"');
            $location.path('alerts');
        }
        $scope.selectedTab = 1;

        $scope.resetAlert();
        $scope.resetTriggers();
        $scope.resetNotifications();
    }]);


/***/ }),
/* 6 */
/***/ (function(module, exports) {

angular.module('argus.controllers.batches', ['ngResource'])
.controller('BatchExpressions', ['$scope', 'AsyncMetrics', 'Batches', 'growl', 'BATCH_CHART_OPTIONS', 
    function($scope, AsyncMetrics, Batches, growl, BATCH_CHART_OPTIONS) {
        $('[data-toggle="tooltip"]').tooltip();
        $scope.hudModes = ['graphs', 'JSON'];
        $scope.hudMode = 0;
        $scope.batches = [];
        $scope.expressions = [{expression: ''}];
        $scope.currTtl = '';
        $scope.currBatchState = 'Refresh for current batch status';
        $scope.currBatchId = '';
        var statusToString = ['queued', 'processing', 'done', 'error'];

        $scope.toggleHudMode = function() {
            if ($scope.hudMode === 0) {
                $scope.hudMode = 1;
            } else {
                $scope.hudMode = 0;
            }
            $scope.refreshBatchState();
        };

        $scope.getBatches = function() {
            $scope.batches = [{id: 'Loading...', status: 'Loading...'}];
            Batches.query().$promise.then(function(batchMap) {
                $scope.batches = [];
                for (var id in batchMap) {
                    if (id.length == 36) {
                        $scope.batches.push({id: id, status: statusToString[batchMap[id]]});
                    }
                }
            }, function() {
                $scope.batches = [];
            });
        };
        $scope.getBatches();

        $scope.addExpression = function() {
            $scope.expressions.push({expression: ''});
        };

        $scope.removeExpression = function() {
            var lastIndex = $scope.expressions.length - 1;
            if (lastIndex >= 0) {
                $scope.expressions.splice($scope.expressions.length - 1);
            }
        };

        $scope.submitBatch = function() {
            var params = {};
            params.expression = [];
            for (var i = 0; i < $scope.expressions.length; i++) {
                if ($scope.expressions[i].expression.length > 0) {
                    params.expression.push($scope.expressions[i].expression);
                }
            }
            params.ttl = $scope.currTtl;
            AsyncMetrics.create(params)
                .then(function success(response) {
                    growl.success('Submitted ' + response.data.id);
                    $scope.expressions = [{expression: ''}];
                    $scope.submitted = true;
                    $scope.currBatchId = response.data.id;
                    $scope.getBatches();
                    $scope.refreshBatchState($scope.currBatchId);
                }, function error(response) {
                    growl.error(response.data.message);
                });
        };

        $scope.refreshBatchState = function(batchId) {
            if (batchId) {
                $scope.currBatchId = batchId;
            }
            if ($scope.currBatchId === '') {
                $scope.currBatchState = 'No batch selected';
                return;
            }
            $scope.currBatchState = 'Loading...';
            if ($scope.hudMode === 0) {
                Batches.query({batchId: $scope.currBatchId}, displayBatchAsJson, displayBatchAsError);
            } else {
                Batches.query({batchId: $scope.currBatchId}, displayBatchAsGraphs, displayBatchAsError);
            }
        };

        $scope.deleteBatch = function(batchId) {
            Batches.delete({batchId: batchId}, function() {
                $scope.batches = $scope.batches.filter(function(batch) {
                    return batch.id !== batchId;
                });
                growl.success('Deleted ' + batchId);
            }, function() {
                growl.error('Failed to delete ' + batchId);
            });
        };

        // These functions and the below constant may better refactor into an angular service? Somewhat copied from viewmetrics
        function displayBatchAsError() {
            $scope.currBatchState = 'Batch has expired';
            for (var i = 0; i < $scope.batches.length; i++) {
                if ($scope.batches[i].id == $scope.currBatchId) {
                    $scope.batches.splice(i, 1);
                    break;
                } 
            }
        }

        function displayBatchAsJson(batchData) {
            if (batchData.queries) {
                for (var i in batchData.queries) {
                    var query = batchData.queries[i];
                    if (query.result && query.result.datapoints && Object.keys(query.result.datapoints).length > 10) {
                        var firstTen = Object.keys(query.result.datapoints).slice(0, 10);
                        var numDatapointsLeft = Object.keys(query.result.datapoints).length - 10;
                        var truncatedDatapoints = {};
                        for (var j in firstTen) {
                            var time = firstTen[j];
                            truncatedDatapoints[time] = query.result.datapoints[time];
                        }
                        query.result.datapoints = truncatedDatapoints;
                        query.result.datapoints.and = numDatapointsLeft + ' more datapoints';
                    }
                }
            }
            $scope.currBatchState = angular.toJson(batchData, 2);
        }

        function displayBatchAsGraphs(batchData) {
            $('#graphs-container').empty();
            if (batchData.queries) {
                for (var i in batchData.queries) {
                    var metric = batchData.queries[i].result;
                    if (metric) {
                        var series = [];
                        for(var key in metric.datapoints) {
                            var timestamp = parseInt(key);
                            if(metric.datapoints[key]){
                                var value = parseFloat(metric.datapoints[key]);
                                series.push([timestamp, value]);
                            }
                        }
                        var id = createSeriesName(metric);
                        var options = angular.copy(BATCH_CHART_OPTIONS);
                        options.series = [{name: id, id: id, data: series, marker : {enabled : true, radius: 1}}];
                        options.title = {text: batchData.queries[i].expression};

                        var graphDiv = document.createElement('div');
                        $('#graphs-container').append(graphDiv);
                        $(graphDiv).highcharts('StockChart', options).css({"min-width": "310px", "height": "400px", "margin": "24px auto"});
                    }
                }
            }
        }

        function createSeriesName(metric) {
            var scope = metric.scope;
            var name = metric.metric;
            var tags = createTagString(metric.tags);
            return scope + ':' + name + tags;
        }

        function createTagString(tags) {
            var result = '';
            if (tags) {
                var tagString ='';
                for (var key in tags) {
                    if (tags.hasOwnProperty(key)) {
                        tagString += (key + '=' + tags[key] + ',');
                    }
                }
                if(tagString.length) {
                    result += '{';
                    result += tagString.substring(0, tagString.length - 1);
                    result += '}';
                }
            }
            return result;
        }
    }]);

/***/ }),
/* 7 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.controllers.dashboards', ['ngResource', 'ui.codemirror'])
.controller('Dashboards', ['Auth', '$scope', 'growl', 'Dashboards', '$sessionStorage', 'TableListService', function (Auth, $scope, growl, Dashboards, $sessionStorage, TableListService) {
    // scope variables
    $scope.colName = {
        id:'ID',
        name:'Name',
        description:'Description',
        createdDate:'Created',
        modifiedDate:'Last Modified',
        ownerName:'Owner'
    };
    $scope.properties = {
        title: "Dashboard",
        type: "dashboards"
    };
    $scope.tabNames = {
        firstTab: Auth.getUsername() + "'s Dashboards",
        secondTab: 'Shared Dashboards'
    };
    $scope.dashboards = [];
    $scope.dashboardsLoaded = false;

    // private variables
    var dashboardLists = {
        sharedList: [],
        usersList: []
    };
    var remoteUsername = Auth.getUsername();

    // TODO: refactor to DashboardService
    $scope.getDashboards = function (shared) {
        if ($scope.dashboardsLoaded) {
            $scope.dashboards = shared? dashboardLists.sharedList: dashboardLists.usersList;
        }
        $sessionStorage.dashboards.shared = shared;
    };

    function setDashboardsAfterLoading (dashboards, shared) {
      dashboardLists.sharedList = TableListService.getListUnderTab(dashboards, true, remoteUsername);
      dashboardLists.usersList = TableListService.getListUnderTab(dashboards, false, remoteUsername);
      $scope.dashboardsLoaded = true;
      $scope.getDashboards(shared);
    }

    function getNewDashboards () {
        Dashboards.getMeta().$promise.then(function(dashboards) {
            setDashboardsAfterLoading(dashboards, $scope.shared);
            $sessionStorage.dashboards.cachedData = dashboards;
        });
    }

    // TODO: refactor to DashboardService
    $scope.refreshDashboards = function () {
        delete $sessionStorage.dashboards.cachedData;
        delete $scope.dashboards;
        $scope.dashboardsLoaded = false;
        getNewDashboards();
    };

    // TODO: refactor to DashboardService
    $scope.addDashboard = function () {
        var dashboard = {
            name: 'new-dashboard-' + Date.now(),
            description: 'A new dashboard',
            shared: $scope.shared,
            content: getContentTemplate()
        };
        Dashboards.save(dashboard, function (result) {
            // update all dashboards
            result.content = "";
            dashboardLists = TableListService.addItemToTableList(dashboardLists, 'dashboards', result, remoteUsername);
            // update dashboards to be seen
            $scope.getDashboards($scope.shared);
            growl.success('Created "' + dashboard.name + '"');
        }, function (error) {
            growl.error('Failed to create "' + dashboard.name + '"');
        });
    };

    // TODO: refactor to DashboardService
    $scope.deleteDashboard = function (dashboard) {
        Dashboards.delete({dashboardId: dashboard.id}, function (result) {
            // update all dashboards
            dashboardLists = TableListService.deleteItemFromTableList(dashboardLists, 'dashboards', dashboard, remoteUsername);
            // update dashboards to be seen
            $scope.getDashboards($scope.shared);
            growl.success('Deleted "' + dashboard.name + '"');
        }, function (error) {
            growl.error('Failed to delete "' + dashboard.name + '"');
        });
    };

    // factor html template to /templates
    function getContentTemplate() {
        var template = "<!-- This is the root level tag. All dashboards must be encapsulated within this tag. -->\n<ag-dashboard>\n\n";

        template += "<!-- <ag-text> are filters used to refine a query. The values of these will be used by the <ag-metric> tag. You may define as many <ag-text> tags as the number of components you want to substitute in the argus query expression. A default value may be specified on each <ag-text> tag. The page will be loaded using these default values. -->\n";
        template += "<ag-date type='datetime' name='start' label='Start Date' default='-2d'></ag-date>\n";
        template += "<ag-date type='datetime' name='end' label='End Date' default='-0d'></ag-date>\n";
        template += "<ag-text type='text' name='scope' label='Scope' default='argus.jvm'></ag-text>\n";
        template += "<ag-text type='text' name='metric' label='Metric' default='mem.heap.used'></ag-text>\n";
        template += "<ag-text type='text' name='tags' label='Tags' default='host=*'></ag-text>\n";
        template += "<ag-text type='text' name='aggregator' label='Aggregator' default='avg'></ag-text>\n";
        template += "<!-- A button used to submit the query. -->\n";
        template += "<ag-submit>Submit</ag-submit>\n\n";

        template += "<!-- A dashboard template can also have arbitrary number of html tags. -->\n";
        template += "<h4>Argus mem heap used - Chart</h4>\n\n";

        template += "<!-- This defines a chart on the dashboard. A dashboard can also have tables which are defined using <ag-table> tag. This/these tags encapsulate all the options for the corresponsing tag as well as the actual metric/annotation data. -->\n";
        template += "<ag-chart name='Chart'>\n\n";

        template += "<!-- This defines options for a chart or a table. The value of 'name' attribute is directly used as the key for the config object(options object for highcharts/highstocks, config object for at-table. Hence use valid values for name attribute.). The values for the corresponding keys can either be provided using the value attribute on the tag or using innerHtml for the tag. -->\n";
        template += "<ag-option name='title.text' value='This title was set with a chart option'></ag-option>\n";
        template += "<!-- This defines each timeseries to be displayed on a chart/table. The timeseries to be displayed is specified as the innerHtml using the Argus Query Language. The individual component/s can be parameterized by placing them between $ signs and using the value of ag-text tag's name attribute. In the example below, all components have are parameterized. -->\n";
        template += "<ag-metric>$start$:$end$:$scope$:$metric${$tags$}:$aggregator$</ag-metric>\n";

        template += "</ag-chart>\n\n";
        template += "</ag-dashboard>";

        return template;
    }

    if ($sessionStorage.dashboards === undefined) $sessionStorage.dashboards = {};
    if ($sessionStorage.dashboards.cachedData !== undefined && $sessionStorage.dashboards.shared !== undefined) {
        // get data from cache if it exists initially
        var dashboards = $sessionStorage.dashboards.cachedData;
        setDashboardsAfterLoading(dashboards, $sessionStorage.dashboards.shared);
    } else {
        // trigger API call if there is no data in cache
        getNewDashboards();
    }

}]);


/***/ }),
/* 8 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.controllers.dashboards.detail', ['ngResource', 'ui.codemirror'])
.controller('DashboardsDetail', ['Storage', '$scope','$http', '$routeParams', '$location', 'growl', 'Dashboards', 'History','$sessionStorage', 'Auth',
    function (Storage, $scope,$http, $routeParams, $location, growl, Dashboards, History, $sessionStorage, Auth) {
        $scope.dashboardNotEditable = true;
        $scope.isDashboardDirty = function () {
            return !angular.equals($scope.dashboard, $scope.unmodifiedDashboard);
        };

        $scope.updateDashboard = function () {
            if ($scope.isDashboardDirty()) {
                var dashboard = $scope.dashboard;
                Dashboards.update({dashboardId: dashboard.id}, dashboard, function (result) {
                    $scope.unmodifiedDashboard = angular.copy(dashboard);
                    growl.success(('Updated "') + dashboard.name + '"');
                    $scope.fetchHistory();
                    // remove existing session storage for update
                    if ($sessionStorage.dashboards !== undefined) delete $sessionStorage.dashboards.cachedData;
                }, function (error) {
                    growl.error('Failed to update "' + dashboard.name + '"');
                });
            }
        };

        $scope.resetDashboard = function () {
            $scope.dashboard = angular.copy($scope.unmodifiedDashboard);
        };

        $scope.editorLoaded = function (editor) {
            editor.setSize(null, '30em');
        };

        $scope.isTabSelected = function (tab) {
            return $scope.selectedTab === tab;
        };

        $scope.selectTab = function (tab) {
            $scope.selectedTab = tab;
        };

        $scope.fetchHistory = function() {
            History.query({id: $scope.dashboardId}, function (history) {
                $scope.jobHistoryError='';
                $scope.history = history;
            }, function (error) {
                if(error.status==404){
                    $scope.jobHistoryError = 'No job history details found.';
                }else if(error.status==403){
                    $scope.jobHistoryError = error.statusText;
                    $scope.jobHistoryError = 'You are not authorized to view this data.';
                }
                else {
                    $scope.jobHistoryError = error.statusText;
                    growl.error('Failed to get history for job "' + $scope.jobId + '"');
                }
            });
        };

        $scope.dashboardId = $routeParams.dashboardId;
        $scope.selectedTab = 1;

        $scope.editorOptions = {
            lineWrapping: true,
            lineNumbers: true,
            mode: "htmlmixed",
            viewportMargin: 500
        };

        if ($scope.dashboardId > 0) {
            Dashboards.get({dashboardId: $scope.dashboardId}, function (dashboard) {
                $scope.dashboard = dashboard;
                $scope.dashboardNotEditable = Auth.isDisabled(dashboard);
                $scope.unmodifiedDashboard = angular.copy(dashboard);
            }, function (error) {
                growl.error('Failed to get dashboard "' + $scope.dashboardId + '"');
                $location.path('/dashboards');
            });
            $scope.fetchHistory();
        } else {
            growl.error('Failed to get dashboard "' + $routeParams.dashboardId + '"');
            $location.path('dashboards');
        }

        $scope.resetDashboard();
    }]);


/***/ }),
/* 9 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.controllers.login', [])
.controller('Login', ['$scope', function ($scope) {
    $scope.username = null;
    $scope.password = null;
}]);


/***/ }),
/* 10 */
/***/ (function(module, exports) {

angular.module('argus.controllers.main', [])
.controller('Main', ['$scope', '$location', 'Auth', '$sessionStorage', function ($scope, $location, Auth, $sessionStorage) {

    $scope.login = function (username, password) {
    	if(username.indexOf("@") != -1) {
            username = username.substring(0, username.indexOf("@"));
        }
        Auth.login(username, password);
    };

    $scope.currentUser = function () {
        return Auth.getUsername();
    };

    $scope.isLoggedIn = function () {
        return Auth.isLoggedIn();
    };

    $scope.isPrivileged = function () {
        return Auth.isPrivileged();
    };

    // delete session cache when the entire page is reloaded i.e. refresh the tab
    $sessionStorage.$reset();
}]);

/***/ }),
/* 11 */
/***/ (function(module, exports) {

angular.module('argus.controllers.metricelements', [])
.controller('metricElements', function($scope) {
    $scope.metricOptions = {};

    this.updateOption = function(name, value) {
        $scope.metricOptions[name] = value;
    };
});

/***/ }),
/* 12 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.controllers.namespace', ['ngResource'])
.controller('Namespace', ['Storage', '$scope', 'growl', 'Namespace', function (Storage, $scope, growl, Namespace) {
	
    $scope.searchText = Storage.get("namespace-searchText") == null ? "" : Storage.get("namespace-searchText");
	$scope.addnamespaceflag = false;
    $scope.namespaces = Namespace.query();
    
    $scope.updateNamespace = function(namespace) {
        //if ($scope.isAlertDirty()) { TODO: uncomment this
    	var userNames = namespace.usernames.toString();
    	namespace.usernames = (userNames && userNames.length > 0) ? userNames.split(',') : [];
    	
    		Namespace.update({namespaceId: namespace.id}, namespace, function (result) {
                growl.success(('Updated namespace "') + namespace.qualifier + '"');
                 
                for (var i=0; i < $scope.namespaces.length; i++){
                	var oldNamespace = $scope.namespaces[i];
                	if (oldNamespace.id == result.id){
                		$scope.namespaces[i] = result;
                		break;
                	}
                }
            }, function (error) {
                growl.error( true ? error.data.message : error.statusText);
            });
        //}
    };

    $scope.saveNamespace = function () {
    	var newNamespace = {};
    	var userNames = $scope.users;
    	 
    	newNamespace.qualifier = $scope.qulifier;
    	newNamespace.usernames = (userNames && userNames.length > 0) ? userNames.split(',') : [];
    	 
    	Namespace.save(newNamespace, function (result) {
    		$scope.namespaces.push(result);
    		$scope.qulifier = "";
    		$scope.users = "";
    		 
    		growl.success('Created namespace "' + newNamespace.qualifier + '"');
        }, function (error) {
            growl.error( true ? error.data.message : error.statusText);
        });
    };
    
    $scope.$watch('searchText', function(newValue, oldValue) {
    	newValue = newValue == null ? "" : newValue;
    	Storage.set("namespace-searchText", newValue);
    });
}]);


/***/ }),
/* 13 */
/***/ (function(module, exports) {

angular.module('argus.controllers.viewelements', [])
.controller('ViewElements', function($scope) {
    $scope.metrics = {};
    $scope.annotations = {};
    $scope.options = {};

    this.updateMetric = function(name, expression, metricSpecificOptions, seriesData) {
        var metric = {
            'name': seriesData.name,
            'color': seriesData.color,
            'expression': expression,
            'metricSpecificOptions': metricSpecificOptions
        };
    	$scope.metrics[name] = metric;
    };

    this.updateAnnotation = function(name, expression) {
        $scope.annotations[name] = expression;
    };

    this.updateOption = function(name, value) {
        $scope.options[name] = value;
    };
});

/***/ }),
/* 14 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.controllers.viewMetrics', ['ngResource'])
.controller('ViewMetrics', ['$location', '$routeParams', '$scope', 'growl', 'Metrics', 'Annotations', 'SearchService', 'Controls', 'ChartDataProcessingService', 'DateHandlerService', '$compile',
    function ($location, $routeParams, $scope, growl, Metrics, Annotations, SearchService, Controls, ChartDataProcessingService, DateHandlerService, $compile) {

        $('[data-toggle="tooltip"]').tooltip();
        $scope.expression = $routeParams.expression ? $routeParams.expression : null;

        // sub-views: (1) single chart, (2) metric discovery
        $scope.checkMetricExpression = function() {
            if ($scope.expression) {
                $scope.showMetricDiscovery = false;
                $scope.showChart = true;
            } else {
                $scope.showMetricDiscovery = true;
                $scope.showChart = false;
            }
        };
        $scope.checkMetricExpression();

        //sync the expression to URL param
        $scope.$watch('expression', function(val){
            // if val is empty, clear url string
            var urlStr = (val) ? Controls.getUrl([{name: 'expression', value: val}]) : '';
            $location.search(urlStr);
        });

        $scope.getMetricData = function () {
            var tempSeries = [];
            var annotationInfo = [];
            if ($scope.expression !== null && $scope.expression.length) {
                // clear old chart
                $("#" + "container").empty();
                $scope.checkMetricExpression();
                // show loading spinner
                $scope.chartLoaded = false;

                Metrics.query({expression: $scope.expression}, function (data) {
                    if (data && data.length > 0) {
                        tempSeries = ChartDataProcessingService.copySeriesDataNSetOptions(data, {});
                        for (var i = 0; i < data.length; i++) {
                            annotationInfo.push(ChartDataProcessingService.getAlertFlagExpression(data[i]));
                        }
                    } else {
                        tempSeries = [{
                            noData: true,
                            errorMessage: 'Empty result returned for the metric expression',
                            name: JSON.stringify($scope.expression).slice(1, -1),
                            color: 'Maroon'
                        }];
                    }
                    $scope.updateChart(tempSeries, annotationInfo, [$scope.expression]);
                    $scope.chartLoaded = true;
                }, function (error) {
                    // prevent error.data.message being null breaks the message
                    if (error.data.message === null) {
                        error.data.message = "Something was wrong. No info.";
                    } else {
                       growl.error(error.data.message, {referenceId: 'viewmetrics-error'});
                    }
                    tempSeries = [{
                        invalidMetric: true,
                        errorMessage: error.statusText + '(' + error.status + ') - ' + error.data.message.substring(0, 31),
                        name: error.config.params.expression,
                        color: 'Black'
                    }];
                    $scope.updateChart(tempSeries, annotationInfo, []);
                    $scope.chartLoaded = true;
                });
            } else {
                // empty expression
                $scope.checkMetricExpression();
                $scope.updateChart(tempSeries, annotationInfo, []);
                $scope.chartLoaded = true;
            }
        };

        $scope.searchMetrics = function(value, category) {
            // TODO: move param processing to search service
            var defaultParams = {
                namespace: '*',
                scope: '*',
                metric: '*',
                tagk: '*',
                tagv: '*',
                limit: 25,
                page: 1,
                type: 'scope'
            };

            var newParams = JSON.parse(JSON.stringify(defaultParams));

            // update params with values in $scope if they exist
            newParams['scope'] = ($scope.scope) ? $scope.scope : '*';
            newParams['metric'] = ($scope.metric) ? $scope.metric : '*';
            newParams['namespace'] = ($scope.namespace) ? $scope.namespace : '*';
            newParams['tagk'] = ($scope.tagk) ? $scope.tagk : '*';
            newParams['tagv'] = ($scope.tagv) ? $scope.tagv : '*';
            newParams['type'] = category ? category : 'scope';

            if(category) {
                if(category === 'scope') {
                    newParams['scope'] = newParams['scope'] + '*';
                } else if(category === 'metric') {
                    newParams['metric'] = newParams['metric'] + '*';
                } else if(category === 'tagk') {
                    newParams['limit'] = 10;
                    newParams['tagk'] = newParams['tagk'] + '*';
                } else if(category === 'tagv') {
                    newParams['tagv'] = newParams['tagv'] + '*';
                } else if(category === 'namespace') {
                    newParams['namespace'] = newParams['namespace'] + '*';
                }
            } else {
                newParams['scope'] = newParams['scope'] + '*';
            }
            // end TODO

            return SearchService.search(newParams)
                .then(function(response) {
                    return response.data;
                });
        };

        $scope.isSearchMetricDisabled = function () {
            var s = $scope.scope, m = $scope.metric;
            return (s === undefined || s.length < 1) && (m === undefined || m.length < 1);
        };

        // add search metrics to $scope expression
        $scope.addSearchExpression = function () {
            // set 'addDefaultValues' to false
            $scope.expression = constructSearchStr(false);
        };

        // construct & build a graph, with search values
        $scope.graphSearchExpression = function () {
            // set 'addDefaultValues' to true
            $scope.expression = constructSearchStr(true);

            // graph new epxression with default values
            $scope.getMetricData();
        };

        // TODO: create service for this form reset/clear
        $scope.setPristine = function () {
            $scope.scope = '';
            $scope.metric = '';
            $scope.metric = '';
            $scope.tagk = '';
            $scope.tagv = '';
            $scope.namespace = '';
            $scope.search_metrics.$setPristine();
        };

        // construct full search string from search fields
        function constructSearchStr(addDefaultValues) {
            var s = $scope.scope, m = $scope.metric, tagk = $scope.tagk, tagv = $scope.tagv, n = $scope.namespace;

            /* expression str format & rules:
                search fields:      scope:metric{tags}:aggregator
                expression field:   start*:end:scope*:metric*{tags}:aggregator*:downsampler:namespace
            **/
            var start_Str = '';
            var scope_Str = (s && s.length > 1) ? s + ':' : '';
            var metric_Str = (m && m.length > 1) ? m : '';

            var tag_Str = '';
            if (tagk && tagv) {
                tag_Str = '{' + tagk + '=' + tagv + "}";
                $scope.enterTagsErr = false;
            } else if ( (tagk && !tagv) || (!tagk && tagv) ) {
                // both tag key AND tag value input must be entered
                $scope.enterTagsErr = true;
                return null;
            }

            var agg_Str = '';
            var namespace_Str = (n && n.length > 1) ? ':' + n : '';

            /* Add default settings for: start, aggregator
                full:  -1h:scope:metric{tags}:avg:namespace
                start: -1h
                aggregator: avg
            **/
            if (addDefaultValues) {
                start_Str = "-1h:";
                agg_Str = ":avg";
            }

            return start_Str + scope_Str + metric_Str + tag_Str + agg_Str + namespace_Str;
        }

        // show newExpression in page view
        function showSearchExpression() {
            var searchStr = constructSearchStr();
            $("#searchExpression").html(searchStr);
        }

        // -------------

        $scope.updateChart = function (series, annotationInfo, expressions) {
            // if the metric expression is not empty
            if (series && series.length > 0) {
                var chartScope = $scope.$new(false);
                chartScope.chartConfig = {
                    chartId: 'container',
                    expressions: expressions
                };
                chartScope.dateConfig = {};
                chartScope.series = series;

                // all graph class name and sort sources alphabetically
                for (var i = 0; i < series.length; i++) {
                    chartScope.series[i].graphClassName = chartScope.chartConfig.chartId + "_graph" + (i + 1);
                }
                chartScope.series.sort(function(a, b) {
                    var textA = a.name.toUpperCase();
                    var textB = b.name.toUpperCase();
                    return (textA < textB) ? -1 : (textA > textB) ? 1 : 0;
                });

                // get start and end time info based on data range
                if (series[0].data && series[0].data.length > 0) {
                    chartScope.dateConfig.startTime = DateHandlerService.getStartTimestamp(series);
                    chartScope.dateConfig.endTime = DateHandlerService.getEndTimestamp(series);
                }
                chartScope.dateConfig.gmt = true;

                // query annotations
                if (annotationInfo.length > 0) {
                    var annotationCount = {};
                    annotationCount.tot = annotationInfo.length;
                    for (var i = 0; i < annotationInfo.length; i++) {
                        Annotations.query({expression: annotationInfo[i]}).$promise.then(function (data) {
                            //prevent empty annotation returns
                            if (data !== undefined && data.length !== 0) {
                                var flagSeries = ChartDataProcessingService.copyFlagSeries(data);
                                if (flagSeries === null || flagSeries === undefined) return;
                                flagSeries.linkedTo = ChartDataProcessingService.createSeriesName(data[0]);
                                chartScope.series = chartScope.series.map(function (item) {
                                    if (item.name === flagSeries.linkedTo) item.flagSeries = flagSeries;
                                    return item;
                                });
                            }
                            annotationCount.tot--;
                            if (annotationCount.tot == 0) {
                                angular.element("#" + "container").append($compile('<line-chart chartConfig="chartConfig" series="series" dateconfig="dateConfig"></line-chart>')(chartScope));
                            }
                        }, function (error) {
                            console.log('no annotation found;', error.statusText);
                            annotationCount.tot--;
                            if (annotationCount.tot == 0) {
                                angular.element("#" + "container").append($compile('<line-chart chartConfig="chartConfig" series="series" dateconfig="dateConfig"></line-chart>')(chartScope));
                            }
                        })
                    }
                } else {
                    angular.element("#" + "container").append( $compile('<line-chart chartConfig="chartConfig" series="series" dateconfig="dateConfig"></line-chart>')(chartScope) );
                }
            }

            $scope.series = series;
        };

        $scope.getMetricData(null);
    }]);


/***/ }),
/* 15 */
/***/ (function(module, exports) {

/**
 * the HTML5 autofocus property can be finicky when it comes to dynamically loaded
 * templates and such with AngularJS. Use this simple directive to
 * tame this beast once and for all.
 *
 * Usage:
 * <input type="text" autoFocus>
 * 
 * License: MIT
 */
angular.module('argus.directives')
.directive('autoFocus', ['$timeout', '$exceptionHandler', function($timeout, $exceptionHandler) {
  return {
    restrict: 'A',
    link : function($scope, $element) {
      $timeout(function() {
        $element[0].focus();
      });
    }
  }
}]);

/***/ }),
/* 16 */
/***/ (function(module, exports) {

angular.module('argus.directives.confirm', [])
.directive('ngConfirm', [function () {
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

/***/ }),
/* 17 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/**
 * Created by liuxizi.xu on 9/6/16.
 */


angular.module('argus.directives')
.directive('ngEnter', function () {
    return function (scope, element, attrs) {
        element.bind("keydown keypress", function (event) {
            if(event.which === 13) {
                scope.$apply(function (){
                    scope.$eval(attrs.ngEnter);
                });

                event.preventDefault();
            }
        });
    };
});

/***/ }),
/* 18 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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


angular.module('argus.directives')
.directive('ngLoading', function ($compile) {
    // get the spinning icon
    var loadingSpinner = '<div class="loadingSpinner"></div>';
    return {
        restrict: 'A',
        link: function (scope, element, attrs) {
            var originalContent = element.html();
            element.html(loadingSpinner);
            scope.$watch(attrs.ngLoading, function (val) {
                if(val) {
                    element.html(originalContent);
                    $compile(element.contents())(scope);
                } else {
                    element.html(loadingSpinner);
                }
            });
        }
    };
});

/***/ }),
/* 19 */
/***/ (function(module, exports) {

angular.module('argus.directives')
.directive('stopEvent', function () {
    return {
        restrict: 'A',
        link: function (scope, element, attr) {
            element.bind('click', function (e) {
                e.stopPropagation();
            });
        }
    };
});

/***/ }),
/* 20 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.directives.breadcrumbs', [])
.directive('breadcrumbsHtml', function() {
    return {
        restrict: 'E',
        templateUrl: 'js/templates/breadcrumbs.html',
        scope: {},
        controller: ['$scope', 'breadcrumbs', function ($scope, breadcrumbs) {
            $scope.breadcrumbs = breadcrumbs;
        }],
        link: function(scope, element, attribute) {
        }
    };
});


/***/ }),
/* 21 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.directives.charts.chart', [])
.directive('agChart', ['Metrics', 'Annotations', 'ChartRenderingService', 'ChartDataProcessingService', 'ChartOptionService', 'DateHandlerService', 'CONFIG', 'VIEWELEMENT', '$compile',
function(Metrics, Annotations, ChartRenderingService, ChartDataProcessingService, ChartOptionService, DateHandlerService, CONFIG, VIEWELEMENT, $compile) {
    var chartNameIndex = 1;
    function compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList) {
        // empty any previous content
        $("#" + newChartId).empty();

        // create a new scope to pass to compiled line-chart directive
        var lineChartScope = scope.$new(false);     // true will set isolate scope, false = inherit

        // add more options in addition to 'ag-options'
        lineChartScope.chartConfig = updatedOptionList;
        lineChartScope.chartConfig.chartId = newChartId;
        lineChartScope.chartConfig.smallChart = scope.chartOptions ? scope.chartOptions.smallChart : undefined;

        lineChartScope.series = series;
        // when there is no agDate
        if (dateConfig.startTime == undefined || dateConfig.endTime == undefined) {
            if (series[0].data && series[0].data.length > 0) {
                dateConfig.startTime = DateHandlerService.getStartTimestamp(series);
                dateConfig.endTime = DateHandlerService.getEndTimestamp(series);
            }
        }
        lineChartScope.dateConfig = dateConfig;

        // give each series an unique ID if it has data
        for (var i = 0; i < series.length; i++) {
            // use graphClassName to bind all the graph element of a metric together
            lineChartScope.series[i].graphClassName = newChartId + "_graph" + (i + 1);
        }
        // sort series alphabetically
        lineChartScope.series = lineChartScope.series.sort(function(a, b) {
            var textA = a.name.toUpperCase();
            var textB = b.name.toUpperCase();
            return (textA < textB) ? -1 : (textA > textB) ? 1 : 0;
        });

        scope.seriesDataLoaded = true;

        // append, compile, & attach new scope to line-chart directive
        angular.element("#" + newChartId).append( $compile('<line-chart chartConfig="chartConfig" series="series" dateconfig="dateConfig"></line-chart>')(lineChartScope) );
    }

    function queryAnnotationData(scope, annotationItem, newChartId, series, dateConfig, updatedOptionList) {
        Annotations.query({expression: annotationItem}).$promise.then(function(data) {
            if (data && data.length > 0) {
                var forName = ChartDataProcessingService.createSeriesName(data[0]);
                var flagSeries = ChartDataProcessingService.copyFlagSeries(data);
                flagSeries.linkedTo = forName;

                // add flagSeries if any data exists
                series[0].flagSeries = (flagSeries) ? flagSeries: null;
            }

            // append, compile, & attach new scope to line-chart directive
            compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList);

        }, function (error) {
            console.log( 'no data found', error.data.message );
            // append, compile, & attach new scope to line-chart directive
            compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList);
        });
    }

    function setupAnnotations(scope, newChartId, series, updatedAnnotationList, dateConfig, updatedOptionList) {

        if (updatedAnnotationList.length === 0) {
            // no annotations list, continue to render chart as normal
            compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList);
        } else {
            // check annotations & add to series data for line-chart
            for (var i=0; i < updatedAnnotationList.length; i++) {
                var annotationItem = updatedAnnotationList[i];
                queryAnnotationData(scope, annotationItem, newChartId, series, dateConfig, updatedOptionList);
            }
        }
    }

    function queryMetricData(scope, metricItem, metricCount, newChartId, series, updatedAnnotationList, dateConfig, attributes, updatedOptionList) {
        if (!metricItem) return;

        var smallChart = !!attributes.smallchart;

        Metrics.query({expression: metricItem.expression}).$promise.then(function(data) {
            var tempSeries;

            if (data && data.length > 0) {
                // check to update statusIndicator with correct status color
                if (smallChart) {
                    // get the last data point
                    var lastStatusVal = ChartDataProcessingService.getLastDataPoint(data[0].datapoints);

                    // update status indicator
                    ChartRenderingService.updateIndicatorStatus(attributes, lastStatusVal);

                    // add 'smallChart' flag to scope
                    scope.chartOptions = {smallChart: smallChart};
                }
                // metric item attributes are assigned to the data (i.e. name, color, etc.)
                tempSeries = ChartDataProcessingService.copySeriesDataNSetOptions(data, metricItem);
                // keep metric expression info if the query succeeded
                metricCount.expressions.push(metricItem.expression);
            } else {
                // growl.info('No data found for the metric expression: ' + JSON.stringify(metricItem.expression));
                console.log('Empty result returned for the metric expression');
                tempSeries = [{
                    noData: true,
                    errorMessage: 'Empty result returned for the metric expression',
                    name: JSON.stringify(metricItem.expression).slice(1, -1),
                    color: 'Maroon'
                }];
            }

            Array.prototype.push.apply(series, tempSeries);
            // decrement metric count each time an expression is added to the series.
            metricCount.tot -= 1;
            if (metricCount.tot === 0) {
                // pass in metric expression in as chartConfig
                updatedOptionList.expressions = metricCount.expressions;
                // check for Annotations
                setupAnnotations(scope, newChartId, series, updatedAnnotationList, dateConfig, updatedOptionList);
            }
        }, function (error) {
            // growl.error(error.message);
            console.log('Metric expression does not exist in database');
            var tempSeries = [{
                invalidMetric: true,
                errorMessage: error.statusText + '(' + error.status + ') - ' + error.data.message.substring(0, 31),
                name: error.config.params.expression,
                color: 'Black'
            }];
            Array.prototype.push.apply(series, tempSeries);

            metricCount.tot -= 1;
            if (metricCount.tot === 0) {
                // display chart with series data and populate annotations
                setupAnnotations(scope, newChartId, series, updatedAnnotationList, dateConfig, updatedOptionList);
            }
        });
    }

    // TODO: below functions 'should' be refactored to the chart services.
    function setupChart(scope, element, attributes, controls) {
        // remove/clear any previous chart rendering from DOM
        element.empty();
        // generate a new chart ID, set css options for main chart container
        var newChartId = 'element_' + VIEWELEMENT.chart + chartNameIndex++;
        var chartType = attributes.type ? attributes.type : 'LINE';
        var cssOpts = ( attributes.smallchart ) ? 'smallChart' : '';

        // set the charts container for rendering
        ChartRenderingService.setChartContainer(element, newChartId, cssOpts);

        var data = {
            metrics: scope.metrics,
            annotations: scope.annotations,
            options: scope.options
        };

        // get start and end time for the charts as well as whether GMT/UTC scale is used or not
        var dateConfig = {};
        var GMTon = false;
        for (var i = 0; i < controls.length; i++) {
            if (controls[i].type === "agDate") {
                var timeValue = controls[i].value;
                if (controls[i].name === "start") {
                    dateConfig.startTime = DateHandlerService.timeProcessingHelper(timeValue);
                    GMTon = GMTon || DateHandlerService.GMTVerifier(timeValue);
                } else if (controls[i].name === "end"){
                    dateConfig.endTime = DateHandlerService.timeProcessingHelper(timeValue);
                    GMTon = GMTon || DateHandlerService.GMTVerifier(timeValue);
                }
            }
        }
        dateConfig.gmt = GMTon;

        // process data for: metrics, annotations, options
        var processedData = ChartDataProcessingService.processMetricData(data, controls);

        if (!processedData) {
            console.log('no processed data returned: ' + newChartId);
            return;
        }

        // re-assign each list for: metrics, annotations, options
        var updatedMetricList = processedData.updatedMetricList;
        var updatedAnnotationList = processedData.updatedAnnotationList;
        var updatedOptionList = processedData.updatedOptionList;

        // define series first, then build list for each metric expression
        var series = [];
        var metricCount = {};
        metricCount.tot = updatedMetricList.length;
        metricCount.expressions = [];

        scope.seriesDataLoaded = false; //used for load spinner
        angular.element("#" + newChartId).append( $compile('<div ng-loading="seriesDataLoaded"></div>')(scope) );

        for (var i = 0; i < updatedMetricList.length; i++) {
            var metricItem = updatedMetricList[i];
            // get data for each metric item, bind optional data with metric data
            queryMetricData(scope, metricItem, metricCount, newChartId, series, updatedAnnotationList, dateConfig, attributes, updatedOptionList);
        }
    }

    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""></div>',
        compile: function (element, attrs, transclude) {
            return {
                post: function postLink(scope, element, attributes, dashboardCtrl, transcludeFn) {
                    scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
                        setupChart(scope, element, attributes, controls);
                    });
                }
            };
        }
    };
}]);

/***/ }),
/* 22 */
/***/ (function(module, exports) {

/* currently not being used? */

angular.module('argus.directives.charts.flags', [])
.directive('agFlags', function() {
    var flagNameIndex = 100;
    return {
        restrict: 'E',
        require: '^agChart',
        scope: {
            expression: '@'
        },
        template: '',
        link: function(scope, element, attributes, chartCtrl) {
            var flagName = 'flag_' + flagNameIndex++;

            if (element.text() && element.text().length > 0) {
                chartCtrl.updateAnnotation(flagName, element.text().replace(/(\r\n|\n|\r|\s+)/gm,""));
            }

            scope.$watch('expression', function(newValue, oldValue) {
                if (newValue) {
                    chartCtrl.updateAnnotation(flagName, newValue);
                }
            });

            element.html('<span> </span>');
        }
    }
});

/***/ }),
/* 23 */
/***/ (function(module, exports) {

angular.module('argus.directives.charts.heatmap', [])
.directive('agHeatmap', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var heatmapNameIndex = 1;
    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template:'<div ng-transclude=""> </div>',
        link: function(scope, element, attributes, dashboardCtrl) {
            DashboardService.buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.heatmap, heatmapNameIndex++, DashboardService);
        }
    }
}]);

/***/ }),
/* 24 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.directives.charts.lineChart', [])
.directive('lineChart', ['$timeout', 'Storage', '$routeParams', function($timeout, Storage, $routeParams) {
    var resizeTimeout = 250; //the time for resize function to fire
    var resizeJobs = [];
    var timer;
    var lineChartIdIndex = 0;
    var lineChartIdName = 'linechart_'; // in case there are other kind of ag-chart on the page

    function resizeHelper(){
        $timeout.cancel(timer); //clear to improve performance
        timer = $timeout(function () {
            resizeJobs.forEach(function (resizeJob) { //resize all the charts
                resizeJob();
            });
        }, resizeTimeout); //only execute resize after a timeout
    }

    d3.select(window).on('resize', resizeHelper);

    return {
        restrict: 'E',
        replace: true,
        scope: {
            chartConfig: '=chartconfig',
            series: '=series',
            dateConfig: '=dateconfig'
        },
        templateUrl: 'js/templates/charts/topToolbar.html',
        controller: ['$scope', 'Metrics', 'DownloadHelper', 'growl', function($scope, Metrics, DownloadHelper, growl) {
            $scope.downloadData = function (queryFunction) {
                // each metric expression will be a separate file
                $scope.chartConfig.expressions.map(function (expression) {
                    //TODO: need to have better way to name the downloaded file instead just "data.*"
                    var dataHandler, filename;
                    switch (queryFunction) {
                        case "query":
                            dataHandler = function (data) { return JSON.stringify(data.slice(0, data.length)); };
                            filename = "data.json";
                            break;
                        case "downloadCSV":
                            dataHandler = function (data) { return data[0]; };
                            filename = "data.csv";
                            break;
                    }
                    growl.info("Downloading data...");
                    Metrics[queryFunction]({expression: expression}).$promise.then(function (data) {
                        DownloadHelper.downloadFile(dataHandler(data), filename);
                    }, function (error) {
                        growl.error("Data cannot be download this time");
                        console.log(error);
                    });
                });

            };

            $scope.sources = [];
            $scope.otherSourcesHidden = false;
            // can be used for future modal window
            $scope.noDataSeries = [];
            $scope.invalidSeries = [];

            $scope.toggleSource = function(source) {
                toggleGraphOnOff(source);
            };

            // show ONLY this 1 source, hide all others
            $scope.hideOtherSources = function(sourceToShow) {
                var sources = $scope.sources;
                for (var i = 0; i < sources.length; i++) {
                    if (sourceToShow.name !== sources[i].name) {
                        toggleGraphOnOff(sources[i]);
                    }
                }
                $scope.otherSourcesHidden = !$scope.otherSourcesHidden;
            };

            $scope.labelTextColor = function(source) {
                return source.displaying? source.color: '#FFF';
            };

            function toggleGraphOnOff(source) {
                // d3 select with dot in ID name: http://stackoverflow.com/questions/33502614/d3-how-to-select-element-by-id-when-there-is-a-dot-in-id
                // var graphID = source.name.replace(/\s+/g, '');
                var displayProperty = source.displaying? 'none' : null;
                source.displaying = !source.displaying;
                d3.selectAll("." + source.graphClassName)
                    .style('display', displayProperty);
                $scope.reScaleY();
                $scope.redraw();
            }
        }],
        // compile: function (iElement, iAttrs, transclude) {},
        link: function (scope, element, attributes) {
            scope.lineChartId = ++lineChartIdIndex;
            /**
             * not using chartId because when reload the chart by 'sumbit' button
             * or other single page app navigate button the chartId is not reset
             * to 1, only by refreshing the page would the chartId be reset to 0
             */

            var chartId = scope.chartConfig.chartId;
            var series = scope.series;
            var startTime = scope.dateConfig.startTime;
            var endTime = scope.dateConfig.endTime;
            var GMTon = scope.dateConfig.gmt;
            var chartOptions = scope.chartConfig;

            /** 'smallChart' settings:
                height: 150
                no timeline, date range, option menu
                only left-side Y axis
                fewer x-axis tick labels
            */

            var agYMin, agYMax;
            //provide support for yaxis lower case situation.
            if(chartOptions.yAxis){
                agYMin = chartOptions.yAxis.min;
                agYMax = chartOptions.yAxis.max;
            }
            if(chartOptions.yaxis){
                agYMin = agYMin || chartOptions.yaxis.min;
                agYMax = agYMax || chartOptions.yaxis.max;
            }

            if (isNaN(agYMin)) agYMin = undefined;
            if (isNaN(agYMax)) agYMax = undefined;

            // set $scope values, get them from the local storage
            scope.menuOption = {
                isWheelOn : false,
                isBrushOn : !chartOptions.smallChart,
                isBrushMainOn : false,
                isTooltipOn : true,
                isTooltipSortOn: false,
                isTooltipDetailOn: false
            };
            scope.hideMenu = false;

            scope.dashboardId = $routeParams.dashboardId;

            var menuOption = Storage.get('menuOption_' + scope.dashboardId +'_' + lineChartIdName + scope.lineChartId);
            if (menuOption){
                scope.menuOption = menuOption;
            }

            var dateExtent; //extent of non empty data date range
            // ---------
            var topToolbar = $(element); //jquery selection
            var container = topToolbar.parent()[0];//real DOM

            var maxScaleExtent = 100; //zoom in extent
            var currSeries = series;

            // Layout parameters
            var containerHeight = chartOptions.smallChart ? 150 : 330;
            var containerWidth = $("#" + chartId).width();

            if (chartOptions.chart !== undefined) {
                containerHeight = chartOptions.chart.height === undefined ? containerHeight: chartOptions.chart.height;
                containerWidth = chartOptions.chart.width === undefined ? containerWidth: chartOptions.chart.width;
            }
            var xAxisLabelHeightFactor = 15;
            var brushHeightFactor = 20;
            var mainChartRatio = 0.8, //ratio of height
                tipBoxRatio = 0.2,
                brushChartRatio = 0.2
                ;
            var marginTop = chartOptions.smallChart ? 5 : 15,
                marginBottom = 35,
                marginLeft = 50,
                marginRight = chartOptions.smallChart ? 5 : 60;

            var width = containerWidth - marginLeft - marginRight;
            var height = parseInt((containerHeight - marginTop - marginBottom) * mainChartRatio);
            var height2 = parseInt((containerHeight - marginTop - marginBottom) * brushChartRatio) - brushHeightFactor;

            var margin = {
                top: marginTop,
                right: marginRight,
                bottom: containerHeight - marginTop - height,
                left: marginLeft
            };

            var margin2 = {
                top: containerHeight - height2 - marginBottom,
                right: marginRight,
                bottom: marginBottom,
                left: marginLeft
            };

            var tipPadding = 3;
            var tipOffset = 8;
            var circleRadius = 4.5;

            var crossLineTipWidth = 35;
            var crossLineTipHeight = 15;
            var crossLineTipPadding = 3;

            var bufferRatio = 0.2; //the ratio of buffer above/below max/min on yAxis for better showing experience

            // Local helpers
            // date formats
            // https://github.com/d3/d3-time-format/blob/master/README.md#timeFormat
            var longDate = '%A, %b %e, %H:%M';      // Saturday, Nov 5, 11:58
            var shortDate = '%b %e, %H:%M';
            var numericalDate = '%-m/%-d/%y %H:%M:%S';   // %x = %m/%d/%Y  11/5/2016
            var smallChartDate = '%x';

            var bisectDate = d3.bisector(function (d) {
                return d[0];
            }).left;
            var formatDate = chartOptions.smallChart ? d3.timeFormat(smallChartDate) : d3.timeFormat(shortDate);
            var GMTformatDate = chartOptions.smallChart ? d3.utcFormat(smallChartDate) : d3.utcFormat(numericalDate);

            var formatValue = d3.format(',');

            //graph setup variables
            var x, x2, y, y2,
                nGridX = chartOptions.smallChart ? 3 : 7,
                nGridY = chartOptions.smallChart ? 3 : 5,
                xAxis, xAxis2, yAxis, yAxisR, xGrid, yGrid,
                line, line2, area, area2,
                brush, brushMain, zoom,
                svg, svg_g, mainChart, xAxisG, xAxisG2, yAxisG, yAxisRG, xGridG, yGridG, //g
                focus, context, clip, brushG, brushMainG, chartRect, flags,//g
                tip, tipBox, tipItems,
                crossLine,
                names, colors, graphClassNames,
                flagsG, labelTip, label,
                yAxisPadding = 1;

            var messageToDisplay = ['No graph available'];

            // color scheme
            var z = d3.scaleOrdinal(d3.schemeCategory20);

            // Base graph setup, initialize all the graph variables
            function setGraph() {
                // use different x axis scale based on timezone
                if (GMTon) {
                    x = d3.scaleUtc().domain([startTime, endTime]).range([0, width]);
                    x2 = d3.scaleUtc().domain([startTime, endTime]).range([0, width]); //for brush
                } else {
                    x = d3.scaleTime().domain([startTime, endTime]).range([0, width]);
                    x2 = d3.scaleTime().domain([startTime, endTime]).range([0, width]); //for brush
                }

                y = d3.scaleLinear().range([height, 0]);
                y2 = d3.scaleLinear().range([height2, 0]);

                //Axis
                xAxis = d3.axisBottom()
                    .scale(x)
                    .ticks(nGridX)
                ;

                xAxis2 = d3.axisBottom() //for brush
                    .scale(x2)
                    .ticks(nGridX)
                ;

                yAxis = d3.axisLeft()
                    .scale(y)
                    .ticks(nGridY)
                    .tickFormat(d3.format('.2s'))
                ;

                yAxisR = d3.axisRight()
                    .scale(y)
                    .ticks(nGridY)
                    .tickFormat(d3.format('.2s'))
                ;

                //grid
                xGrid = d3.axisBottom()
                    .scale(x)
                    .ticks(nGridX)
                    .tickSizeInner(-height)
                ;

                yGrid = d3.axisLeft()
                    .scale(y)
                    .ticks(nGridY)
                    .tickSizeInner(-width)
                ;

                //line
                line = d3.line()
                    .x(function (d) {
                        return x(d[0]);
                    })
                    .y(function (d) {
                        return y(d[1]);
                    });

                //line2 (for brush area)
                line2 = d3.line()
                    .x(function (d) {
                        return x2(d[0]);
                    })
                    .y(function (d) {
                        return y2(d[1]);
                    });

                //brush
                brush = d3.brushX()
                    .extent([[0, 0], [width, height2]])
                    .on("brush end", brushed);

                brushMain = d3.brushX()
                    .extent([[0, 0], [width, height]])
                    .on("end", brushedMain);

                //zoom
                zoom = d3.zoom()
                    .scaleExtent([1, Infinity])
                    .translateExtent([[0, 0], [width, height]])
                    .extent([[0, 0], [width, height]])
                    .on("zoom", zoomed)
                    .on("start", function () {
                        mainChart.select(".chartOverlay").style("cursor", "move");
                    })
                    .on("end", function () {
                        mainChart.select(".chartOverlay").style("cursor", "crosshair");
                    })
                ;

                //Add elements to SVG
                svg = d3.select(container).append('svg')
                    .attr('width', width + margin.left + margin.right)
                    .attr('height', height + margin.top + margin.bottom);

                svg_g = svg
                    .append('g')
                    .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

                mainChart = svg_g.append("g");

                xAxisG = mainChart.append('g')
                    .attr('class', 'x axis')
                    .attr('transform', 'translate(0,' + height + ')')
                    .call(xAxis);

                yAxisG = mainChart.append('g')
                    .attr('class', 'y axis')
                    .call(yAxis);

                // add axis label if they are in ag options
                if (chartOptions.xAxis!== undefined && chartOptions.xAxis.title !== undefined) {
                    mainChart.append("text")
                              .attr("class", "xAxisLabel")
                              .attr("transform", "translate(" + (width / 2) + " ," + (height + margin.top + xAxisLabelHeightFactor) + ")")
                              .style("text-anchor", "middle")
                              .style("font-size", 12)
                              .text(chartOptions.xAxis.title.text);
                }
                if (chartOptions.yAxis!== undefined && chartOptions.yAxis.title !== undefined) {
                    mainChart.append("text")
                              .attr("class", "yAxisLabel")
                              .attr("transform", "rotate(-90)")
                              .attr("y", 0 - margin.left)
                              .attr("x",0 - (height / 2))
                              .attr("dy", "1em")
                              .style("text-anchor", "middle")
                              .style("font-size", 12)
                              .text(chartOptions.yAxis.title.text);
                }

                yAxisRG = mainChart.append('g')
                    .attr('class', 'y axis')
                    .attr('transform', 'translate(' + width + ')')
                    .call(yAxisR);

                xGridG = mainChart.append('g')
                    .attr('class', 'x grid')
                    .attr('transform', 'translate(0,' + height + ')')
                    .call(xGrid);

                yGridG = mainChart.append('g')
                    .attr('class', 'y grid')
                    .call(yGrid);

                //Brush, zoom, pan
                //clip path
                clip = svg_g.append("defs").append("clipPath")
                    .attr('name','clip')
                    .attr("id", "clip_" + chartId)
                    .append("rect")
                    .attr("width", width)
                    .attr("height", height);

                //brush area
                context = svg_g.append("g")
                    .attr("class", "context")
                    // .attr("transform", "translate(0," + (height + margin.top + 10) + ")");
                    .attr("transform", "translate(0," + margin2.top + ")");

                // flags (annotations)
                flags = svg_g.append("g").attr("class", "flags");

                //set brush area axis
                xAxisG2 = context.append("g")
                    .attr("class", "xBrush axis")
                    .attr("transform", "translate(0," + height2 + ")")
                    .call(xAxis2)
                ;

                // Mouseover/tooltip setup
                focus = mainChart.append('g')
                    .attr('class', 'focus')
                    .style('display', 'none');
                tip = svg_g.append('g')
                    .attr('class', 'tooltip')
                    .style('opacity', 1)
                    .style('display', 'none');
                tipBox = tip.append('rect')
                    .attr('rx', tipPadding)
                    .attr('ry', tipPadding);
                tipItems = tip.append('g')
                    .attr('class', 'tooltip-items');

                //focus tracking/crossLine
                crossLine = focus.append('g')
                    .attr('name', 'crossLine');
                crossLine.append('line')
                    .attr('name', 'crossLineX')
                    .attr('class', 'crossLine');
                crossLine.append('line')
                    .attr('name', 'crossLineY')
                    .attr('class', 'crossLine');

                //tooltip label on axis background rect
                crossLine.append('rect')
                    .attr('name', 'crossLineTipRectX')
                    .attr('class', 'crossLineTipRect');
                crossLine.append('rect')
                    .attr('name', 'crossLineTipRectY')
                    .attr('class', 'crossLineTipRect');
                //tooltip label on axis text
                crossLine.append('text')
                    .attr('name', 'crossLineTipY')
                    .attr('class', 'crossLineTip');
                crossLine.append('text')
                    .attr('name', 'crossLineTipX')
                    .attr('class', 'crossLineTip');

                //annotations
                flagsG = d3.select('#' + chartId).select('svg').select('.flags');
                labelTip = d3.tip().attr('class', 'd3-tip').offset([-10, 0]);
                d3.select('#' + chartId).select('svg').call(labelTip);
            }

            // Graph tools that only needs to be created once in theory; all of these are data independent
            function setGraphTools(series) {
                // set z to metric names and set legend content
                z.domain(names);
                // create mouse over circle, tooltip items, lines and brush lines
                series.forEach(function (metric) {
                    if (metric.data.length === 0) return;
                    var tempColor = metric.color === null ? z(metric.name) : metric.color;
                    // main graphs
                    mainChart.append('path')
                      .attr('class', 'line ' + metric.graphClassName)
                      .style('stroke', tempColor)
                      .style('clip-path', "url('#clip_" + chartId + "')");
                    // graphs in the brush
                    context.append('path')
                        .attr('class', 'brushLine ' + metric.graphClassName + '_brushline')
                        .style('stroke', tempColor);
                    // circle on graph during mouse over
                    focus.append('circle')
                        .attr('r', circleRadius)
                        .attr('fill', tempColor)
                        .attr('class', metric.graphClassName);
                    // tooltip items
                    tipItems.append('circle')
                        .attr('r', circleRadius)
                        .attr('fill', tempColor)
                        .attr('class', metric.graphClassName);
                    tipItems.append('text')
                        .attr('class', metric.graphClassName);
                    // annotations
                    if (!metric.flagSeries) return;
                    var flagSeries = metric.flagSeries.data;
                    flagSeries.forEach(function (d) {
                        var label = flagsG.append('g')
                            .attr("class", "flagItem " + metric.graphClassName)
                            .attr("id", metric.graphClassName + d.flagID)
                            .style("stroke", tempColor)
                            .on("mouseover", function() {
                                // add timestamp to the annotation label
                                var tempTimestamp = GMTon ? GMTformatDate(d.x) : formatDate(d.x);
                                tempTimestamp =  "<strong>" + tempTimestamp + "</strong><br/>" + d.text;
                                labelTip.style("border-color", tempColor).html(tempTimestamp);
                                labelTip.show();
                                // prevent annotation label goes outside of the view on the  side
                                if (parseInt(labelTip.style("left")) < 15) labelTip.style("left", "15px");
                            })
                            .on("mouseout", labelTip.hide);
                        label.append("line")
                            .attr("y2", 35)
                            .attr("stroke-width", 2);
                        label.append("circle")
                            .attr("r", 8)
                            .attr("class", "flag");
                        label.append("text")
                            .attr('dy', 4)
                            .style("text-anchor", "middle")
                            .style("stroke", "black")
                            .text(d.title);
                    })
                });
            }

            function mouseMove() {
                if (!currSeries || currSeries.length === 0) return;
                var datapoints = [];
                var position = d3.mouse(this);
                var positionX = position[0];
                var positionY = position[1];
                var mouseX = x.invert(positionX);
                var mouseY = y.invert(positionY);

                if(isBrushInNonEmptyRange()) {
                    currSeries.forEach(function (metric) {
                        if (metric.data.length === 0) return;
                        var data = metric.data;
                        var i = bisectDate(data, mouseX, 1);
                        var d0 = data[i - 1];
                        var d1 = data[i];
                        var d;
                        // snap the datapoint that lives in the x domain
                        if (!d0) {
                            //There is a case when d0 is outside domain but d1 is undefined, we cannot render d1
                            //we could still render d0 but make it invisible.
                            d = d1;
                        } else if (!d1) {
                            d = d0;
                            // if both data points lives in the domain, choose the closer one to the mouse position
                        } else {
                            d = mouseX - d0[0] > d1[0] - mouseX ? d1 : d0;
                        }

                        var circle = focus.select('.' + metric.graphClassName);

                        if(d[0] < x.domain()[0] || d[0] > x.domain()[1].getTime() ||d[1] < y.domain()[0] || d[1] > y.domain()[1]){
                            //outside domain
                            circle.attr('display', 'none');
                        }else{
                            circle.attr('display', null);
                        }

                        // update circle's position on each graph
                        circle
                            .attr('dataX', d[0]).attr('dataY', d[1]) //store the data
                            .attr('transform', 'translate(' + x(d[0]) + ',' + y(d[1]) + ')');

                        // check if the source is displaying based on the legend
                        var sourceInLegend = scope.sources.find(function (source) {
                            return source.graphClassName === metric.graphClassName;
                        });
                        if (sourceInLegend.displaying) {
                            datapoints.push({
                                data: d,
                                graphClassName: metric.graphClassName,
                                name: metric.name
                            });
                        }
                    });
                    // sort items in tooltip if needed
                    if (scope.menuOption.isTooltipSortOn) {
                        datapoints = datapoints.sort(function (a, b) {
                            return b.data[1] - a.data[1]
                        });
                    }

                    toolTipUpdate(tipItems, datapoints, positionX, positionY);
                }
                updateCrossLine(mouseX, mouseY, positionX, positionY);
            }

            function toolTipUpdate(group, datapoints, X, Y) {
                var XOffset = 0;
                var YOffset = 0;
                var newXOffset = 0;
                var OffsetMultiplier = -1;
                var itemsPerCol = 8;
                var circleLen = circleRadius * 2;
                if (scope.menuOption.isTooltipDetailOn) {
                    itemsPerCol = 14;
                } else if (datapoints.length < 2*itemsPerCol) {
                    itemsPerCol = Math.ceil(datapoints.length / 2);
                }

                for (var i = 0; i < datapoints.length; i++) {
                    // create a new col after every itemsPerCol
                    if (i % itemsPerCol === 0) {
                        OffsetMultiplier++;
                        YOffset = OffsetMultiplier * itemsPerCol;
                        XOffset += newXOffset;
                        newXOffset = 0;
                    }
                    // Y data point - metric specific
                    var tempData = datapoints[i].data[1];

                    // X data point - time
                    // var tempDate = new Date(datapoints[i].data[0]);
                    // tempDate = GMTon ? GMTformatDate(tempDate) : formatDate(tempDate);

                    var circle = group.select("circle." + datapoints[i].graphClassName)
                                        .attr('cy', 20 * (0.75 + i - YOffset) + Y)
                                        .attr('cx', X + tipOffset + tipPadding + circleRadius + XOffset);
                    var textLine = group.select("text." + datapoints[i].graphClassName)
                                        .attr('dy', 20 * (1 + i - YOffset) + Y)
                                        .attr('dx', X + tipOffset + tipPadding + circleLen + 2 + XOffset);

                    if (scope.menuOption.isTooltipDetailOn) {
                        textLine.text(datapoints[i].name + "   " + d3.format('0,.7')(tempData));
                    } else {
                        textLine.text(d3.format('.2s')(tempData));
                    }

                    // update XOffset if existing offset is smaller than texLine
                    var tempXOffset = textLine.node().getBBox().width + circleLen + tipOffset;
                    if (tempXOffset > newXOffset) {
                        newXOffset = tempXOffset;
                    }

                    /*
                     // keep this just in case different styles are needed for time and value
                     textLine.append('tspan')
                     .attr('class', 'timestamp')
                     .text(formatDate(new Date(datapoints[i][0])));
                     textLine.append('tspan').attr('class', 'value')
                     .attr('dx', 8)
                     .text(formatValue(datapoints[i][1]));
                     textLine.append('tspan').attr('dx', 8).text(names[i]);
                     */
                }

                var tipBounds = group.node().getBBox();
                tipBox.attr('x', X + tipOffset);
                tipBox.attr('y', Y + tipOffset);

                if (tipBounds.width === 0 || tipBounds.height === 0) {
                    // when there is no graph, make the tipBox 0 size
                    tipBox.attr('width', 0);
                    tipBox.attr('height', 0);
                } else {
                    tipBox.attr('width', tipBounds.width + 4 * tipPadding);
                    tipBox.attr('height', tipBounds.height + 2 * tipPadding);
                }

                // move tooltip on the right if there is not enough to display it on the right
                var transformAttr;
                if (X + Number(tipBox.attr('width')) > (width + marginRight) &&
                    X - Number(tipBox.attr('width')) > 0) {
                    transformAttr = 'translate(-' + (Number(tipBox.attr('width')) + 2 * tipOffset) + ')';
                } else {
                    transformAttr = null;
                }
                group.attr('transform', transformAttr);
                tipBox.attr('transform', transformAttr);
            }

            function legendCreator(names, colors, graphClassNames) {
                var tmpSources = [];
                for (var i = 0; i < names.length; i++) {
                    var tempColor = colors[i] === null ? z(names[i]) : colors[i];
                    tmpSources.push({
                        name: names[i],
                        displaying: true,
                        color: tempColor,
                        graphClassName: graphClassNames[i]
                    });
                }
                // set names into $scope for legend
                scope.sources = tmpSources;
            }

            /*  Generate cross lines at the point/cursor
             mouseX,mouseY are actual values
             X,Y are coordinates value
             */
            function updateCrossLine(mouseX, mouseY, X, Y) {
                //if (!mouseY) return; comment this to avoid some awkwardness when there is no data in selected range

                focus.select('[name=crossLineX]')
                    .attr('x1', X).attr('y1', 0)
                    .attr('x2', X).attr('y2', height);
                focus.select('[name=crossLineY]')
                    .attr('x1', 0).attr('y1', Y)
                    .attr('x2', width).attr('y2', Y);
                //add some information around the axis

                var textY;
                if(isNaN(mouseY)){ //mouseY can be 0
                    textY = "No Data";
                }else{
                    textY = d3.format('.2s')(mouseY);
                }

                focus.select('[name=crossLineTipY')
                    .attr('x', 0)
                    .attr('y', Y)
                    .attr('dx', -crossLineTipWidth)
                    .text(textY);

                //add a background to it
                var boxY = focus.select('[name=crossLineTipY]').node().getBBox();
                focus.select('[name=crossLineTipRectY]')
                    .attr('x', boxY.x - crossLineTipPadding)
                    .attr('y', boxY.y - crossLineTipPadding)
                    .attr('width', boxY.width + 2 * crossLineTipPadding)
                    .attr('height', boxY.height + 2 * crossLineTipPadding);

                var date = GMTon ? GMTformatDate(mouseX) : formatDate(mouseX);
                focus.select('[name=crossLineTipX]')
                    .attr('x', X)
                    .attr('y', 0)
                    .attr('dy', crossLineTipHeight)
                    .text(date);

                //add a background to it
                var boxX = focus.select('[name=crossLineTipX]').node().getBBox();
                focus.select('[name=crossLineTipRectX]')
                    .attr('x', boxX.x - crossLineTipPadding)
                    .attr('y', boxX.y - crossLineTipPadding)
                    .attr('width', boxX.width + 2 * crossLineTipPadding)
                    .attr('height', boxX.height + 2 * crossLineTipPadding);

            }

            //reset the brush area
            function reset() {
                svg_g.selectAll(".brush").call(brush.move, null);
                svg_g.selectAll(".brushMain").call(brush.move, null);
            }

            //redraw the line with restrict
            function redraw(){
                var domainStart = x.domain()[0].getTime();
                var domainEnd = x.domain()[1].getTime();
                //redraw
                if(isBrushInNonEmptyRange()) {
                    mainChart.selectAll('path.line').attr('display', null);
                    //update the dataum and redraw the line
                    currSeries.forEach(function (metric, index) {
                        if (metric === null || metric.data.length === 0 || //empty
                            !scope.sources[index].displaying) return; //hided
                        var len = metric.data.length;
                        if (metric.data[0][0] > domainEnd || metric.data[len - 1][0] < domainStart){
                            mainChart.select('path.line.' + metric.graphClassName)
                                .datum([])
                                .attr('d', line);
                            return;
                        }
                        //if this metric time range is within the x domain
                        var start = bisectDate(metric.data, x.domain()[0]);
                        if(start > 0) start-=1; //to avoid cut off issue on the edge
                        var end = bisectDate(metric.data, x.domain()[1], start) + 1; //to avoid cut off issue on the edge
                        var data = metric.data.slice(start, end + 1);

                        //only render the data within the domain
                        mainChart.select('path.line.' + metric.graphClassName)
                            .datum(data)
                            .attr('d', line); //change the datum will call d3 to redraw
                    });
                    //svg_g.selectAll(".line").attr("d", line);//redraw the line
                }else{
                    mainChart.selectAll('path.line').attr('display', 'none');
                }
                xAxisG.call(xAxis);  //redraw xAxis
                yAxisG.call(yAxis);  //redraw yAxis
                yAxisRG.call(yAxisR); //redraw yAxis right
                xGridG.call(xGrid);
                yGridG.call(yGrid);
                if (!scope.menuOption.isBrushOn) {
                    context.attr("display", "none");
                }
                updateDateRange();
                updateAnnotations();
            }

            scope.redraw = redraw; //have to register this as scope function cause toggleGraphOnOff is outside link function

            //brushed
            function brushed() {
                // ignore the case when it is called by the zoomed function
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "zoom" )) return;
                var s = d3.event.selection || x2.range();
                x.domain(s.map(x2.invert, x2));     //rescale the domain of x axis
                                                    //invert the x value in brush axis range to the
                                                    //value in domain

                reScaleY(); //rescale domain of y axis
                //redraw
                redraw();
                //sync with zoom
                chartRect.call(zoom.transform, d3.zoomIdentity
                    .scale(width / (s[1] - s[0]))
                    .translate(-s[0], 0));

                if (brushMainG) {
                    brushMainG.call(zoom.transform, d3.zoomIdentity
                        .scale(width / (s[1] - s[0]))
                        .translate(-s[0], 0));
                }
            }

            function brushedMain() {
                var selection = d3.event.selection; //the brushMain selection
                if (selection) {
                    var start = x.invert(selection[0]);
                    var end = x.invert(selection[1]);
                    var range = end - start;
                    brushMainG.call(brushMain.move, null);
                    if (range * maxScaleExtent < x2.domain()[1] - x2.domain()[0]) return;
                    x.domain([start, end]);
                    brushG.call(brush.move, [x2(start), x2(end)]);
                }
            }

            //zoomed
            function zoomed() {
                // ignore the case when it is called by the brushed function
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "brush" || d3.event.sourceEvent.type === "end"))return;
                var t = d3.event.transform;
                x.domain(t.rescaleX(x2).domain());  //rescale the domain of x axis
                                                    //invert the x value in brush axis range to the
                                                    //value in domain

                reScaleY(); //rescale domain of y axis
                //redraw
                redraw();
                // sync the brush
                context.select(".brush").call
                (brush.move, x.range().map(t.invertX, t));

                //sync the crossLine
                var position = d3.mouse(this);
                var positionX = position[0];
                var positionY = position[1];
                var mouseX = x.invert(positionX);
                var mouseY = y.invert(positionY); //domain value
                if(isBrushInNonEmptyRange()) {
                    focus.selectAll('circle').attr('display', null)
                        .each(function (d, i) {
                        var circle = d3.select(this);
                        var dataX = circle.attr('dataX');
                        var dataY = circle.attr('dataY');
                        circle.attr('transform', 'translate(' + x(dataX) + ',' + y(dataY) + ')');

                        if(dataX < x.domain()[0] || dataX > x.domain()[1]){
                            circle.attr('display', 'none');
                        }
                    });
                }else{
                    focus.selectAll('circle').attr('display', 'none');
                }
                updateCrossLine(mouseX, mouseY, positionX, positionY);
            }

            //change brush focus range, k is the number of minutes
            function brushMinute(k) {
                return function () {
                    if (!k) k = (x2.domain()[1] - x2.domain()[0]);
                    //the unit of time value is millisecond
                    //x2.domain is the domain of total
                    var interval = k * 60000; //one minute is 60000 millisecond

                    //take current x domain value and extend it
                    var start = x.domain()[0].getTime();
                    var end = x.domain()[1].getTime();
                    var middle = (start + end) / 2;
                    start = middle - interval / 2;
                    var min = x2.domain()[0].getTime();
                    var max = x2.domain()[1].getTime();
                    if (start < min) start = min;
                    end = start + interval;
                    if (end > max) end = max;
                    context.select(".brush").call
                    (brush.move, [x2(new Date(start)), x2(new Date(end))]);
                };
            }

            //rescale YAxis based on XAxis Domain
            function reScaleY() {
                if (currSeries === "series" || !currSeries) return;
                if(agYMin !== undefined && agYMax !== undefined) return; //hard coded ymin & ymax

                var xDomain = x.domain();
                var datapoints = [];

                currSeries.forEach(function (metric, index) {
                    if (metric === null || metric.data.length === 0 || //empty
                        !scope.sources[index].displaying) return; //hided

                    var len = metric.data.length;
                    if (metric.data[0][0] > xDomain[1].getTime() || metric.data[len - 1][0] < xDomain[0].getTime()) return;
                    //if this metric time range is within the xDomain
                    var start = bisectDate(metric.data, xDomain[0]);
                    var end = bisectDate(metric.data, xDomain[1], start);
                    datapoints = datapoints.concat(metric.data.slice(start, end + 1));
                });

                var extent = d3.extent(datapoints, function (d) {
                    return d[1];
                });
                var diff = extent[1] - extent[0];
                if (diff === 0) diff = yAxisPadding;
                var buffer = diff * bufferRatio;
                var yMin = (agYMin === undefined) ? extent[0] - buffer : agYMin;
                var yMax = (agYMax === undefined) ? extent[1] + 3 * buffer : agYMax;

                y.domain([yMin, yMax]);
            }
            scope.reScaleY = reScaleY; //have to register this as scope function cause toggleGraphOnOff is outside link function

            //precise resize without removing and recreating everything
            function resize(){
                if (series === "series" || !series) {
                    return;
                }

                containerWidth = $(container).width();
                width = containerWidth - marginLeft - marginRight;

                if(width < 0) return; //it happens when click other tabs (like 'edit'/'history', the charts are not destroyed

                margin = {
                    top: marginTop,
                    right: marginRight,
                    bottom: containerHeight - marginTop - height,
                    left: marginLeft
                };
                margin2 = {
                    top: containerHeight - height2 - marginBottom,
                    right: marginRight,
                    bottom: marginBottom,
                    left: marginLeft
                };

                if (series.length > 0) {
                    var tempX = x.domain(); //remember that when resize

                    clip.attr('width', width)
                        .attr('height', height);
                    chartRect.attr('width', width);
                    //update range
                    x.range([0, width]);
                    x2.range([0, width]);

                    //update brush & zoom
                    brush.extent([
                        [0, 0],
                        [width, height2]
                    ]);
                    brushMain.extent([
                        [0, 0],
                        [width, height]
                    ]);
                    zoom.translateExtent([
                            [0, 0],
                            [width, height]
                        ])
                        .extent([
                            [0, 0],
                            [width, height]
                        ]);
                    brushG.call(brush);
                    brushMainG.call(brushMain);

                    //width related svg element
                    svg.attr('width', width + margin.left + margin.right);
                    svg_g.attr('width', width)
                        .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

                    yGrid.tickSizeInner(-width);
                    yGridG.call(yGrid);

                    yAxisRG.attr('transform', 'translate(' + width + ')')
                        .call(yAxisR);

                    svg_g.selectAll(".line").attr("d", line); //redraw the line
                    svg_g.selectAll(".brushLine").attr("d", line2); //redraw brush line

                    xAxisG.call(xAxis); //redraw xAxis
                    yAxisG.call(yAxis); //redraw yAxis
                    xGridG.call(xGrid);
                    xAxisG2.call(xAxis2);

                    // update x axis label if it's in ag options
                    if (chartOptions.xAxis!== undefined && chartOptions.xAxis.title !== undefined) {
                        mainChart.select(".xAxisLabel")
                                  .attr("transform", "translate(" + (width / 2) + " ," + (height + margin.top + xAxisLabelHeightFactor) + ")");
                    }

                    if (tempX[0].getTime() == x2.domain()[0].getTime() &&
                        tempX[1].getTime() == x2.domain()[1].getTime()) {
                        reset();
                    } else {
                        //restore the zoom&brush
                        context.select(".brush").call(brush.move, [x2(tempX[0]), x2(tempX[1])]);
                    }
                } else {
                    displayEmptyGraph(container, width, height, margin, messageToDisplay);
                }
            }

            function updateGraph(series) {
                var allDatapoints = [];
                currSeries = series;

                series.forEach(function (metric) {
                    allDatapoints = allDatapoints.concat(metric.data);
                });

                //x domain was set according to dateConfig previously
                //this shows exactly the date range defined by user instead of actual data

                dateExtent = d3.extent(allDatapoints, function (d) {
                    return d[0];
                });

                if(!startTime) startTime = dateExtent[0]; //startTime/endTime will not be 0
                if(!endTime) endTime = dateExtent[1];

                //x.domain([startTime, endTime]);
                x.domain(dateExtent); //doing this cause some date range are defined in metric queries and regardless of ag-date

                var yDomain = d3.extent(allDatapoints, function (d) {
                    return d[1];
                });

                // if only a straight line
                if (yDomain[0] === yDomain[1]) {
                    yDomain[0] -= yAxisPadding;
                    yDomain[1] += 3 * yAxisPadding;
                }

                if(agYMin !== undefined && agYMax !== undefined){
                    y.domain([agYMin, agYMax]);
                }else{
                    y.domain(yDomain);
                }

                x2.domain(x.domain());
                y2.domain(yDomain);

                series.forEach(function (metric) {
                    if (metric.data.length === 0) return;
                    mainChart.select('path.line.' + metric.graphClassName)
                        .datum(metric.data)
                        .attr('d', line);
                    context.select('path.brushLine.' + metric.graphClassName + '_brushline')
                        .datum(metric.data)
                        .attr('d', line2);
                });
                //draw the brush xAxis
                xAxisG2.call(xAxis2);
                setZoomExtent(3);

                // draw flag(s) to denote annotation mark
                updateAnnotations();
            }

            // when there is no data for series, display a message
            function displayEmptyGraph(containerName, width, height, margin, messageToDisplay) {
                if (svg) svg.remove();
                svg = d3.select(containerName).append('svg')
                    .attr('width', width + margin.left + margin.right)
                    .attr('height', height + margin.top + margin.bottom);
                svg.selectAll('text')
                    .data(messageToDisplay)
                    .enter()
                    .append("text")
                    .attr("x", margin.left + width/2)
                    .attr("y", function (d, i) {
                        return 20*i + margin.top;
                    })
                    .style("text-anchor", "middle")
                    .style("font-size", "12px")
                    .text(function(d){return d;});
            }

            function updateAnnotations() {
                if (!series) return;
                series.forEach(function(metric) {
                    if (!metric.flagSeries) return;
                    var flagSeries = metric.flagSeries.data;
                    flagSeries.forEach(function(d) {
                        var label = flagsG.select('#' + metric.graphClassName + d.flagID);
                        var x_Val = x(d.x); // d.x is timestamp of X axis
                        var y_Val = height - 35;
                        // dont render flag if it's outside of the range; similar to focus circle
                        if (d.x < x.domain()[0] || d.x > x.domain()[1]) {
                            label.attr("display", 'none');
                        } else {
                            label.attr("display", null);
                            label.attr("transform", "translate(" + x_Val + ", " + y_Val + ")");
                        }
                    });
                });
            }

            //this function add the overlay element to the graph when mouse interaction takes place
            //need to call this after drawing the lines in order to put mouse interaction overlay on top
            function addOverlay() {
                //the graph rectangle area
                chartRect = mainChart.append('rect')
                    .attr('class', 'chartOverlay')
                    .attr('width', width)
                    .attr('height', height)
                    .on('mouseover', function () {
                       mouseOverChart();
                    })
                    .on('mouseout', function () {
                        focus.style('display', 'none');
                        if (scope.menuOption.isTooltipOn) tip.style('display', 'none');
                    })
                    .on('mousemove', mouseMove)
                    .call(zoom)
                ;

                //the brush overlay
                brushG = context.append("g")
                    .attr("class", "brush")
                    .call(brush)
                    .call(brush.move, x.range()); //change the x axis range when brush area changes

                brushMainG = mainChart.append("g")//have to do this seperately, because rect svg cannot register brush
                    .attr("class", "brushMain")
                    .call(zoom)
                    .on("mousedown.zoom", null)
                    .call(brushMain)
                    .on('mouseover', function () {
                       mouseOverChart();
                    })
                    .on('mouseout', function () {
                        focus.style('display', 'none');
                        if (scope.menuOption.isTooltipOn) tip.style('display', 'none');
                    })
                    .on('mousemove', mouseMove);

                if (scope.menuOption.isBrushMainOn) {
                    brushMainG.attr('display', null);
                } else {
                    brushMainG.attr('display', 'none');
                }
                // no wheel zoom on page load
                if (!scope.menuOption.isWheelOn) {
                    chartRect.on("wheel.zoom", null);   // does not disable 'double-click' to zoom
                    brushMainG.on("wheel.zoom", null);
                }
            }

            //toggle time brush
            function toggleBrush() {
                var display = !scope.menuOption.isBrushOn ? 'none' : null;
                svg_g.select('.context').attr('display', display);

                updateStorage();
            }

            //toggle time brush
            function toggleBrushMain() {
                //enable main chart brush
                if (scope.menuOption.isBrushMainOn) {
                    brushMainG.attr('display', null);
                } else {
                    //disable main chart brush
                    brushMainG.attr('display', 'none');
                }
                updateStorage();
            }

            //toggle the mouse wheel for zoom
            function toggleWheel() {
                if (scope.menuOption.isWheelOn) {
                    chartRect.call(zoom);
                    brushMainG.call(zoom)
                        .on("mousedown.zoom", null);
                } else {
                    chartRect.on("wheel.zoom", null);
                    brushMainG.on("wheel.zoom", null);
                }
                updateStorage();
            }

            //toggle tooltip
            function toggleTooltip() {
                if (scope.menuOption.isTooltipOn) {
                    svg_g.select(".tooltip").attr("display", 'none');
                } else {
                    svg_g.select(".tooltip").attr("display", null);
                }
                updateStorage();
            }

            //date range
            function updateDateRange() {
                var start, end, str;
                if (GMTon) {
                    start = GMTformatDate(x.domain()[0]);
                    end = GMTformatDate(x.domain()[1]);
                    str = start + ' - ' + end + " (GMT/UTC)";
                } else {
                    start = formatDate(x.domain()[0]);
                    end = formatDate(x.domain()[1]);
                    var temp = (new Date()).toString();
                    var currentTimeZone = temp.substring(temp.length - 6, temp.length);
                    str = start + ' - ' + end + currentTimeZone;
                }

                // update $scope
                scope.dateRange = str;

                // update view
                d3.select('#topTb-' + chartId + ' .dateRange').text(str);
            }

            //extent, k is the least number of points in one line you want to see on the main chart view
            function setZoomExtent(k) {
                var numOfPoints = currSeries[0].data.length;
                //choose the max among all the series
                for (var i = 1; i < currSeries.length; i++) {
                    if (numOfPoints < currSeries[i].data.length) {
                        numOfPoints = currSeries[i].data.length;
                    }
                }
                if (!k || k > numOfPoints) k = 3;
                zoom.scaleExtent([1, numOfPoints / k]);
                maxScaleExtent = parseInt(numOfPoints / k);
            }

            //dynamically enable button for brush time period(1h/1d/1w/1m/1y)
            function enableBrushTime() {
                var range = x2.domain()[1] - x2.domain()[0];
                if (range > 3600000) {
                    //enable 1h button
                    $('[name=oneHour]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24) {
                    //enable 1d button
                    $('[name=oneDay]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24 * 7) {
                    //enable 1w button
                    $('[name=oneWeek]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24 * 30) {
                    //enable 1month button
                    $('[name=oneMonth]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24 * 365) {
                    //enable 1y button
                    $('[name=oneYear]', topToolbar).prop('disabled', false);
                }
            }

            function isBrushInNonEmptyRange(){
                return x.domain()[0].getTime() <= dateExtent[1] &&  x.domain()[1].getTime()>= dateExtent[0];
            }

            function mouseOverChart(){
                focus.style('display', null);
                if(isBrushInNonEmptyRange()) {
                    if (scope.menuOption.isTooltipOn) tip.style('display', null);
                }else{
                    //no need to show the circle to tip
                    focus.selectAll('circle').attr('display', 'none');
                    tip.attr('display', 'none');
                }
            }

            function setupMenu(){
                //button set up
                $('[name=reset]', topToolbar).click(reset);
                $('[name=oneHour]', topToolbar).click(brushMinute(60));
                $('[name=oneDay]', topToolbar).click(brushMinute(60*24));
                $('[name=oneWeek]', topToolbar).click(brushMinute(60*24*7));
                $('[name=oneMonth]', topToolbar).click(brushMinute(60*24*30));
                $('[name=oneYear]', topToolbar).click(brushMinute(60*24*365));

                //toggle
                $('[name=toggle-brush]', topToolbar).change(toggleBrush);
                $('[name=toggle-brush-main]', topToolbar).change(toggleBrushMain);
                $('[name=toggle-wheel]', topToolbar).change(toggleWheel);
                $('[name=toggle-tooltip]', topToolbar).change(toggleTooltip);
            }

            function hideMenu(){
                scope.hideMenu = true;
            }

            function updateStorage(){
                Storage.set('menuOption_' + scope.dashboardId + '_' + lineChartIdName + scope.lineChartId, scope.menuOption);
            }

            // create graph only when there is data
            if (!series || series.length === 0) {
                //this should never happen
                console.log("Empty data from chart data processing");
                hideMenu();
            } else {
                // set up legend
                names = series.map(function(metric) { return metric.name; });
                colors = series.map(function(metric) { return metric.color; });
                graphClassNames = series.map(function(metric) { return metric.graphClassName; });
                legendCreator(names, colors, graphClassNames);
                // check if there is anything to graph
                var hasNoData, emptyReturn, invalidExpression;
                var tempSeries = [];
                for (var i = 0; i < series.length; i++) {
                    if (series[i].invalidMetric) {
                        scope.invalidSeries.push(series[i]);
                        invalidExpression = true;
                    } else if (series[i].noData) {
                        scope.noDataSeries.push(series[i]);
                        emptyReturn = true;
                    } else if (series[i].data.length === 0) {
                        hasNoData = true;
                    } else {
                        // only keep the metric that's graphable
                        tempSeries.push(series[i]);
                    }
                }
                series = tempSeries;

                if (series.length > 0) {
                    // Update graph on new metric results
                    setGraph();
                    setGraphTools(series);
                    updateGraph(series);
                    // initialize starting point for graph settings & info
                    addOverlay();

                    // dont need to setup everything for a small chart
                    updateDateRange();
                    enableBrushTime();
                    reset();    //to remove the brush cover first for user the drag
                    setupMenu();
                } else {
                    // generate content for no graph message
                    if (invalidExpression) {
                        messageToDisplay.push('Metric does not exist in TSDB');
                        for (var i = 0; i < scope.invalidSeries.length; i ++) {
                            messageToDisplay.push(scope.invalidSeries[i].errorMessage);
                        }
                        messageToDisplay.push('(Failed metrics are black in the legend)');
                    }
                    if (emptyReturn) {
                        messageToDisplay.push('No data returned from TSDB');
                        messageToDisplay.push('(Empty metrics are labeled maroon)');
                    }
                    if (hasNoData) {
                        messageToDisplay.push('No data found for metric expressions');
                        messageToDisplay.push('(Valid sources have normal colors)');
                    }
                    displayEmptyGraph(container, width, height, margin, messageToDisplay);
                    hideMenu();
                }
            }

            //TODO improve the resize efficiency if performance becomes an issue
            element.on('$destroy', function(){
                if(lineChartIdIndex){
                    resizeJobs = [];
                    lineChartIdIndex = 0;
                }
            });
            resizeJobs.push(resize);
        }
    };
}]);


/***/ }),
/* 25 */
/***/ (function(module, exports) {

angular.module('argus.directives.charts.metric', [])
.directive('agMetric', ['UtilService', function(UtilService) {
    var metricNameIndex = 100;
    return {
        restrict: 'E',
        require: ['?^agChart', '?^agStatusIndicator', '?^agHeatmap', '?^agTable'],
        scope: {
            expression: '@'
        },
        controller: 'metricElements',
        template: '',
        link: function(scope, element, attributes, controllers) {
            var elementCtrl;
            var value = '';
            var seriesData = {};
            var metricName = 'metric_' + metricNameIndex++;

            // assign proper controller
            elementCtrl = UtilService.assignController(controllers);

            // separate specific series data from other attributes
            // 'color' & 'name' are used to supplement the 'series' data when rendering a chart
            seriesData.color = attributes.seriescolor;
            seriesData.name = attributes.seriesname;

            if (attributes.value && attributes.value.length > 0) {
                value = attributes.value;
            } else {
                value = element.text();
            }

            if (value && value.length > 0) {
                elementCtrl.updateMetric(metricName, value.replace(/(\r\n|\n|\r|\s+)/gm,""), scope.metricOptions, seriesData);
            }

            scope.$watch('expression', function(newValue, oldValue) {
                if (newValue) {
                    elementCtrl.updateMetric(metricName, newValue, scope.metricOptions, seriesData);
                }
            });
            element.html('<span> </span>');
        }
    }
}]);

/***/ }),
/* 26 */
/***/ (function(module, exports) {

/* currently not being used? */

angular.module('argus.directives.charts.option', [])
.directive('agOption', ['UtilService', function(UtilService) {
    return {
        restrict: 'E',
        require: ['?^agChart', '?^agHeatmap', '?^agTable', '?^agMetric'],
        scope: {},
        template: '',
        link: function(scope, element, attributes, controllers) {
            var elementCtrl;

            // assign proper controller
            elementCtrl = UtilService.assignController(controllers);

            var value = '';

            if (attributes.value && attributes.value.length > 0) {
                value = attributes.value;
            } else {
                value = element.text();
            }

            elementCtrl.updateOption(attributes.name, value);
            element.html('<span> </span>');
        }
    }
}]);


/***/ }),
/* 27 */
/***/ (function(module, exports) {

angular.module('argus.directives.charts.statusIndicator', [])
.directive('agStatusIndicator', ['ChartDataProcessingService', 'ChartRenderingService', 'DashboardService', 'VIEWELEMENT',
    function(ChartDataProcessingService, ChartRenderingService, DashboardService, VIEWELEMENT) {

        var metricNameIndex = 1;
        return {
            restrict: 'E',
            transclude: true,
            scope: {
                serviceName: '@name',
                hi: '@hi',
                lo: '@lo'
            },
            require: '^agDashboard',
            controller: 'ViewElements',
            template: '<div ng-transclude=""> </div>',
            link: function(scope, element, attributes, dashboardCtrl) {
                var metricExpression;
                var indicatorHTML =
                    '<div class="serviceItem">' +
                        '<div class="serviceName">' + attributes.name + '</div>' +
                        '<div id="'+ attributes.name + '-status" class="statusIndicator"></div>' +
                    '</div>';

                // render status indicator
                element.html(indicatorHTML);

                // listen to scope for event and controls info
                scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
                    for (var key in scope.metrics) {
                        if (scope.metrics.hasOwnProperty(key)) {
                            // get metricExpression from scope
                            metricExpression = scope.metrics[key].expression;

                            // process mertricExpression from controls if present
                            if ( controls ) {
                                metricExpression = ChartDataProcessingService.augmentExpressionWithControlsData(event, metricExpression, controls);
                            }
                        }
                    }

                    // get datapoints from metric expression
                    if ( metricExpression) {
                        DashboardService.getMetricData(metricExpression)
                            .then(function( result ) {
                                // get the last data point from the result data
                                var lastStatusVal = ChartDataProcessingService.getLastDataPoint(result.data[0].datapoints);

                                // update status indicator
                                ChartRenderingService.updateIndicatorStatus(attributes, lastStatusVal);
                            });
                    }
                });
            }
        }
}]);

/***/ }),
/* 28 */
/***/ (function(module, exports) {

angular.module('argus.directives.charts.table', [])
.directive('agTable', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var tableNameIndex = 1;
    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""></div>',
        link: function(scope, element, attributes, dashboardCtrl) {
        	DashboardService.buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.table, tableNameIndex++, DashboardService, growl);
        }
    }
}]);

/***/ }),
/* 29 */
/***/ (function(module, exports) {

angular.module('argus.directives.controls.dashboard', [])
.directive('agDashboard', ['$location', '$rootScope', '$routeParams', 'Controls', function($location, $rootScope, $routeParams, Controls) {
    return {
        restrict: 'E',
        scope: {
            name: '@'
        },
        transclude: true,
        template: '<div ng-transclude=""></div>',
        controller: function($scope) {
            $scope.controls = [];
            
            this.updateControl = function(controlName, controlValue, controlType, localSubmit) {
            	var controlExists = false;

                if (!localSubmit) {
                    for (var prop in $routeParams) {
                        if (prop == $scope.controlName) {
                            controlValue = $routeParams[prop];
                        }
                    }
                }

                for (var i in $scope.controls) {
                    if ($scope.controls[i].name === controlName) {
                        $scope.controls[i].value = controlValue;
                        controlExists = true;
                        break;
                    }
                }

            	if (!controlExists) {
            		var control = {
            			name: controlName,
            			value: controlValue,
            			type: controlType
                	};
                	$scope.controls.push(control);
            	}

                //add controls to url
            	this.addControlsToUrl();
            };

            this.addControlsToUrl = function () {
                var controls = $scope.controls;
                // update url with controls params
                var urlStr = Controls.getUrl(controls);
                $location.search(urlStr);
            };

            this.getAllControls = function(){
            	return $scope.controls;
            };
            
            this.getSubmitBtnEventName = function(){
                return 'submitButtonEvent';
            };

            this.broadcastEvent = function(eventName, data){
            	console.log(eventName + ' was broadcast');
            	$scope.$broadcast(eventName, data);
            };
        },
        link:function(scope, element, attributes){
            if (!attributes.onload || attributes.onload == true) {
                scope.$broadcast('submitButtonEvent', scope.controls);
            }
        }
    }
}]);

/***/ }),
/* 30 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.directives.controls.date', [])
.directive('agDate', ['CONFIG', '$routeParams', function(CONFIG, $routeParams) {
    return {
        restrict: 'E',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default'
        },
        controller: function($scope, $filter) {
            $scope.ctrlVal = $scope.controlValue;

            for (var prop in $routeParams) {
                if (prop == $scope.controlName) {
                    $scope.ctrlVal = $routeParams[prop];
                    // remove GMT from page refreshing
                    if( $scope.ctrlVal.indexOf('GMT') >= 0){
                        $scope.ctrlVal = $scope.ctrlVal.replace('GMT','').trim();
                        $scope.GMTon = true;
                    }
                }
            }

        	$scope.datetimepickerConfig = {
    			dropdownSelector: '.my-toggle-select',
    			minuteStep: 1
        	};

        	$scope.onSetTime = function(newDate, oldDate) {
        		$scope.ctrlVal = $filter('date')(newDate, "short");
        	};
        },
        require: '^agDashboard',
        template: // TODO: move to external template
            '<strong>{{labelName}} </strong>' +
            '<div class="dropdown" style="display: inline;">' +
                '<a class="dropdown-toggle my-toggle-select" id="dLabel" role="button" data-toggle="dropdown" data-target="#" href="">' +
                    '<input type="text" class="input-medium" style="color:#000;" ng-model="ctrlVal">' +
                '</a>' +
                '<ul class="dropdown-menu" role="menu" aria-labelledby="dLabel">' +
                    '<datetimepicker ng-model="data.date" on-set-time="onSetTime(newDate, oldDate)" data-datetimepicker-config="datetimepickerConfig"></datetimepicker>' +
                '</ul>' +
            '</div>' +
            '<label class="GMT-select">GMT: <input type="checkbox" ng-model="GMTon" ng-checked="GMTon || (ctrlVal[0] === \'-\')" ng-disabled="ctrlVal[0] === \'-\'"></label>',
        link: function(scope, element, attributes, dashboardCtrl) {
            dashboardCtrlUpdateControlGMTHelper(scope.ctrlVal, scope.GMTon);
            scope.$watch('ctrlVal', function(newValue, oldValue) {
                dashboardCtrlUpdateControlGMTHelper(newValue, scope.GMTon);
            });
            scope.$watch('GMTon', function(newValue, oldValue) {
                dashboardCtrlUpdateControlGMTHelper(scope.ctrlVal, newValue);
            });

            function dashboardCtrlUpdateControlGMTHelper(controlValue, GMTon) {
                if (GMTon) {
                    dashboardCtrl.updateControl(scope.controlName, controlValue + " GMT", "agDate", true);
                } else {
                    dashboardCtrl.updateControl(scope.controlName, controlValue, "agDate", true);
                }
            }
        }
    };
}]);


/***/ }),
/* 31 */
/***/ (function(module, exports) {

angular.module('argus.directives.controls.dropdown', [])
.directive('agDropdown', ['CONFIG', 'Tags', function(CONFIG, Tags) {
    return {
        restrict: 'E',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default',
        },
        controller: function($scope) {
        	$scope.selectizeOptions = [];
            $scope.selectizeConfig = {
        		delimiter: '|',
        	    create: function(input) {
        	        return {
        	            value: input,
        	            text: input
        	        }
        	    }
            };
        },
        require: '^agDashboard',
        template: 
            '<B>{{labelName}} : </B>' +
            '<div style="display: inline-block; width: 20%;">' +
                '<selectize config="selectizeConfig" options="selectizeOptions" ng-model="controlValue">' +
            '</div>',
        link: function(scope, element, attributes, dashboardCtrl) {
        	var key = attributes.key;
        	if (key) {
        		var promise = Tags.getDropdownOptions(key);
            	promise.success(function(data, status, headers, config) {
            		if (data && data[key]) {
            	    	var options;
            	    	for(var i in data) {
            	    		options = data[i];
            	    	}
            	    	
            	    	var selectize = element.find('selectize')[0].selectize;
            	    	
            	    	for (var i in options) {
            	    		var option = options[i];
            	    		selectize.addOption({
            	    			text: option,
            	    			value: option
            	    		});
            	    	}
            	    	
            	    	selectize.refreshOptions(false);
            		}
            	}).error(function(data, status, headers, config) {
            		console.log("Error in retrieving tags");
                });
        	}

        	dashboardCtrl.updateControl(scope.controlName, scope.controlValue, "agDropdown");
            scope.$watch('controlValue', function(newValue, oldValue){
                dashboardCtrl.updateControl(scope.controlName, newValue, "agDropdown");
            });
        }
    }
}]);

/***/ }),
/* 32 */
/***/ (function(module, exports) {

angular.module('argus.directives.controls.submit', [])
.directive('agSubmit', ['$http', function($http) {
    return {
        restrict: 'E',
        scope: {
            controlName: '@name',
            elemId: '@id',
            cssName: '@class',
            style: '@style',
            size: '@size'
        },
        require: '^agDashboard',
        template: '',
        link: function(scope, element, attributes, dashboardCtrl) {
            var buttonName = 'Submit';
            if (element.text() && element.text().length > 0) {
                buttonName = element.text();
            }

            element.html('<button id='+ scope.elemId +' class="btn btn-primary btn-md '+ scope.cssName +'" size='+ scope.size +' style='+ scope.style +'>' + buttonName + '</button>');

            element.on('click', function() {
                $http.pendingRequests = []; //This line should be deleted.
                dashboardCtrl.broadcastEvent(dashboardCtrl.getSubmitBtnEventName(), dashboardCtrl.getAllControls());
            });
        }
    }
}]);

/***/ }),
/* 33 */
/***/ (function(module, exports) {

angular.module('argus.directives.controls.text', [])
.directive('agText', ['CONFIG', '$routeParams', function(CONFIG, $routeParams) {
    return {
        restrict: 'EA',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default',
            elemId: '@id',
            cssName: '@class',
            style: '@style',
            size: '@size'
        },
        controller: function($scope) {
            $scope.ctrlVal = $scope.controlValue;

            for (var prop in $routeParams) {
                if (prop == $scope.controlName) {
                    $scope.ctrlVal = $routeParams[prop];
                }
            }
        },
        require:'^agDashboard',
        template:'<strong>{{labelName}} </strong><input id="{{elemId}}" type="text" class="{{cssName}}" size="{{size}}" style="{{style}}" ng-model="ctrlVal">',
        link: function(scope, element, attributes, dashboardCtrl) {
            dashboardCtrl.updateControl(scope.controlName, scope.ctrlVal, "agText");
            scope.$watch('ctrlVal', function(newValue, oldValue){
                dashboardCtrl.updateControl(scope.controlName, newValue, "agText", true);
            });
        }
    }
}]);

/***/ }),
/* 34 */
/***/ (function(module, exports) {

angular.module('argus.directives.dashboardResource', [])
.directive('agDashboardResource', ['DashboardService', '$sce', '$compile', '$rootScope', '$timeout', function(DashboardService, $sce, $compile, $rootScope, $timeout) {
    return {
        restrict: 'E',
        scope: false,
        link: function(scope, element, attribute) {
            var dashboardID;
            if (attribute.id && attribute.id > 0) {
                dashboardID = attribute.id;
            } else {
                dashboardID = scope.dashboardId;
            }

            DashboardService.getDashboardById(dashboardID)
                .success(function (data) {
                    element.html('<div>' + $sce.trustAsHtml(data.content) + '</div>');
                    $compile(element.contents())(scope);
                });
        }
    }
}]);

/***/ }),
/* 35 */
/***/ (function(module, exports) {

angular.module('argus.directives.headerMenu', [])
.directive('headerMenu', ['Auth', function (Auth) {
    "use strict";
    return {
        restrict: 'E',
        templateUrl: 'js/templates/headerMenu.html',
        scope: {},
        controller: ['$rootScope', '$scope', function ($rootScope, $scope) {

            $rootScope.$on('$routeChangeSuccess', function (event, current, previous) {
                $scope.activeTab = (current.$$route) ? current.$$route.activeTab : '';
            });

            $scope.isLoggedIn = function () {
                return Auth.isLoggedIn();
            };

            $scope.currentUser = function () {
                return Auth.getUsername();
            };
            
            $scope.logout = function () {
                Auth.logout();
            };
        }],
        link: function(scope, element, attribute) {
        }
    }
}]);

/***/ }),
/* 36 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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
/**
 * Created by liuxizi.xu on 9/2/16.
 */


angular.module('argus.directives')
    .directive('tableList', function() {
        return {
            restrict: "E",
            templateUrl: 'js/templates/tableList.html',
            scope: {
                colName: '=',
                properties: '=',
                loaded: '=',
                dataSet: '=data',
                addItem: '&',
                delete: '&',
                enable: '&',
                refreshList: '&'
            },
            controller: ['$scope', 'InputTracker', 'Auth',
                function($scope, InputTracker, Auth) {
                // TODO: move this to a service
                // itemsPerPage setting
                $scope.itemsPerPageOptions = [5, 10, 15, 25, 50, 100, 200];
                var itemsPerPageFromStorage = $scope.properties.type + '-itemsPerPage';
                $scope.itemsPerPage = InputTracker.getDefaultValue(itemsPerPageFromStorage, $scope.itemsPerPageOptions[1]);
                $scope.$watch('itemsPerPage', function(newValue) {
                    InputTracker.updateDefaultValue(itemsPerPageFromStorage, $scope.itemsPerPageOptions[1], newValue);
                    update();
                });

                // searchText setting
                var searchTextFromStorage = $scope.properties.type + '-searchText';
                $scope.searchText = InputTracker.getDefaultValue(searchTextFromStorage, "");
                $scope.$watch('searchText', function(newValue) {
                    InputTracker.updateDefaultValue(searchTextFromStorage, "", newValue);
                });

                // pagination page setting
                var currentPageFromStorage = $scope.properties.type + '-currentPage';
                $scope.currentPage = InputTracker.getDefaultValue(currentPageFromStorage, 1);
                $scope.$watch('currentPage', function (newValue) {
                    InputTracker.updateDefaultValue(currentPageFromStorage, 1, newValue);
                    update();
                });

                // sort setting
                var sortKeyFromStorage = $scope.properties.type + '-sortKey';
                $scope.sortKey = InputTracker.getDefaultValue(sortKeyFromStorage, 'modifiedDate');
                var sortReverseFromStorage = $scope.properties.type + '-sortReverse';
                $scope.reverse = InputTracker.getDefaultValue(sortReverseFromStorage, true);
                $scope.sort = function (key) {
                    if ($scope.sortKey === key) {
                        $scope.reverse = !$scope.reverse;
                        InputTracker.updateDefaultValue(sortReverseFromStorage, true, $scope.reverse);
                    } else {
                        $scope.sortKey = key;
                        InputTracker.updateDefaultValue(sortKeyFromStorage, 'modifiedDate', $scope.sortKey);
                    }
                };

                // total number setting
                $scope.$watch('dataSet.length', function () {
                    update();
                });

                //enableAlert, isDisabled & delete setting
                $scope.deleteItem = function(item) {
                    $scope.delete()(item);
                };
                $scope.isDisabled = Auth.isDisabled;
                $scope.enableItem = function(item, enabled) {
                    $scope.enable()(item, enabled);
                };

                function update(){
                    $scope.start = ($scope.currentPage - 1)* $scope.itemsPerPage + 1;
                    var end = $scope.start + $scope.itemsPerPage - 1;
                    if ($scope.dataSet) {
                        $scope.end = end < $scope.dataSet.length ? end : $scope.dataSet.length;
                    }
                }
            }]
        };
    });


/***/ }),
/* 37 */
/***/ (function(module, exports) {

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
        },
        isDisabled: function (item) {
            var user = Storage.get('user');
            return !(user && (user.privileged || user.userName === item.ownerName));
        }
    };
}]);

/***/ }),
/* 38 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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



angular.module('argus.services.breadcrumbs', [])
.factory('breadcrumbs', ['$rootScope', '$location', function($rootScope, $location) {

  var breadcrumbs = [];
  var breadcrumbsService = {};

  // we want to update breadcrumbs only when a route is actually changed
  // as $location.path() will get updated imediatelly (even if route change fails!)
  $rootScope.$on('$routeChangeSuccess', function(event, current) {

    var _contextRootPath = $location.absUrl().substr(0, $location.absUrl().lastIndexOf("#"));
    var pathElements = $location.path().split('/'), result = [], i;
    var breadcrumbPath = function (index) {
      return _contextRootPath + '#/' + (pathElements.slice(0, index + 1)).join('/');
    };

    pathElements.shift();
    for (i=0; i < pathElements.length; i++) {
      result.push({name: pathElements[i], path: breadcrumbPath(i)});
    }

    breadcrumbs = result;
  });

  breadcrumbsService.getAll = function() {
    return breadcrumbs;
  };

  breadcrumbsService.getFirst = function() {
    return breadcrumbs[0] || {};
  };

  return breadcrumbsService;
}]);


/***/ }),
/* 39 */
/***/ (function(module, exports) {

angular.module('argus.services.charts.options', [])
.service('ChartOptionService', ['UtilService', function(UtilService) {
    'use strict';

    var options = {
        setCustomOptions: function(options, optionList) {
            for (var idx in optionList) {
                var propertyName = optionList[idx].name;
                var propertyValue = optionList[idx].value;
                var result = UtilService.constructObjectTree(propertyName, propertyValue);

                UtilService.copyProperties(result, options);
            }
            return options;
        },

        getOptionsByChartType: function(config, chartType, smallChart) {
            var options = config ? angular.copy(config) : {};

            options.legend = {
                enabled: true,
                maxHeight: 62,
                itemStyle: {
                    fontWeight: 'normal',
                    fontSize: '10px'
                },
                navigation : {
                    style : {
                        fontWeight: 'normal',
                        fontSize: '10px'
                    }
                }
            };

            options.credits = {enabled: false};
            options.rangeSelector = {selected: 1, inputEnabled: false};

            options.xAxis = {
                type: 'datetime',
                ordinal: false
            };

            options.lang = {
                loading: '',    // override default 'Loading...' msg from displaying under spinner img.
                noData: 'No Data to Display'
            };

            // loading spinner for graph
            options.loading = {
                labelStyle: {
                    top: '25%',
                    backgroundImage: 'url("img/ajax-loader.gif")',
                    backgroundSize: '80px 80px',
                    backgroundRepeat: 'no-repeat',
                    display: 'inline-block',
                    width: '80px',
                    height: '80px',
                    backgroundColor: '#FFF'
                }
            };

            if (chartType && chartType.toUpperCase() === 'AREA') {
                options.plotOptions = {series: {animation: false}};
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
            } else if (chartType && chartType.toUpperCase() === 'STACKAREA') {
                options.plotOptions = {
                    area: {
                        stacking: 'normal',
                       // lineWidth: 1.5,
                        dataGrouping: {
                            enabled: true//,
                          //  groupPixelWidth: 2
                        },
                        animation: false,
                        marker: {
                            enabled: false
                        }
                    }
                };
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
            } else {
                options.plotOptions = {series: {animation: false}};
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
            }

            // override options for a 'small' chart, e.g. 'Services Status' dashboard
            if ( smallChart ) {
                options.legend.enabled = false;
                options.rangeSelector.enabled = false;

                options['scrollbar'] = {enabled: false};
                options['navigator'] = {enabled: false};

                options.chart.height = '120';
                options.chart.borderWidth = 0;

                // reset loading options, no spinner required
                options.lang = {
                    loading: 'Loading...'
                };
                options.loading = {};
            }

            return options;
        }
    };

    return options;
}]);

/***/ }),
/* 40 */
/***/ (function(module, exports) {

angular.module('argus.services.charts.rendering', [])
.service('ChartRenderingService', [function() {
		'use strict';

		var service = {
				getChart: function(chartId, highChartOptions) {
						if (!chartId) return;
						$('#' + chartId).highcharts('StockChart', highChartOptions);
						var chart = $('#' + chartId).highcharts('StockChart');
						return chart;
				},

				setChartContainer: function(element, chartId, cssOpts) {
						if (!element || !chartId) return;
						element.prepend('<div id='+ chartId +' class="chartContainer ' + cssOpts +'"></div>');
				},

				loadChart: function(chartId, highChartOptions) {
						var chart = this.getChart(chartId, highChartOptions);

						// show loading spinner & hide 'no data message' during api request
						if (chart)
								this.loading(chart, true);
				},

				loading: function(chart, loading) {
						if (!chart) return;

						// show loading indicator if chart is loading
						if (loading) {
								chart.showLoading();
								chart.hideNoData();
						} else {
								// chart finishd loading: hide loading indicator when loading is complete; chart may have no data.
								chart.hideLoading();
								if ( !chart.hasData() )
										chart.showNoData();
						}
				},

				displayChart: function(chartId, highChartOptions) {
						// display chart in DOM
						var chart = this.getChart(chartId);

						// hide the loading spinner after data loads.
						if (chart)
								this.loading(chart, false);
				},

				updateIndicatorStatus: function(attributes, lastStatusVal) {
						if (!attributes || !lastStatusVal) return;

						if (lastStatusVal < attributes.lo) {
								$('#' + attributes.name + '-status').removeClass('red orange green').addClass('red');
						} else if (lastStatusVal > attributes.lo && lastStatusVal < attributes.hi) {
								$('#' + attributes.name + '-status').removeClass('red orange green').addClass('orange');
						} else if (lastStatusVal > attributes.hi) {
								$('#' + attributes.name + '-status').removeClass('red orange green').addClass('green');
						}
				}
		};

		return service;
}]);


/***/ }),
/* 41 */
/***/ (function(module, exports) {

angular.module('argus.services.charts.tools', [])
.service('ChartToolService', [function() {
    'use strict';

    var tools = {
        getTimeAxis: function(timeSpan) {
            var hours = [
                '12AM', '1AM', '2AM', '3AM', '4AM', '5AM',
                '6AM', '7AM', '8AM', '9AM', '10AM', '11AM',
                '12PM', '1PM', '2PM', '3PM', '4PM', '5PM',
                '6PM', '7PM', '8PM', '9PM', '10PM', '11PM'
            ];
            var axis = [];
            var firstHour = (new Date(timeSpan.begin)).getHours();
            for (var i = 0; i < timeSpan.span; i++) {
                axis.push(hours[(firstHour + i) % 24]);
            }
            axis.push('<b><i>Average</i></b>');
            return axis;
        }
    };

    return tools;
}]);

/***/ }),
/* 42 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.services.charts.dataProcessing', [])
.service('ChartDataProcessingService', ['ChartOptionService', 'Annotations', 'JsonFlattenService', function(ChartOptionService, Annotations, JsonFlattenService) {
    // Private methods
    function copySeries(data) {
        var result = [];
        if (data) {
            for (var i = 0; i < data.length; i++) {
                var series = [];
                for (var key in data[i].datapoints) {
                    var timestamp = parseInt(key);
                    if (data[i].datapoints[key] !== null) {
                        var value = parseFloat(data[i].datapoints[key]);
                        series.push([timestamp, value]);
                    }
                }
                result.push({name: createSeriesName(data[i]), data: series});
            }
        } else {
            result.push({name: 'result', data: []});
        }
        return result;
    }

    function createSeriesName(metric) {
        var scope = metric.scope;
        var name = metric.metric;
        var tags = createTagString(metric.tags);
        return scope + ':' + name + tags;
    }

    function createTagString(tags) {
        var result = '';
        if (tags) {
            var tagString ='';
            for (var key in tags) {
                if (tags.hasOwnProperty(key)) {
                    tagString += (key + '=' + tags[key] + ',');
                }
            }
            if (tagString.length) {
                result += '{' + tagString.substring(0, tagString.length - 1) + '}';
            }
        }
        return result;
    }

    function copyFlagSeries(data) {
        var result;
        if (data) {
            result = {type: 'flags'/*, shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2*/};
            result.data = [];
            var tempID = 0;
            for (var i = 0; i < data.length; i++) {
                var flagData = data[i];
                result.data.push({
                    x: flagData.timestamp,
                    title: 'A',
                    text: formatFlagText(flagData.fields),
                    flagID: '_Flag'+tempID
                });
                tempID++;
            }
        } else {
            result = null;
        }
        return result;
    }

    function formatFlagText(fields) {
        var result = '';
        if (fields) {
            for (var field in fields) {
                if (fields.hasOwnProperty(field)) {
                    result += (field + ': ' + fields[field] + '<br/>');
                }
            }
        }
        return result;
    }

    // Public Service methods
    var service = {
        getLastDataPoint: function(datapoints) {
            if (!datapoints) return;
            return datapoints[Object.keys(datapoints).sort().reverse()[0]];
        },

        getMetricSpecificOptionsInArray: function(metricSpecificOptions) {
            var options = [];
            for (var key in metricSpecificOptions) {
                if (metricSpecificOptions.hasOwnProperty(key)) {
                    options.push({'name': key, 'value': metricSpecificOptions[key]});
                }
            }
            return options;
        },

        augmentExpressionWithControlsData: function(expression, controls) {
			var result = expression;

            for (var controlIndex in controls) {
                var controlName = controls[controlIndex].name;
                var controlValue = controls[controlIndex].value;
                var controlType = controls[controlIndex].type;
                var regularReplace = true;
                if ( controlType === "agDate" ) {
                    controlValue = isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
                    // remove GMT from offset input from
                    if( typeof (controlValue) === "string" && controlValue.indexOf('GMT') >= 0){
                        controlValue = controlValue.replace('GMT','').trim();
                    }

                    if(result.match(new RegExp('\\$' + controlName + '\\$', "g")) !== null) {
                        result = result.replace(new RegExp('\\$' + controlName + '\\$', "g"), controlValue);
                    }


                    var match = null;
                    //Check if it either matches something like $start-7h$ or $start-$diff$$
                    if((match = result.match(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', "g"))) !== null) {
                        controlValue = this.modifyControlValue(controlValue, match[0]);
                        result = result.replace(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', "g"), controlValue);
                    } else if((match = result.match(new RegExp('\\$' + controlName + '\\-\\$[^\\$]*\\$\\$', "g"))) !== null) {
                        match = match[0].substring(1, match[0].length - 1);
                        var subtractControlName = match.match(/\$.*\$/)[0];
                        subtractControlName = subtractControlName.substring(1, subtractControlName.length - 1);
                        var value = this.getControlValueFromName(controls, subtractControlName);

                        controlValue = this.modifyControlValue(controlValue, "-" + value);
                        result = result.replace(new RegExp('\\$' + controlName + '\\-\\$[^\\$]*\\$\\$', "g"), controlValue);
                    }

                } else {
                    controlValue = controlValue === undefined ? "" : controlValue;
                    result = result.replace(new RegExp('\\$' + controlName + '\\$', "g"), controlValue);
                }
            }

            result = result.replace(/(\r\n|\n|\r|\s+)/gm, "");
            return result;
        },

        getControlValueFromName: function(controls, controlName) {
            for(var index in controls) {
                if(controlName === controls[index].name) {
                    return controls[index].value;
                }
            }

            return null;
        },

        modifyControlValue: function(controlValue, controlName) {
            var millisToSubtract = 0;
            var match = controlName.match(/\-\d+[smdh]/)[0];
            var subtract = this.getValue(match);

            if(isNaN(controlValue)) {
                controlValue = this.getValue(controlValue);
                return "-" + (controlValue + subtract) + "s";
            }

            return controlValue - (subtract * 1000);
        },

        getValue: function (timeStr) {
            timeStr = timeStr.substring(1);
            var digits = timeStr.substring(0, timeStr.length - 1);
            var unit = timeStr.substring(timeStr.length - 1);

            var secs = "invalid";
            switch(unit) {
                case "s":
                    secs = parseFloat(digits);
                    break;
                case "m":
                    secs = parseFloat(digits) * 60;
                    break;
                case "h":
                    secs = parseFloat(digits) * 3600;
                    break;
                case "d":
                    secs = parseFloat(digits) * 24 * 3600;
                    break;
                default:
                    console.log("Invalid time unit used.");
            }

            return secs;
        },

        processMetricData: function(data, controls) {
            if (!data) return;

            var processedData = [];
            var updatedMetricList = [];
            var updatedAnnotationList = [];
            var updatedOptionList = JsonFlattenService.unflatten(data.options);
            for (var key in data.metrics) {
                if (data.metrics.hasOwnProperty(key)) {

                    // get metricExpression, and name & color attributes from data
                    var metrics = data.metrics[key];
                    var metricExpression = metrics.expression;
                    var metricSpecificOptions = metrics.metricSpecificOptions;
                    var processedExpression = this.augmentExpressionWithControlsData(metricExpression, controls);

                    if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
                        var processedMetric = {};
                        processedMetric['expression'] = processedExpression;
                        processedMetric['name'] = metrics.name;
                        processedMetric['color'] = metrics.color;
                        processedMetric['metricSpecificOptions'] = this.getMetricSpecificOptionsInArray(metricSpecificOptions);

                        // update metric list with new processed metric object
                        updatedMetricList.push(processedMetric);
                    }
                }
            }

            for (var key in data.annotations) {
                if (data.annotations.hasOwnProperty(key)) {
                    var processedExpression = this.augmentExpressionWithControlsData(data.annotations[key], controls);
                    if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
                        updatedAnnotationList.push(processedExpression);
                    }
                }
            }

            if (updatedMetricList.length > 0) {
                processedData = {
                    updatedMetricList: updatedMetricList,
                    updatedAnnotationList: updatedAnnotationList,
                    updatedOptionList: updatedOptionList
                };

                return processedData;
            }
        },

        copySeriesDataNSetOptions: function(data, metricItem) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                    var series = [];

                    for (var key in data[i].datapoints) {
                        var timestamp = parseInt(key);
                        if (data[i].datapoints[key] !== null) {
                            var value = parseFloat(data[i].datapoints[key]);
                            series.push([timestamp, value]);
                        }
                    }

                    var metricName = (metricItem.name) ? metricItem.name : createSeriesName(data[i]);
                    var metricColor = (metricItem.color) ? metricItem.color : null;
                    var objSeries = {
                        name: metricName,
                        color: metricColor,
                        data: series
                    };
                    var objSeriesWithOptions = ChartOptionService.setCustomOptions(objSeries, metricItem.metricSpecificOptions);

                    result.push(objSeriesWithOptions);
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        },

        populateAnnotations: function(annotationsList, chart) {
            if (annotationsList && annotationsList.length > 0 && chart) {
                for (var i=0; i < annotationsList.length; i++) {
                    this.addAlertFlag(annotationsList[i], chart);
                }
            }
        },

        addAlertFlag: function(annotationExpression, chart) {
            Annotations.query({expression: annotationExpression}, function (data) {
                if (data && data.length > 0) {
                    var forName = createSeriesName(data[0]);
                    var series = copyFlagSeries(data);
                    series.linkedTo = forName;

                    for (var i=0; i < chart.series.length; i++) {
                        if (chart.series[i].name == forName) {
                            series.color = chart.series[i].color;
                            break;
                        }
                    }
                    //TODO: addSeries is a highchart function; it wont work with d3
                    chart.addSeries(series);
                }
            }, function (error) {
                console.log( 'no data found', error.data.message );
            });
        },

        getDatapointRange: function (datapoints) {
            var result = {start: Number.MAX_VALUE, end: Number.MIN_VALUE};
            for (var key in datapoints) {
                if (datapoints.hasOwnProperty(key)) {
                    if (key < result.start) {
                        result.start = key;
                    }
                    if (key > result.end) {
                        result.end = key;
                    }
                }
            }
            return result;
        },

        getAlertFlagExpression: function (metric) {
            if (metric && metric.datapoints) {
                var range = this.getDatapointRange(metric.datapoints);
                var scopeName = metric.scope;
                var metricName = metric.metric;
                var tagData = metric.tags;
                var result = range.start + ":" + range.end + ":" + scopeName + ":" + metricName;
                result += createTagString(tagData);
                result += ":ALERT";
                return result;
            } else {
                return null;
            }
        },

        copySeries: copySeries,

        createSeriesName: createSeriesName,

        copyFlagSeries: copyFlagSeries
    };

    return service;
}]);


/***/ }),
/* 43 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/**
 * Created by liuxizi.xu on 11/3/16.
 */

angular.module('argus.services.charts.dateHandler', [])
.service('DateHandlerService', function () {
    this.timeProcessingHelper = function (timeValue) {
        var result;
        if (timeValue[0] === '-') {
            // apply offset to current time
            timeValue = timeValue.toLowerCase().trim();
            var offsetValue = parseInt(timeValue.substring(1, timeValue.length - 1));
            var offsetUnit = timeValue[timeValue.length - 1];
            result = new Date();
            switch (offsetUnit) {
                case "s":
                    result = result.setSeconds(result.getSeconds() - offsetValue);
                    break;
                case "m":
                    result = result.setMinutes(result.getMinutes() - offsetValue);
                    break;
                case "h":
                    result = result.setHours(result.getHours() - offsetValue);
                    break;
                case "d":
                    result = result.setDate(result.getDate() - offsetValue);
                    break;
            }
            return new Date(result);
        } else {
            // convert timepicker string to Date object
            result = new Date(timeValue);
            return result.toString() === 'Invalid Date' ? new Date() : result;
        }
    };

    this.GMTVerifier = function(timeValue) {
        // true if offset and string with GMT are used for input
        return (timeValue.indexOf('-') !== -1) || (timeValue.indexOf('GMT') !== -1);
    };

    // assuming series' data is sorted already
    this.getStartTimestamp = function(series) {
        var allStartTimestamp = series.map(function(item) {
            return item.data[0][0];
        });
        return Math.min.apply(null, allStartTimestamp);
    };

    this.getEndTimestamp = function(series) {
        var allStartTimestamp = series.map(function(item) {
            return item.data[item.data.length - 1][0];
        });
        return Math.max.apply(null, allStartTimestamp);
    };
});

/***/ }),
/* 44 */
/***/ (function(module, exports) {

/**
 * Created by pfu on 8/31/16.
 */
angular.module('argus.services')
    .service('Controls', ['$routeParams', '$location', function ($routeParams, $location) {
        this.updateControlValue = function(controlName, controlValue) {
            // check $routeParams to override controlValue,
            for (var prop in $routeParams) {
                if (prop == controlName) {
                    return $routeParams[prop];
                }
            }
        };

        this.getUrl = function(controls) {
            var urlStr = '';
            // setup url str from all controls values
            for (var i = 0; i < controls.length; i++) {
                urlStr += controls[i].name + '=' + controls[i].value;
                if (i < controls.length - 1) {
                    urlStr += '&';
                }
            }
            return urlStr;
        };
    }]);

/***/ }),
/* 45 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";


angular.module('argus.services.dashboard', [])
.service('DashboardService', ['$filter', '$compile', '$resource', 'CONFIG', 'VIEWELEMENT', 'Metrics', '$sce', '$http', 'Annotations', 'growl',
    function ($filter, $compile, $resource, CONFIG, VIEWELEMENT, Metrics, $sce, $http, Annotations, growl) {

        this.updateIndicatorStatus = updateIndicatorStatus;

        this.getDashboardById = function(dashboardId){
            return $http.get(CONFIG.wsUrl + 'dashboards/' + dashboardId);
        };

        // TODO: refactor this duplicate code also in: viewMetrics.js $scope function
        // 'populateSeries' below makes same API call, refactor both to separate factories
        this.getMetricData = function(metricExpression) {
            if (!metricExpression) return;

            var metricData =
                $http({
                    method: 'GET',
                    url: CONFIG.wsUrl + 'metrics',
                    params: {'expression': metricExpression}
                }).
                success(function(data, status, headers, config) {
                    if ( data && data.length > 0 ) {
                        return data[0];
                    } else{
                        growl.info('No data found for the metric expression: ' + JSON.stringify(metricExpression));
                        return;
                    }
                }).
                error(function(data, status, headers, config) {
                    growl.error(data.message);
                    return;
                });

            return metricData;
        };

        this.augmentExpressionWithControlsData = function(event, expression, controls) {
            var result = expression;

            for (var controlIndex in controls) {
                var controlName = controls[controlIndex].name;
                var controlValue = controls[controlIndex].value;
                var controlType = controls[controlIndex].type;
                var regularReplace = true;
                if ( controlType === "agDate" ) {
                    controlValue = isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
                    // remove GMT from offset input from
                    if( typeof (controlValue) === "string" && controlValue.indexOf('GMT') >= 0){
                        controlValue = controlValue.replace('GMT','').trim();
                    }
                    
                    if(result.match(new RegExp('\\$' + controlName + '\\$', "g")) !== null) {
                    	result = result.replace(new RegExp('\\$' + controlName + '\\$', "g"), controlValue);
                    } 
                    
                    
                    var match = null;
                    //Check if it either matches something like $start-7h$ or $start-$diff$$
                    if((match = result.match(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', "g"))) != null) {
                    	controlValue = modifyControlValue(controlValue, match[0]);
                    	result = result.replace(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', "g"), controlValue);
                    } else if((match = result.match(new RegExp('\\$' + controlName + '\\-\\$.*\\$\\$', "g"))) != null) {
                    	match = match[0].substring(1, match[0].length - 1);
                    	var subtractControlName = match.match(/\$.*\$/)[0];
                    	subtractControlName = subtractControlName.substring(1, subtractControlName.length - 1);
                    	var value = getControlValueFromName(controls, subtractControlName);
                    	
                    	controlValue = modifyControlValue(controlValue, "-" + value);
                    	result = result.replace(new RegExp('\\$' + controlName + '\\-\\$.*\\$\\$', "g"), controlValue);
                    }
                    
                } else {
                	controlValue = controlValue == undefined ? "" : controlValue;
                    result = result.replace(new RegExp('\\$' + controlName + '\\$', "g"), controlValue);
                }
            }

            result = result.replace(/(\r\n|\n|\r|\s+)/gm, "");
            return result;
        };
        
        
        function getControlValueFromName(controls, controlName) {
        	for(var index in controls) {
        		if(controlName === controls[index].name) {
        			return controls[index].value;
        		}
        	}
        	
        	return null;
        }
        
        function modifyControlValue(controlValue, controlName) {
        	var millisToSubtract = 0;
        	var match = controlName.match(/\-\d+[smdh]/)[0];
        	var subtract = getValue(match);
        	
        	if(isNaN(controlValue)) {
        		controlValue = getValue(controlValue);
        		return "-" + (controlValue + subtract) + "s";
        	}
        	
        	return controlValue - (subtract * 1000);  
        }
        
        function getValue(timeStr) {
        	timeStr = timeStr.substring(1);
        	var digits = timeStr.substring(0, timeStr.length - 1);
        	var unit = timeStr.substring(timeStr.length - 1);
        	
        	var secs = "invalid";
        	switch(unit) {
        		case "s":
        			secs = parseFloat(digits);
        			break;
        		case "m":
        			secs = parseFloat(digits) * 60;
        			break;
        		case "h":
        			secs = parseFloat(digits) * 3600;
        			break;
        		case "d":
        			secs = parseFloat(digits) * 24 * 3600;
        			break;
        		default:
        			console.log("Invalid time unit used.");
        	}
        	
        	return secs;
        }
        

        this.buildViewElement = function(scope, element, attributes, dashboardCtrl, elementType, index, DashboardService, growl) {
            var elementId = 'element_' + elementType + index;
            var smallChartCss = ( attributes.smallchart ) ? 'class="smallChart"' : '';
            element.prepend('<div id=' + elementId + ' ' + smallChartCss +'></div>');

            scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls){
                console.log(dashboardCtrl.getSubmitBtnEventName() + ' event received.');
                populateView(event, controls);
            });

            function populateView(event, controls) {
                // processListData(event, controls)
                // metrics, annotations, options

                var updatedMetricList = [];
                var updatedAnnotationList = [];
                var updatedOptionList = [];

                // TODO: move these 3 items to 'utils' folder
                for (var key in scope.metrics) {
                    if (scope.metrics.hasOwnProperty(key)) {

                        // get metricExpression, and name & color attributes from scope
                        var metrics = scope.metrics[key];
                        var metricExpression = metrics.expression;
                        var metricSpecificOptions = metrics.metricSpecificOptions;
                        var processedExpression = DashboardService.augmentExpressionWithControlsData(event, metricExpression, controls);

                        if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
                            var processedMetric = {};
                            processedMetric['expression'] = processedExpression;
                            processedMetric['name'] = metrics.name;
                            processedMetric['color'] = metrics.color;
                            processedMetric['metricSpecificOptions'] = getMetricSpecificOptionsInArray(metricSpecificOptions);

                            // update metric list with new processed metric object
                            updatedMetricList.push(processedMetric);
                        }
                    }
                }

                for (var key in scope.annotations) {
                    if (scope.annotations.hasOwnProperty(key)) {
                        var processedExpression = DashboardService.augmentExpressionWithControlsData(event, scope.annotations[key],controls);
                        if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
                            updatedAnnotationList.push(processedExpression);
                        }
                    }
                }

                for (var key in scope.options) {
                    if (scope.options.hasOwnProperty(key)) {
                        updatedOptionList.push({name: key, value: scope.options[key]});
                    }
                }

                if (updatedMetricList.length > 0) {
                    DashboardService.populateView(updatedMetricList, updatedAnnotationList, updatedOptionList, elementId, attributes, elementType, scope);
                } else {
                    // growl.error('A valid metric expression(s) is required to display the chart.', {referenceId: 'growl-error'});
                    // $('#' + elementId).hide();
                }
            }

            function getMetricSpecificOptionsInArray(metricSpecificOptions){
                var options = [];
                for (var key in metricSpecificOptions) {
                    if (metricSpecificOptions.hasOwnProperty(key)) {
                        options.push({'name': key, 'value': metricSpecificOptions[key]});
                    }
                }
                return options;
            }
        }

        this.populateView = function(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope) {
            if (!metricList && !divId) return;

            if (!metricList) {
                growl.error('Valid metric expressions are required to display the chart/table.');
                $('#' + divId).hide();
                return;
            }

            if ( elementType === VIEWELEMENT.chart ) {
                populateChart(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope);
            } else {
                var metricExpressionList = getMetricExpressionList(metricList);

                $http({
                    method: 'GET',
                    url: CONFIG.wsUrl + 'metrics',
                    params: {'expression': metricExpressionList}
                }).success(function(data, status, headers, config) {
                    if ( data && data.length > 0) {
                        $('#' + divId).show();

                        if (elementType === VIEWELEMENT.heatmap)
                            updateHeatmap({}, data, divId, optionList, attributes);
                        else if (elementType === VIEWELEMENT.table)
                            updateTable(data, scope, divId, optionList);

                    } else {
                        updateChart({}, data, divId, annotationExpressionList, optionList, attributes);
                        growl.info('No data found for the metric expressions: ' + JSON.stringify(metricExpressionList));
                    }
                }).error(function(data, status, headers, config) {
                    growl.error(data.message);
                    $('#' + divId).hide();
                });
            }
        };

        // ---------------

        // not used: elementType, scope
        function populateChart(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope){

            $('#' + divId).empty();
            $('#' + divId).show();

            var smallChart = attributes.smallchart ? true : false;
            var chartType = attributes.type ? attributes.type : 'LINE';
            var highChartOptions = getOptionsByChartType(CONFIG, chartType, smallChart);

            setCustomOptions(highChartOptions, optionList);

            $('#' + divId).highcharts('StockChart', highChartOptions);
            var chart = $('#' + divId).highcharts('StockChart');

            // show loading spinner & hide 'no data message' during api request
            chart.showLoading();
            chart.hideNoData();

            // define series first; then build list for each metric expression
            var series = [];
            var objMetricCount = {};

            objMetricCount.totalCount = metricList.length;

            for (var i = 0; i < metricList.length; i++) {
                var metricExpression = metricList[i].expression;
                var metricOptions = metricList[i].metricSpecificOptions;

                // make api call to get data for each metric item
                populateSeries(metricList[i], highChartOptions, series, divId, attributes, annotationExpressionList, objMetricCount);
            }
            //populateAnnotations(annotationExpressionList, chart);
        }

        function populateSeries(metricItem, highChartOptions, series, divId, attributes, annotationExpressionList, objMetricCount) {

            $http({
                method: 'GET',
                url: CONFIG.wsUrl + 'metrics',
                params: {'expression': metricItem.expression}
            }).success(function(data, status, headers, config){
                if (data && data.length > 0) {

                    // check to update services dashboard
                    if (attributes.smallchart) {
                        // get last status values & broadcast to 'agStatusIndicator' directive
                        var lastStatusVal = Object.keys(data[0].datapoints).sort().reverse()[0];
                        lastStatusVal = data[0].datapoints[lastStatusVal];
                        // updateServiceStatus(attributes, lastStatusVal);
                        updateIndicatorStatus(attributes, lastStatusVal);
                    }

                    // metric item attributes are assigned to the data (i.e. name, color, etc.)
                    var seriesWithOptions = copySeriesDataNSetOptions(data, metricItem);

                    // add each metric item & data to series list
                    Array.prototype.push.apply(series, seriesWithOptions);

                } else{
                    growl.info('No data found for the metric expression: ' + JSON.stringify(metricItem.expression));
                }

                objMetricCount.totalCount = objMetricCount.totalCount - 1;

                if (objMetricCount.totalCount == 0) {
                    bindDataToChart(divId, highChartOptions, series, annotationExpressionList);
                }
            }).error(function(data, status, headers, config) {
                growl.error(data.message);
                objMetricCount.totalCount = objMetricCount.totalCount - 1;

                if (objMetricCount.totalCount == 0) {
                   bindDataToChart(divId, highChartOptions, series, annotationExpressionList);
                }
            });
        }

        // ---------------

        // 'dataProcessing'
        function copySeriesDataNSetOptions(data, metricItem) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                    var series = [];

                    for (var key in data[i].datapoints) {
                        var timestamp = parseInt(key);
                        if (data[i].datapoints[key] != null) {
                            var value = parseFloat(data[i].datapoints[key]);
                            series.push([timestamp, value]);
                        }
                    }

                    var metricName = (metricItem.name) ? metricItem.name : createSeriesName(data[i]);
                    var metricColor = (metricItem.color) ? metricItem.color : null;
                    var objSeries = {
                        name: metricName,
                        color: metricColor,
                        data: series
                    };
                    var objSeriesWithOptions = setCustomOptions(objSeries, metricItem.metricSpecificOptions);

                    result.push(objSeriesWithOptions);
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        }

        // 'dataProcessing'
        function setCustomOptions(options, optionList){
          for(var idx in optionList) {
                var propertyName = optionList[idx].name;
                var propertyValue = optionList[idx].value;
                var result = constructObjectTree(propertyName, propertyValue);
                copyProperties(result,options);
            }
            return options;
        }

        // 'chartRendering' & 'dataProcessing'
        function bindDataToChart(divId, highChartOptions, series, annotationExpressionList) {
            // bind series data to highchart options
            highChartOptions.series = series;

            // display chart in DOM
            $('#' + divId).highcharts('StockChart', highChartOptions);
            var chart = $('#' + divId).highcharts('StockChart');

            // hide the loading spinner after data loads.
            if (chart) {
                chart.hideLoading();
            }

            // check if data exists, otherwise, show the 'no data' message.
            if ( chart && !chart.hasData() ) {
                chart.showNoData();
            }

            // ----------------

            populateAnnotations(annotationExpressionList, chart);
        }

        // 'dataProcessing'
        function populateAnnotations(annotationsList, chart){
            if (annotationsList && annotationsList.length>0 && chart) {
                for (var i = 0; i < annotationsList.length; i++) {
                    addAlertFlag(annotationsList[i],chart);
                }
            }
        }

        // 'dataProcessing', update to return promise instead
        function addAlertFlag(annotationExpression, chart) {
            Annotations.query({expression: annotationExpression}, function (data) {
                if(data && data.length>0) {
                    var forName = createSeriesName(data[0]);
                    var series = copyFlagSeries(data);
                    series.linkedTo = forName;

                    for(var i=0;i<chart.series.length;i++){
                        if(chart.series[i].name == forName){
                            series.color = chart.series[i].color;
                            break;
                        }
                    }

                    chart.addSeries(series);
                }
            });
        }

        // 'dataProcessing'
        function getMetricExpressionList(metrics){
            var result = [];
            for(var i=0; i < metrics.length; i++){
                result.push(metrics[i].expression);
            }
            return result;
        }

        // 'dataProcessing'
        function copyHeatmapSeries(data, timeSpan) {
            var table = data.map(getHourlyAverage.bind(null, timeSpan));
            for (var i = 0; i < data.length; i++) {
                table[i].push(getAverage(data[i]));
            }
            var dataSeries = [];
            for (var i = 0; i < data.length; i++) {
                for (var j = 0; j < table[0].length; j++) {
                    var intValue = table[data.length - 1 - i][j] ? Math.floor(table[data.length - 1 - i][j]) : null;
                    dataSeries.push([j, i, intValue]);
                }
            }
            return dataSeries;
        }

        // 'dataProcessing'
        function copySeries(data) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                    var series = [];
                    for(var key in data[i].datapoints) {
                        var timestamp = parseInt(key);
                        if(data[i].datapoints[key] !=null){
                            var value = parseFloat(data[i].datapoints[key]);
                            series.push([timestamp, value]);
                        }
                    }
                    result.push({name: createSeriesName(data[i]), data: series});
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        }

        // 'dataProcessing'
        function createSeriesName(metric) {
            var scope = metric.scope;
            var name = metric.metric;
            var tags = createTagString(metric.tags);
            return scope + ':' + name + tags;
        }

        // 'dataProcessing'
        function createTagString(tags) {
            var result = '';
            if (tags) {
                var tagString ='';
                for (var key in tags) {
                    if (tags.hasOwnProperty(key)) {
                        tagString += (key + '=' + tags[key] + ',');
                    }
                }
                if(tagString.length) {
                    result += '{';
                    result += tagString.substring(0, tagString.length - 1);
                    result += '}';
                }
            }
            return result;
        }

        // 'dataProcessing'
        function copyFlagSeries(data) {
            var result;
            if (data) {
                result = {type: 'flags', shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2};
                result.data = [];
                for (var i = 0; i < data.length; i++) {
                    var flagData = data[i];
                    result.data.push({x: flagData.timestamp, title: 'A', text: formatFlagText(flagData.fields)});
                }
            } else {
                result = null;
            }
            return result;
        }

        // 'dataProcessing'
        function formatFlagText(fields) {
            var result = '';
            if (fields) {
                for (var field in fields) {
                    if (fields.hasOwnProperty(field)) {
                        result += (field + ': ' + fields[field] + '<br/>');
                    }
                }
            }
            return result;
        }

        // --------

        // 'chartRendering'
        function updateIndicatorStatus(attributes, lastStatusVal) {
            if (lastStatusVal < attributes.lo) {
                $('#' + attributes.name + '-status').removeClass('red orange green').addClass('red');
            } else if (lastStatusVal > attributes.lo && lastStatusVal < attributes.hi) {
                $('#' + attributes.name + '-status').removeClass('red orange green').addClass('orange');
            } else if (lastStatusVal > attributes.hi) {
                $('#' + attributes.name + '-status').removeClass('red orange green').addClass('green');
            }
        }

        // 'chartRendering'
        function updateChart(config, data, divId, annotationExpressionList, optionList, attributes) {
            var chartType = attributes.type ? attributes.type : 'LINE';

            if (data && data.length > 0) {
                var options = getOptionsByChartType(config,chartType);
                options.series = copySeries(data);
                //options.chart={renderTo: 'container',defaultSeriesType: 'line'};
                setCustomOptions(options,optionList);
                $('#' + divId).highcharts('StockChart', options);
            } else {
                $('#' + divId).highcharts('StockChart', getOptionsByChartType(config, chartType));
            }

            var chart = $('#' + divId).highcharts('StockChart');
            //chart.chart={renderTo: 'container',defaultSeriesType: 'line'};
            //chart.renderTo='container';
            //chart.defaultSeriesType='line';

            populateAnnotations(annotationExpressionList, chart);
        }

        // 'chartRendering'
        function updateTable(data, scope, divId, options) {
            if(data && data.length > 0) {

                var allTimestamps = {};
                for(var i in data) {
                    var dps = data[i].datapoints;
                    for(var timestamp in dps) {
                        if(!allTimestamps[timestamp]) {
                            allTimestamps[timestamp] = [];
                        }
                    }
                }

                var columns = [{title: "timestamp", value: "Timestamp"}];
                for(var i in data) {
                    var dps = data[i].datapoints;
                    if(dps) {
                        columns.push({
                            title: "value" + i,
                            value: createSeriesName(data[i])
                        });

                        for(var timestamp in allTimestamps) {
                            var values = allTimestamps[timestamp];
                            if(dps[timestamp]) {
                                values.push(parseFloat(dps[timestamp]));
                            } else {
                                values.push(undefined);
                            }
                            allTimestamps[timestamp] = values;
                        }
                    }
                }

                var tData = [];
                for(var timestamp in allTimestamps) {
                    var obj = {
                            timestamp: parseInt(timestamp),
                            date: $filter('date')(timestamp, "medium")
                    };

                    for(var i in columns) {
                        if(columns[i].title !== "timestamp")
                            obj[columns[i].title] = allTimestamps[timestamp][i-1];
                    }
                    tData.push(obj);
                }

                var tableConfig = {
                        itemsPerPage: 10,
                        fillLastPage: true
                };

                for(var i in options) {
                    var option = options[i];
                    if(option.name && option.value)
                        tableConfig[option.name] = option.value;
                }


                scope.tData = tData;
                scope.config = tableConfig;

                var html = '<div style="overflow-x: scroll"><table class="table table-striped table-header-rotated" at-table at-paginated at-list="tData" at-config="config">';

                html += '<thead>';
                html += '<tr>';
                for(var i in columns) {
                    html += '<th class="rotate-45" at-attribute="' + columns[i].title + '"><div><span>' + columns[i].value + '</span></div></th>';
                }
                html += '</tr>';
                html += '</thead>';

                html += '<tbody>';
                html += '<tr>';

                for(var i in columns) {
                    if(columns[i].title === 'timestamp')
                        html += '<td at-sortable at-attribute="' + columns[i].title + '">{{ item.date }}</td>';
                    else
                        html += '<td at-sortable at-attribute="' + columns[i].title + '">{{ item.' + columns[i].title + '}}</td>';
                }

                html += '</tr>';
                html += '</tbody>';

                html += '</table></div>';

                html += '<at-pagination at-list="tData" at-config="config"></at-pagination>';

                $("#" + divId).empty();
                $compile($("#" + divId).prepend(html))(scope);
            }
        }

        // 'chartRendering'
        function updateHeatmap(config, data, divId, optionList, attributes) {
            if(data && data.length>0) {
                var top = attributes.top? parseInt(attributes.top) : data.length;
                var options = getOptionsByHeatmapType(config, top);
                data.sort(compareAverage);
                data = data.slice(0, Math.min(top, data.length));
                var orgAxis = data.map(createSeriesName);
                var timeSpan = getTimeSpan(data);
                var timeAxis = getTimeAxis(timeSpan);
                var dataSeries = copyHeatmapSeries(data, timeSpan);
                options.series[0].data = dataSeries;
                options.xAxis.categories = timeAxis;
                options.yAxis.categories = orgAxis.reverse();
                setCustomOptions(options,optionList);
                $('#' + divId).highcharts(options);
            }else {
                $('#' + divId).highcharts('StockChart', getOptionsByChartType(config, 'LINE'));
            }
        }

        // 'chartOptions'
        function getOptionsByChartType(config, chartType, smallChart){
            var options = config ? angular.copy(config) : {};
            options.legend = {
                enabled: true,
                maxHeight: 62,
                itemStyle: {
                    fontWeight: 'normal',
                    fontSize: '10px'
                },
                navigation : {
                    style : {
                        fontWeight: 'normal',
                        fontSize: '10px'
                    }
                }
            };
            options.credits = {enabled: false};
            options.rangeSelector = {selected: 1, inputEnabled: false};
            options.xAxis = {
                type: 'datetime',
                ordinal: false
            };

            options.lang = {
                loading: '',    // override default 'Loading...' msg from displaying under spinner img.
                noData: 'No Data to Display'
            };

            // loading spinner for graph
            options.loading = {
                labelStyle: {
                    top: '25%',
                    backgroundImage: 'url("img/ajax-loader.gif")',
                    backgroundSize: '80px 80px',
                    backgroundRepeat: 'no-repeat',
                    display: 'inline-block',
                    width: '80px',
                    height: '80px',
                    backgroundColor: '#FFF'
                }
            };

            if(chartType && chartType.toUpperCase() === 'AREA'){
                options.plotOptions = {series: {animation: false}};
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
            }else  if(chartType && chartType.toUpperCase() === 'STACKAREA'){
                options.plotOptions = {
                    area: {
                        stacking: 'normal',
                       // lineWidth: 1.5,
                        dataGrouping: {
                            enabled: true//,
                          //  groupPixelWidth: 2
                        },
                        animation: false,
                        marker: {
                            enabled: false
                        }
                    }
                };
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
            }
            else {
                options.plotOptions = {series: {animation: false}};
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
            }

            // override options for a 'small' chart, e.g. 'Services Status' dashboard
            if ( smallChart ) {
                options.legend.enabled = false;
                options.rangeSelector.enabled = false;

                options['scrollbar'] = {enabled: false};
                options['navigator'] = {enabled: false};

                options.chart.height = '120';
                options.chart.borderWidth = 0;

                // reset loading options, no spinner required
                options.lang = {
                    loading: 'Loading...'
                };
                options.loading = {};
            }

            return options;
        }

        function getOptionsByHeatmapType(config, top){
            var options = config ? angular.copy(config) : {};
            options.credits = {enabled: false};
            options.chart = {
                type: 'heatmap',
                marginTop: 0,
                marginBottom: 60,
                height: 40 * top
            };
            options.title = {text: ''};
            options.xAxis = {
                categories: null
            };
            options.yAxis = {
                categories: null,
                title: null,
                labels: {
                }
            };
            options.colorAxis = {
                dataClasses: [{
                    from: 0,
                    to: 300,
                    color: '#00FF00'
                },{
                    from:300,
                    to:400,
                    color:'#FF8000'
                },{
                    from:400,
                    color:'#FF0040'
                }]
            };
            options.legend = {enabled: true};
            options.tooltip = {enabled: false};
            options.series = [{
                name: '',
                borderWidth: 1,
                data: null,
                dataLabels: {
                    enabled: true,
                    color: 'black',
                    style: {
                        textShadow: 'none',
                        HcTextStroke: null
                    }
                }
            }];
            return options;
        }

        // 'chartTools' --> moved to 'ChartRenderingService.chartTools'
        function getTimeAxis(timeSpan) {
            var hours = [
                '12AM', '1AM', '2AM', '3AM', '4AM', '5AM',
                '6AM', '7AM', '8AM', '9AM', '10AM', '11AM',
                '12PM', '1PM', '2PM', '3PM', '4PM', '5PM',
                '6PM', '7PM', '8PM', '9PM', '10PM', '11PM'
            ];
            var axis = [];
            var firstHour = (new Date(timeSpan.begin)).getHours();
            for (var i = 0; i < timeSpan.span; i++) {
                axis.push(hours[(firstHour + i) % 24]);
            }
            axis.push('<b><i>Average</i></b>');
            return axis;
        }

        // --------

        // 'utilService'

        function compareAverage(a,b) {
            if (getAverage(a) < getAverage(b)) return 1;
            if (getAverage(a) > getAverage(b)) return -1;
            return 0;
        }

        function getTimeSpan(data) {
            var begin = 9999999999999;
            var end = 0;
            for (var i = 0; i < data.length; i++) {
                for (var time in data[i].datapoints) {
                    begin = Math.min(begin, parseInt(time));
                    end = Math.max(end, parseInt(time));
                }
            }
            var span = Math.floor(end/1000/60/60) - Math.floor(begin/1000/60/60) + 1;
            return {begin: begin, end: end, span: span};
        }

        function getAverage(data) {
            var total = 0;
            var count = 0;
            for (var time in data.datapoints) {
                total += parseInt(data.datapoints[time]);
                count += 1;
            }
            if (count > 0)
                return total / count;
            else
                return 0;
        }

        function getHourlyAverage(timeSpan, data) {
            var sums = Array.apply(null, Array(timeSpan.span)).map(Number.prototype.valueOf,0);
            var counts = Array.apply(null, Array(timeSpan.span)).map(Number.prototype.valueOf,0);
            var pivotHour = Math.floor(timeSpan.begin / 1000 / 60 / 60);
            for (var time in data.datapoints) {
                var hour = Math.floor(parseInt(time) / 1000 / 60 / 60);
                sums[hour - pivotHour] += parseInt(data.datapoints[time]);
                counts[hour - pivotHour] += 1;
            }
            var avgs = [];
            for (var i = 0; i < timeSpan.span; i++) {
                if (counts[i] > 0) avgs.push(sums[i] / counts[i]);
                else avgs.push(null);
            }
            return avgs;
        }

        function copyProperties(from, to){
            for (var key in from) {
                if (from.hasOwnProperty(key)) {
                    if(!to[key] || typeof from[key] == 'string' || from[key] instanceof String ){//if from[key] is not an object and is last property then just copy so that it will overwrite the existing value
                        to[key]=from[key];
                    }else{
                        copyProperties(from[key],to[key]);
                    }
                }
            }
        }

        function constructObjectTree(name, value) {
            var result = {};
            var index = name.indexOf('.');
            if (index == -1) {
                result[name] = getParsedValue(value);
                return result;
            } else {
                var property = name.substring(0, index);
                result[property] = constructObjectTree(name.substring(index + 1), value);
                return result;
            }
        }

        function getParsedValue(value){

            if(value instanceof Object || value.length==0){
                return value;
            }

            if(value=='true'){
                return true;
            }else if(value=='false'){
                return false;
            }else if(!isNaN(value)){
                return parseInt(value);
            }
            return value;
        }
    }]);


/***/ }),
/* 46 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/**
 * Created by liuxizi.xu on 2/2/17.
 */


angular.module('argus.services.downloadHelper', [])
.service('DownloadHelper', function () {
    this.downloadFile = function (data, filename) {
        var url = window.URL.createObjectURL(new Blob([data]));
        var a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.target = '_blank';
        a.click();
    };
});

/***/ }),
/* 47 */
/***/ (function(module, exports) {

angular.module('argus.services.alerts', [])
.factory('Alerts', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId', {}, {
            query: {method: 'GET', params: {alertId: ''}, isArray: true},
            update: {method: 'PUT'},
            getMeta: {method: 'GET', url: CONFIG.wsUrl + 'alerts/meta?shared=true', isArray: true},
            getUsers: {method: 'GET', url: CONFIG.wsUrl + 'alerts/meta?shared=false', isArray: true}
        });
    }]);


/***/ }),
/* 48 */
/***/ (function(module, exports) {

angular.module('argus.services.annotations', [])
.factory('Annotations', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'annotations', {}, {
        query: {method: 'GET', isArray: true}
    });
}]);


/***/ }),
/* 49 */
/***/ (function(module, exports) {

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

/***/ }),
/* 50 */
/***/ (function(module, exports) {

angular.module('argus.services.batches', ['ngResource'])
.factory('Batches', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'batches/:batchId', {}, {
        query: {method: 'GET', params: {batchId: ''}}
    });
}]);

/***/ }),
/* 51 */
/***/ (function(module, exports) {

angular.module('argus.services.dashboards', [])
.factory('Dashboards', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'dashboards/:dashboardId', {}, {
        query: {method: 'GET', params: {dashboardId: ''}, isArray: true},
        update: {method: 'PUT'},
        getMeta: {method: 'GET', url: CONFIG.wsUrl + 'dashboards/meta', isArray: true}
    });
}]);


/***/ }),
/* 52 */
/***/ (function(module, exports) {

angular.module('argus.services.history', [])
.factory('History', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'audit/entity/:id', {id: '@id', limit: '20'}, {});
}]);

/***/ }),
/* 53 */
/***/ (function(module, exports) {

angular.module('argus.services.jobexecutiondetails', [])
.factory('JobExecutionDetails', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'history/job/:id', {id: '@id', limit: '20'}, {});
}]);

/***/ }),
/* 54 */
/***/ (function(module, exports) {

angular.module('argus.services.metrics', [])
.factory('Metrics', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'metrics', {}, {
        query: {method: 'GET', isArray: true},
        downloadCSV: {
            method: 'GET',
            headers: {"Accept": "application/ms-excel"},
            // override angular http response default transform
            transformResponse: [function(data) {return [data]}],
            isArray: true
        }
    });
}]);


/***/ }),
/* 55 */
/***/ (function(module, exports) {

angular.module('argus.services.namespace', [])
.factory('Namespace', ['$resource', 'CONFIG', function ($resource, CONFIG) {
	return $resource(CONFIG.wsUrl + 'namespace/:namespaceId', {}, {
        update: {method: 'PUT'}
    });
}]);

/***/ }),
/* 56 */
/***/ (function(module, exports) {

angular.module('argus.services.notifications', [])
.factory('Notifications', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/notifications/:notificationId', {}, {
            query: {method: 'GET', params: {notificationId: ''}, isArray: true},
            save: {method: 'POST', isArray: true},
            update: {method: 'PUT'}
        });
    }]);

/***/ }),
/* 57 */
/***/ (function(module, exports) {

angular.module('argus.services.admin.reinstateuser', ['ngResource'])
.factory('ReinstateUser', ['$resource', 'CONFIG',
		function ($resource, CONFIG) {
				return $resource(CONFIG.wsUrl + 'management/reinstateuser', {}, {
						update: {method: 'PUT'}
				});
		}
]);

/***/ }),
/* 58 */
/***/ (function(module, exports) {

angular.module('argus.services.triggers', [])
.factory('Triggers', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/triggers/:triggerId', {}, {
            query: {method: 'GET', params: {triggerId: ''}, isArray: true},
            save: {method: 'POST', isArray: true},
            update: {method: 'PUT'}
        });
    }]);

/***/ }),
/* 59 */
/***/ (function(module, exports) {

angular.module('argus.services.triggersmap', [])
.factory('TriggersMap', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/notifications/:notificationId/triggers/:triggerId', {}, {
            map: {method: 'POST'},
            unmap: {method: 'DELETE'}
        });
    }]);


/***/ }),
/* 60 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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
/**
 * Created by liuxizi.xu on 9/2/16.
 */


angular.module('argus.services.inputTracker', [])
.service('InputTracker', ['Storage', function (Storage) {
    this.getDefaultValue = function(fieldName, defaultVal) {
        return Storage.get(fieldName) === null ? defaultVal : Storage.get(fieldName);
    };

    this.updateDefaultValue = function(fieldName, defaultVal, val) {
        var result = val === null ? defaultVal : val;
        Storage.set(fieldName, result);
    };
}]);


/***/ }),
/* 61 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/**
 * Created by liuxizi.xu on 12/12/16.
 */

// source: http://stackoverflow.com/questions/19098797/fastest-way-to-flatten-un-flatten-nested-json-objects


angular.module('argus.services.jsonFlatten', [])
.service('JsonFlattenService', function() {
    this.unflatten = function(data) {
        if (Object(data) !== data || Array.isArray(data))
            return data;
        var regex = /\.?([^.\[\]]+)|\[(\d+)\]/g,
            resultholder = {};
        for (var p in data) {
            var cur = resultholder,
                prop = "",
                m;
            while (m = regex.exec(p)) {
                cur = cur[prop] || (cur[prop] = (m[2] ? [] : {}));
                prop = m[2] || m[1];
            }
            cur[prop] = data[p];
        }
        return resultholder[""] || resultholder;
    };

    this.flatten = function(data) {
        var result = {};
        function recurse (cur, prop) {
            if (Object(cur) !== cur) {
                result[prop] = cur;
            } else if (Array.isArray(cur)) {
                for(var i=0, l=cur.length; i<l; i++)
                    recurse(cur[i], prop + "[" + i + "]");
                if (l == 0)
                    result[prop] = [];
            } else {
                var isEmpty = true;
                for (var p in cur) {
                    isEmpty = false;
                    recurse(cur[p], prop ? prop+"."+p : p);
                }
                if (isEmpty && prop)
                    result[prop] = {};
            }
        }
        recurse(data, "");
        return result;
    };
});

/***/ }),
/* 62 */
/***/ (function(module, exports) {

angular.module('argus.services.search', [])
.service('SearchService', ['$q', '$http', 'CONFIG', function($q, $http, CONFIG) {
    this.search = function(searchParams) {
        if (!searchParams) return;
        
        // TODO: refactor api call to a separate factory for metric queries
        var request = $http({
            method: 'GET',
            url: CONFIG.wsUrl + 'discover/metrics/schemarecords',
            params: searchParams,
            timeout: 30000
        });

        return request;
    };

    this.processResponse = function(response) {
        return response.data;
    };

    /*
    this.processResponses = function(responses) {
        return responses
            .filter(function(res){  //filters successful requests
                return (res.state === 'fulfilled');
            })
            .map(function(res) {    //maps each response
                var resCategory = res.value.config.headers.category;
                res.value.data.map(function(datum) {
                    //adds the category (namespace, scope, metric, etc.)
                    //and expression value to each search result
                    datum['category'] = resCategory;
                    datum.expression = buildExpression(datum);
                });
                res.value.data = res.value.data.filter(function(datum, index, arr) {
                    //filters results with unique expressions
                    var thisExpression = datum.expression;
                    index++;
                    while (index < arr.length) {
                        var otherExpresssion = arr[index].expression;
                        if (thisExpression === otherExpresssion) {
                            return false;   //expression match found
                        }
                        index++;
                    }
                    return true;    //expression match not found
                });
                return res.value.data;  //leaves only the data obj
            })
            .reduce(function(a, b) {
                //flattens the 2D list of responses
                return a.concat(b);
            });
    };

    //Returns a complete expression for a search result depending on
    //which category it belongs to (namespace, scope, metric, etc.)
    var buildExpression = function(searchResult) {
        if (searchResult != null) {
            var categories = ['namespace', 'scope', 'metric', 'tagk', 'tagv'];
            var searchCategory = searchResult.category;
            var expressionArray = [];
            
            for (var i = 0; i < categories.length; i++) {
                var expressionComponent = searchResult[categories[i]];
                expressionArray.push(expressionComponent);
                if (categories[i] === searchCategory) {
                    break;  //Reached the end of expression
                }
            }
            
            var expressionString = expressionArray.join(":");
            return expressionString;
        }
    };
    */
}]);


/***/ }),
/* 63 */
/***/ (function(module, exports) {

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

/***/ }),
/* 64 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
/**
 * Created by liuxizi.xu on 1/9/17.
 */


angular.module('argus.services.tableListService', [])
.service('TableListService', ['$sessionStorage', function ($sessionStorage) {
    this.deleteItemFromListHelper = function (itemList, itemToDelete) {
        return itemList.filter(function(element) {
            return element.id !== itemToDelete.id;
        });
    };

    this.getListUnderTab = function (allItems, shared, userName) {
        var i, result = [];
        var totNum = allItems.length;
        if(shared) {
            for(i = 0; i < totNum; i++) {
                if(allItems[i].shared) {
                    result.push(allItems[i]);
                }
            }
        } else {
            for(i = 0; i < totNum; i++) {
                if (allItems[i].ownerName === userName) {
                    result.push(allItems[i]);
                }
            }
        }
        return result;
    };

    this.addItemToTableList = function (allList, propertyType, item, userName) {
        $sessionStorage[propertyType].cachedData.push(item);
        if (item.shared) allList.sharedList.push(item);
        if (item.ownerName === userName) allList.usersList.push(item);
        return allList;
    };

    this.deleteItemFromTableList = function (allList, propertyType, item, userName) {
        $sessionStorage[propertyType].cachedData = this.deleteItemFromListHelper($sessionStorage[propertyType].cachedData, item);
        if (item.shared) allList.sharedList = this.deleteItemFromListHelper(allList.sharedList, item);
        if (item.ownerName === userName) allList.usersList = this.deleteItemFromListHelper(allList.usersList, item);
        return allList;
    };
}]);

/***/ }),
/* 65 */
/***/ (function(module, exports) {

angular.module('argus.services.tags', [])
.service('Tags', ['CONFIG', '$http', '$q', function(CONFIG, $http, $q) {
		this.getDropdownOptions = function(key) {
				var request = $http({
						method: 'GET',
						url: CONFIG.wsUrl + 'schema/tags',
						params: {
							tagk: key
						}
				});
				return request;
		};
}]);


/***/ }),
/* 66 */
/***/ (function(module, exports) {

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


/***/ }),
/* 67 */
/***/ (function(module, exports) {

angular.module('argus.services.utils', [])
.service('UtilService', [function() {
    'use strict';

    var options = {
        assignController: function(controllers) {
            if (!controllers) return;
            for (var i=0; i < controllers.length; i++) {
                if (controllers[i])
                    return controllers[i];
            }
        },

        copyProperties: function(from, to) {
            for (var key in from) {
                if (from.hasOwnProperty(key)) {
                    //if from[key] is not an object and is last property then just copy so that it will overwrite the existing value
                    if (!to[key] || typeof from[key] == 'string' || from[key] instanceof String ) {
                        to[key] = from[key];
                    } else {
                        copyProperties(from[key],to[key]);
                    }
                }
            }
        },

        constructObjectTree: function(name, value) {
            var result = {};
            var index = name.indexOf('.');

            if (index == -1) {
                result[name] = this.getParsedValue(value);
                return result;
            } else {
                var property = name.substring(0, index);
                result[property] = this.constructObjectTree(name.substring(index + 1), value);
                return result;
            }
        },

        getParsedValue: function(value) {
            if (value instanceof Object || value.length == 0) {
                return value;
            }
            if (value == 'true') {
                return true;
            } else if (value == 'false') {
                return false;
            } else if (!isNaN(value)) {
                return parseInt(value);
            }
            return value;
        }
    };

    return options;
}]);


/***/ }),
/* 68 */
/***/ (function(module, exports) {

angular.module('argus.constants', [])
.constant('VIEWELEMENT', {
	chart: 'chart',
	heatmap: 'heatmap',
	table: 'table'
})

.constant('CHARTTYPE', {
	line: 'line',
	area: 'area'
})

.constant('BATCH_CHART_OPTIONS', {
    credits: {
        enabled: false
    },
    rangeSelector: {
        selected: 1,
        inputEnabled: false
    },
    xAxis: {
        type: 'datetime',
        ordinal: false
    },
    lang: {
        noData: 'No Data to Display'
    },
    legend: {
        enabled: true,
        maxHeight: 62,
        itemStyle: {
            fontWeight: 'normal',
            fontSize: '10px'
        },
        navigation: {
            style: {
                fontWeight: 'normal',
                fontSize: '10px'
            }
        }
    },
    plotOptions: {
        series: {
            animation: false,
            connectNulls: true
        },
        line: {
            gapSize: 1.5
        }
    },
    chart: {
        animation: false,
        borderWidth: 1,
        borderColor: 'lightGray',
        borderRadius: 5
    }
});


/***/ }),
/* 69 */
/***/ (function(module, exports) {

angular.module('argus.filters', [])

.filter('isEmpty', function() {
	return function(object) {
		return angular.equals({}, object);
	}
})

.filter('duration', function () {
	return function (duration) {
	  var seconds = Math.floor(( duration / 1000 ) % 60),
	    minutes = Math.floor(( duration / (1000 * 60) ) % 60),
	    hours = (duration < 86400000) ? Math.floor(( duration / (1000*60*60) ) % 24) : Math.floor( duration / (1000*60*60) );

	  hours = (hours < 1) ? '' : hours + "h\u00A0";
	  minutes = (minutes < 1) ? '' : minutes + "m\u00A0";
	  seconds = (seconds < 1) ? '' : seconds + "s";

	  if (seconds === '' && minutes === '' && hours === '') {
	    seconds = '0s';
	  }

	  return hours + minutes + seconds;
	};
})

.filter('duration_fractional', function() {
	return function(original) {
	  var s = original;
	  var ms = s % 1000;
	  s = (s - ms) / 1000;
	  var secs = s % 60;
	  s = (s - secs) / 60;
	  var mins = s % 60;
	  var hrs = (s - mins) / 60;

	  if ( hrs > 0 ) {
	    return Math.floor(10 * original / (60*60*1000) ) / 10 + "h";
	  } else if ( mins > 0 ) {
	    return Math.floor( 10 * original / (60*1000) ) / 10 + "m";
	  } else if ( original > 10) {
	    return Math.floor( original / 10 ) / 100 + "s";
	  } else if ( original > 0) {
	    return Math.floor( original ) + "ms";
	  } else {
	    return 0;
	  }
	};
})

.filter('bytes', function() {
	return function(input) {
	  if (input === undefined || input === null) return 'n/a';
	  if (input === 0) return '0';

	  var sizes = ['B', 'KB', 'MB', 'GB', 'TB'];

	  var sizeIndex = Math.floor(Math.log(input) / Math.log(1024));
	  var outputNum = Math.round(100 * input / Math.pow(1024, sizeIndex)) / 100;

	  return outputNum + ' ' + sizes[sizeIndex];
	};
})

.filter('base10si', function() {
	return function(input) {
	  if (input === undefined || input === null) return 'n/a';
	  if (input === 0) return '0';

	  var sizes = ['', 'K', 'M', 'G', 'T'];

	  var sizeIndex = Math.floor(Math.log(input) / Math.log(1000));
	  var outputNum = Math.round(100 * input / Math.pow(1000, sizeIndex)) / 100;

	  return outputNum + ' ' + sizes[sizeIndex];
	};
})

.filter('nullable_decimal', function() {
	return function(input) {
	  if (input === undefined || input === null) return 'n/a';
	  return parseFloat(input).toFixed(2);
	};
})

.filter('_date_', function() {
	return function(input) {
	  if (input === undefined || input === null || input === 0) return 'n/a';
	  return moment(input).format('MMM Do, YYYY');
	};
})

.filter('_time_', function() {
	return function(input) {
	  if (input === undefined || input === null || input === 0) return 'n/a';
	  return moment(input).format('h:mm:ss a');
	};
})

// all date/times come back from the server in UTC - reformat to local (browser) time
.filter('_date_time_', function() {
	return function(input) {
	  if (input === undefined || input === null || input === 0) return 'n/a';
	  return moment(input).format('MMM Do, YYYY h:mm:ss a');
	};
})

// short version of date_time, format:  Jan 10th
.filter('_short_date_time_', function() {
	return function(input) {
	  if (input === undefined || input === null || input === 0) return 'n/a';
	  return moment(input).format('MMM Do');
	};
})

.filter('_date_time_from_timestamp_', function() {
	return function(input, asRangeFrom) {
	  var num = parseInt(input);
	  if ( isNaN(num) ) return "n/a";
	  var time = moment(num);
	  var formatString = 'MMM Do, YYYY h:mm a';
	  var dateComponent = true;
	  var yearComponent = true;
	  var hourComponent = true;
	  var minuteComponent = true;

	  if ( asRangeFrom ) {
	    var asRangeNum = parseInt(asRangeFrom);
	    if ( !isNaN(asRangeNum) ) {
	      var fromTime = moment(asRangeNum);
	      if(time.year() == fromTime.year()){
	        yearComponent = false;
	      }
	      if ( time.year() == fromTime.year() && time.date() == fromTime.date() && time.month() == fromTime.month() ) {
	        dateComponent = false;
	      }
	    }
	  }

	  var formatString = ( dateComponent ? 'MMM Do' : '') + (dateComponent&&yearComponent ? ", YYYY " : (dateComponent?" ":"")) + (hourComponent ? 'h' : '') + ( minuteComponent ? ':mm' : '' ) + ( hourComponent || minuteComponent ? ' a' : '' );
	  return time.format(formatString);
	};
})

.filter('truncateString', function() {
	return function(input, length) {
		if ( input && input.length > length ) {
			return input.substring(0,length) + "\u2026";
		}
	return input;
	};
})

.filter('capitalize', function() {
	return function(input, scope) {
		if (input != null) {
			input = input.toLowerCase();
			return input.substring(0,1).toUpperCase() + input.substring(1);
		}
	}
})

.filter('urlencode', function () {
	return window.encodeURIComponent;
})

.filter('newline', function() {
    return function(data) {
        if (data && data.length > 0) {
            var retvalue = data.replace(/</g, '&lt');
            retvalue = retvalue.replace(/>/g, '&gt');
            retvalue = retvalue.replace(/\n/g, '<br/>');
            return retvalue;
        } else return "";
    }
})

.filter('trustedhtml', ['$sce', function ($sce) {
	return function (text) {
		return $sce.trustAsHtml(text);
	};
}])

;

/***/ }),
/* 70 */
/***/ (function(module, exports, __webpack_require__) {

"use strict";
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



/* Core Argus module */

/*global angular:false, $:false */
angular.module('argus', [
  // Dependencies
  'ngRoute',
  'ngAnimate',
  'ngStorage',
  'angular-growl',
  'angularUtils.directives.dirPagination',
  'angulartics',
  'ui.bootstrap',
  'ui.bootstrap.datetimepicker',
  'argus.urlConfig',
  'argus.config',
  'argus.filters',
  'argus.constants',
  'argus.services',
  'argus.controllers',
  'argus.directives',
  'argus.directives.breadcrumbs',
  'argus.directives.confirm',
  'argus.directives.dashboardResource',
  'argus.directives.controls.dashboard',
  'argus.directives.controls.date',
  'argus.directives.controls.dropdown',
  'argus.directives.controls.submit',
  'argus.directives.controls.text',
  'argus.directives.charts.chart',
  'argus.directives.charts.lineChart',
  'argus.directives.charts.flags',
  'argus.directives.charts.heatmap',
  'argus.directives.charts.metric',
  'argus.directives.charts.option',
  'argus.directives.charts.statusIndicator',
  'argus.directives.charts.table',
  'argus.directives.headerMenu'
]).run(['$http', '$templateCache', function ($http, $templateCache) {
  "use strict";
  // template caching
  $http.get('js/templates/breadcrumbs.html', {cache: $templateCache});
  $http.get('js/templates/login.html', {cache: $templateCache});
  $http.get('js/templates/alert-list.html', {cache: $templateCache});
  $http.get('js/templates/alert-detail.html', {cache: $templateCache});
  $http.get('js/templates/dashboard-list.html', {cache: $templateCache});
  $http.get('js/templates/dashboard-detail.html', {cache: $templateCache});
  $http.get('js/templates/viewmetrics.html', {cache: $templateCache});
  $http.get('js/templates/charts/topToolbar.html', {cache: $templateCache});
}]);

// Services
angular.module('argus.services', [
  'argus.services.admin.reinstateuser',
  'argus.services.alerts',
  'argus.services.annotations',
  'argus.services.asyncMetrics',
  'argus.services.auth',
  'argus.services.batches',
  'argus.services.breadcrumbs',
  'argus.services.charts.options',
  'argus.services.charts.rendering',
  'argus.services.charts.tools',
  'argus.services.charts.dataProcessing',
  'argus.services.charts.dateHandler',
  'argus.services.dashboard',
  'argus.services.dashboards',
  'argus.services.history',
  'argus.services.interceptor',
  'argus.services.inputTracker',
  'argus.services.jobexecutiondetails',
  'argus.services.metrics',
  'argus.services.namespace',
  'argus.services.notifications',
  'argus.services.search',
  'argus.services.storage',
  'argus.services.tags',
  'argus.services.triggers',
  'argus.services.triggersmap',
  'argus.services.utils',
  'argus.services.jsonFlatten',
  'argus.services.tableListService',
  'argus.services.downloadHelper'
]);

// Controllers
angular.module('argus.controllers', [
  'argus.services',
  'argus.controllers.about',
  'argus.controllers.admin',
  'argus.controllers.alerts',
  'argus.controllers.alerts.detail',
  'argus.controllers.batches',
  'argus.controllers.dashboards',
  'argus.controllers.dashboards.detail',
  'argus.controllers.login',
  'argus.controllers.main',
  'argus.controllers.metricelements',
  'argus.controllers.namespace',
  'argus.controllers.viewelements',
  'argus.controllers.viewMetrics'
]);

// Directives
angular.module('argus.directives', [
]);

// Filters
angular.module('argus.filters', [
]);


//require files for webpack bundle
//config
__webpack_require__(1);
__webpack_require__(0);

//utils
__webpack_require__(68);
__webpack_require__(69);

//controllers
__webpack_require__(2);
__webpack_require__(3);
__webpack_require__(4);
__webpack_require__(5);
__webpack_require__(6);
__webpack_require__(7);
__webpack_require__(8);
__webpack_require__(9);
__webpack_require__(10);
__webpack_require__(11);
__webpack_require__(12);
__webpack_require__(13);
__webpack_require__(14);

//services
__webpack_require__(37);
__webpack_require__(38);
__webpack_require__(44);
__webpack_require__(45);
__webpack_require__(46);
__webpack_require__(60);
__webpack_require__(62);
__webpack_require__(63);
__webpack_require__(64);
__webpack_require__(65);
__webpack_require__(66);
__webpack_require__(67);
__webpack_require__(61);

__webpack_require__(39);
__webpack_require__(40);
__webpack_require__(41);
__webpack_require__(42);
__webpack_require__(43);

__webpack_require__(47);
__webpack_require__(49);
__webpack_require__(48);
__webpack_require__(50);
__webpack_require__(51);
__webpack_require__(52);
__webpack_require__(53);
__webpack_require__(54);
__webpack_require__(55);
__webpack_require__(56);
__webpack_require__(57);
__webpack_require__(58);
__webpack_require__(59);

//directives
__webpack_require__(35);
__webpack_require__(36);
__webpack_require__(20);
__webpack_require__(34);

__webpack_require__(29);
__webpack_require__(30);
__webpack_require__(31);
__webpack_require__(32);
__webpack_require__(33);

__webpack_require__(21);
__webpack_require__(24);
__webpack_require__(22);
__webpack_require__(23);
__webpack_require__(25);
__webpack_require__(26);
__webpack_require__(27);
__webpack_require__(28);

__webpack_require__(15);
__webpack_require__(19);
__webpack_require__(16);
__webpack_require__(18);
__webpack_require__(17);


/***/ })
/******/ ]);