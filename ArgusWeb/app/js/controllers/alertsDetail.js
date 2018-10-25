'use strict';
/*global angular:false */

angular.module('argus.controllers.alerts.detail', ['ngResource'])
.controller('AlertsDetail', ['$q','$scope', '$routeParams', '$location', 'growl', 'Alerts', 'Triggers', 'Notifications', 'History', 'TriggersMap', 'JobExecutionDetails', '$sessionStorage', 'Auth',
	function ($q, $scope, $routeParams, $location, growl, Alerts, Triggers, Notifications, History, TriggersMap, JobExecutionDetails, $sessionStorage, Auth) {
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

				Alerts.update({alertId: alert.id}, alert, function () {
					$scope.unmodifiedAlert = angular.copy(alert);
					growl.success(('Updated "') + alert.name + '"');
					$scope.fetchHistory();
					$scope.fetchJobExecutionDetails();
					// remove existing session storage for update
					if ($sessionStorage.alerts !== undefined) {
						delete $sessionStorage.alerts.cachedData;
						$sessionStorage.alerts.emptyData = true;
					}
				}, function () {
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
				Triggers.update({alertId: trigger.alertId, triggerId: trigger.id}, trigger, function () {
					$scope.unmodifiedTriggers.filter(function (element) {
						if (element.id === trigger.id) {
							angular.copy(trigger, element);
							growl.success('Updated trigger "' + trigger.id + '".');
						}
					});
				}, function () {
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
				Notifications.update({alertId: notification.alertId, notificationId: notification.id}, notification, function () {
					$scope.unmodifiedNotifications.filter(function (element) {
						if (element.id === notification.id) {
							angular.copy(notification, element);
							growl.success('Updated notification "' + notification.id + '".');
						}
					});
					$scope.mapTriggers(notification);
				}, function () {
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
				type: 'GREATER_THAN',
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
			}, function () {
				growl.error('Failed to create "' + trigger.name + '"');
			});
		};

		$scope.addNotification = function () {
			var notification = {
				name: 'new-notification-' + Date.now(),
				notifierName: 'com.salesforce.dva.argus.service.alert.notifier.EmailNotifier',
				subscriptions: [],
				metricsToAnnotate: [],
				cooldownPeriod: 0,
				sractionable:false,
				severityLevel:5,
				alertId: $scope.alert.id
			};

			Notifications.save({alertId: $scope.alert.id}, notification, function (result) {
				angular.copy(result, $scope.notifications);
				$scope.unmodifiedNotifications = angular.copy($scope.notifications);
				$scope.fetchHistory();
				$scope.fetchJobExecutionDetails();
				growl.success('Created "' + notification.name + '"');
			}, function () {
				growl.error('Failed to create "' + notification.name + '"');
			});
		};

		$scope.removeTrigger = function (trigger) {
			Triggers.delete({alertId: trigger.alertId, triggerId: trigger.id}, function () {
				$scope.triggers = $scope.triggers.filter(function (element) {
					return element.id !== trigger.id;
				});
				$scope.unmodifiedTriggers = angular.copy($scope.triggers);
				$scope.fetchHistory();
				$scope.fetchJobExecutionDetails();
				growl.success('Deleted "' + trigger.name + '"');
			}, function () {
				growl.error('Failed to delete "' + trigger.name + '"');
			});
		};

		$scope.removeNotification = function (notification) {
			Notifications.delete({alertId: notification.alertId, notificationId: notification.id}, function () {
				$scope.notifications = $scope.notifications.filter(function (element) {
					return element.id !== notification.id;
				});
				$scope.unmodifiedNotifications = angular.copy($scope.notifications);
				$scope.fetchHistory();
				$scope.fetchJobExecutionDetails();
				growl.success('Deleted "' + notification.name + '"');
			}, function () {
				growl.error('Failed to delete "' + notification.name + '"');
			});
		};

		$scope.mapTriggers = function (notification) {
			var promises = $scope.unmodifiedTriggers.map(function(trigger){
				return TriggersMap.unmap({alertId: notification.alertId, notificationId: notification.id, triggerId: trigger.id}).$promise;
			});

			$q.all(promises).finally(function(){
				for (var i = 0; i < notification.triggersIds.length; i++) {
					var trigger = notification.triggersIds[i];
					TriggersMap.map({alertId: notification.alertId, notificationId: notification.id, triggerId: trigger}, null);
				}
			});

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
			$scope.historyLoaded = false;
			History.query({id: $scope.alertId}, function (history) {
				$scope.history = history;
				$scope.historyLoaded = true;
			}, function () {
				growl.error('Failed to get history for alert "' + $scope.alertId + '"');
				$scope.historyLoaded = true;
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
			{label: '><', value: 'NOT_BETWEEN'},
			{label: 'no data', value: 'NO_DATA'}
		];

		$scope.notificationTypes = [
			{label: 'Callback', value: 'com.salesforce.dva.argus.service.alert.notifier.CallbackNotifier'},
			{label: 'Audit', value: 'com.salesforce.dva.argus.service.alert.notifier.AuditNotifier'},
			{label: 'Mail', value: 'com.salesforce.dva.argus.service.alert.notifier.EmailNotifier'},
			{label: 'GOC++', value: 'com.salesforce.dva.argus.service.alert.notifier.GOCNotifier'},
			{label: 'GUS', value: 'com.salesforce.dva.argus.service.alert.notifier.GusNotifier'}
			// {label: 'Refocus', value: 'com.salesforce.dva.argus.service.alert.notifier.RefocusNotifier'}
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
			}, function () {
				growl.error('Failed to get alert "' + $scope.alertId + '"');
				$location.path('/alerts');
			});

			Triggers.query({alertId: $scope.alertId}, function (triggers) {
				$scope.triggers = triggers;
				$scope.unmodifiedTriggers = angular.copy(triggers);
			}, function () {
				growl.error('Failed to get triggers for alert "' + $scope.alertId + '"');
			});

			Notifications.query({alertId: $scope.alertId}, function (notifications) {
				$scope.notifications = notifications;
				$scope.unmodifiedNotifications = angular.copy(notifications);
			}, function () {
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
