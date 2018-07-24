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
/*global angular:false, console:false */

/* App Module */
angular.module('argus.config', [])
.config(['$routeProvider', '$httpProvider', 'growlProvider', 'paginationTemplateProvider', '$analyticsProvider',
	function ($routeProvider, $httpProvider, growlProvider, paginationTemplateProvider, $analyticsProvider) {
		$httpProvider.defaults.withCredentials = true;
		$httpProvider.interceptors.push('TokenAuthInterceptor');
		paginationTemplateProvider.setPath('node_modules/angular-utils-pagination/dirPagination.tpl.html');
		$routeProvider.
			when('/beta', {
				templateUrl: 'js/templates/beta.html',
				controller: 'BetaFeatures',
				label: 'Beta',
				activeTab: 'beta',
				reloadOnSearch: false
			}).
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
			when('/metricsBrowsing', {
				templateUrl: 'js/templates/metricsBrowsing.html',
				controller: 'MetricsBrowsing',
				label: 'Browsing',
				activeTab: 'browsing',
				reloadOnSearch: false
			}).
			when('/oauthConfirmation', {
				templateUrl: 'js/templates/oauthConfirmation.html',
				controller: 'OAuthConfirmation',
				label: 'OAuth',
			}).
			when('/oauthManagement', {
				templateUrl: 'js/templates/oauthManagement.html',
				controller: 'OauthManagement',
				label: 'Oauth Management',
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
			growl.error('You are not logged in.');
			event.preventDefault();
			Auth.setTarget(path);
			$location.path('/login');
		} else if (!angular.isDefined(current)) {
			event.preventDefault();
			$route.reload();
		}
	});
}]);
