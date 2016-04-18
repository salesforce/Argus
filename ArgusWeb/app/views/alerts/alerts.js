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

var argusAlerts = angular.module('argusAlerts', [
    'ngResource'
]);

argusAlerts.controller('AlertListCtrl', ['Storage', '$scope', 'growl', 'Alerts',
    function (Storage, $scope, growl, Alerts) {
		$scope.searchText = Storage.get("alerts-searchText") == null ? "" : Storage.get("alerts-searchText");
        $scope.alerts = Alerts.query();
        $scope.itemsPerPageOptions=[5,10,15,25,50,100,200];
        $scope.itemsPerPage = Storage.get("alerts-itemsPerPage") == null ? $scope.itemsPerPageOptions[1] : Storage.get("alerts-itemsPerPage");
        
        $scope.addAlert = function () {
            var alert = {
                name: 'new-alert-' + Date.now(),
                expression: "-1h:scope:metric{tagKey=tagValue}:avg",
                cronEntry: "0 */4 * * *"
            };
            Alerts.save(alert, function (result) {
                $scope.alerts.push(result);
                growl.success('Created "' + alert.name + '"');
            }, function (error) {
                growl.error('Failed to create "' + alert.name + '"');
            });
        };
        $scope.enableAlert = function (alert, enabled) {
            if (!alert.enabled === enabled) {
                var updated = angular.copy(alert);
                updated.enabled = enabled;
                Alerts.update({alertId: alert.id}, updated, function (result) {
                    alert.enabled = enabled;
                    growl.success((enabled ? 'Enabled "' : 'Disabled "') + alert.name + '"');
                }, function (error) {
                    growl.error('Failed to ' + (enabled ? 'enable "' : 'disable "') + alert.name + '"');
                });
            }
        };
        $scope.removeAlert = function (alert) {
            Alerts.delete({alertId: alert.id}, function (result) {
                $scope.alerts = $scope.alerts.filter(function (element) {
                    return element.id !== alert.id;
                });
                growl.success('Deleted "' + alert.name + '"');
            }, function (error) {
                growl.error('Failed to delete "' + alert.name + '"');
            });
        };
        
        $scope.$watch('searchText', function(newValue, oldValue) {
        	newValue = newValue == null ? "" : newValue;
        	Storage.set("alerts-searchText", newValue);
        });
        
        $scope.$watch('itemsPerPage', function(newValue, oldValue) {
        	newValue = newValue == null ? $scope.itemsPerPageOptions[1] : newValue;
        	Storage.set("alerts-itemsPerPage", newValue);
        });

    }]);

argusAlerts.controller('AlertDetailCtrl', ['$scope', '$routeParams', '$location', 'growl', 'Alerts', 'Triggers', 'Notifications', 'History', 'TriggerMap','JobExecutionDetails',
    function ($scope, $routeParams, $location, growl, Alerts, Triggers, Notifications, History, TriggerMap,JobExecutionDetails) {

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
                cooldownPeriod: 3600000,
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
                TriggerMap.unmap({alertId: notification.alertId, notificationId: notification.id, triggerId: trigger.id});
            }
            for (var i = 0; i < notification.triggersIds.length; i++) {
                var trigger = notification.triggersIds[i];
                TriggerMap.map({alertId: notification.alertId, notificationId: notification.id, triggerId: trigger}, null);
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

argusAlerts.factory('Alerts', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId', {}, {
            query: {method: 'GET', params: {alertId: ''}, isArray: true},
            update: {method: 'PUT'}
        });
    }]);

argusAlerts.factory('Triggers', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/triggers/:triggerId', {}, {
            query: {method: 'GET', params: {triggerId: ''}, isArray: true},
            save: {method: 'POST', isArray: true},
            update: {method: 'PUT'}
        });
    }]);

argusAlerts.factory('Notifications', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/notifications/:notificationId', {}, {
            query: {method: 'GET', params: {notificationId: ''}, isArray: true},
            save: {method: 'POST', isArray: true},
            update: {method: 'PUT'}
        });
    }]);

argusAlerts.factory('TriggerMap', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'alerts/:alertId/notifications/:notificationId/triggers/:triggerId', {}, {
            map: {method: 'POST'},
            unmap: {method: 'DELETE'}
        });
    }]);
