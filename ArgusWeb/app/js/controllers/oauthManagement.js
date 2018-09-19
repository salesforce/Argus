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
/*global angular:false */

angular.module('argus.controllers.oauthManagement', [])
	.controller('OauthManagement', ['$scope', '$window', 'Auth', '$routeParams', 'CONFIG', '$resource', 'growl',
		function ($scope, $window, Auth, $routeParams, CONFIG, $resource, growl) {
			$scope.user = Auth.getUsername() + '@salesforce.com';
			$scope.colName = {
				name: 'Name',
			};
			$scope.properties = {
				title: 'Authorized Apps',
				type: 'Authorized Apps'
			};
			
			$scope.authorizedAppsLoaded = false;
			
			//  test
			$scope.apps = [];
			$scope.authorizedAppsLoaded = true;

			
			$scope.refreshApps = function () {
				$resource(CONFIG.wsUrl + CONFIG.oauthListPath, {}, { 'get': { method: 'GET', isArray: true } }).get({},
					function (apps) {
						$scope.apps = apps.map(function (app) {
							return {
								name: app.applicationName
							};
						});
						$scope.authorizedAppsLoaded = true;
					}, function (err) {
						console.log(err + 'not authorized before!');
						$scope.authorizedAppsLoaded = true;
					});
			};

			$scope.refreshApps();

			$scope.deleteApp = function (app) {
				$scope.authorizedAppsLoaded = false;
				$resource(CONFIG.wsUrl + CONFIG.oauthDeletePath + '/' + app.name, {}, {}).delete({
				}, function () {
					$scope.apps = $scope.apps.filter(function (app_) {
						return app_.name !== app.name;
					});
					$scope.authorizedAppsLoaded = true;
				}, function (err) {
					growl.error('fail to delete the app!' + err);
					$scope.authorizedAppsLoaded = true;
				});
			};
		}]);
