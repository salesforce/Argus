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

angular.module('argus.controllers.alerts', ['ngResource'])
.controller('Alerts', ['$scope', 'growl', 'Alerts', function ($scope, growl, Alerts) {

		Alerts.query().$promise.then(function(alerts) {
			$scope.alerts = alerts;		
		});

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
}]);
