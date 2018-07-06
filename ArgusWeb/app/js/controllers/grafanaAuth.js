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

angular.module('argus.controllers.grafanaAuth', [])
	.controller('GrafanaAuth', ['$scope', '$window', 'Auth', '$routeParams', 'CONFIG', '$resource', 'growl',
		function ($scope, $window, Auth, $routeParams, CONFIG, $resource, growl) {
			$scope.user = Auth.getUsername() + '@salesforce.com';
			var code = $routeParams['code'];
			var state = $routeParams['state'];
			var redirectUrl = '';
			var noError = false;
			//make a call to get grafana OAuth uri
			$resource(CONFIG.grafanaUrl + 'login/accept_auth?', {}, {}).save({
				code: code,
				state: state,
			}, function (resp) {
				redirectUrl = resp.redirect_uri;
				//redirectUrl = CONFIG.grafanaUrl + 'login/generic_oauth?code=' + code + '&state=' + state;
				noError = true;
			}, function (err) {
				growl.error('Error accessing argus OAuth service: ' + err);
			});

			$scope.authorize = function () {
				console.log('authorizing grafana!');
				if (noError) {
					$window.location = redirectUrl;
				}else{
					growl.error('Error accessing argus OAuth service!');
				}
			}
			$scope.cancel = function () {
				$window.history.back();
			}
		}]);
