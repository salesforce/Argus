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

var argus = angular.module('argus', [
    'ngRoute',
    'ngAnimate',
    'ngStorage',
    'angular-growl',
    'angularUtils.directives.dirPagination',
    'angulartics',
    'angulartics.piwik',
    'ui.bootstrap',
    'ui.bootstrap.datetimepicker',

    // Argus specific
    // 'argusControls',
    'argusViewElements',
    
    'argusDashboards',
    'argusDashboardService',
    
    'argusViewMetrics',
    'argusBatches',
    
    'argusMockups',
    'argusConfig',
    
    // controllers
    'argus.controllers.about',
    'argus.controllers.admin',
    'argus.controllers.alerts',
    'argus.controllers.alerts.detail',
    'argus.controllers.login',
    'argus.controllers.main',
    'argus.controllers.namespace',
    
    // services
    'argus.services.admin.reinstateuser',
    'argus.services.alerts',
    'argus.services.auth',
    'argus.services.breadcrumbs',
    'argus.services.history',
    'argus.services.interceptor',
    'argus.services.jobexecutiondetails',
    'argus.services.namespace',
    'argus.services.notifications',
    'argus.services.storage',
    'argus.services.triggers',
    'argus.services.triggersmap',
    
    // directives
    'argus.directives',
    'argus.directives.breadcrumbs',
    'argus.directives.dashboardResource',
    'argus.directives.controls.dashboard',
    'argus.directives.controls.date',
    'argus.directives.controls.dropdown',
    'argus.directives.controls.submit',
    'argus.directives.controls.text',

    // utils
    'filters',
    'constants'
]);

argus.config(['$routeProvider', '$httpProvider', 'growlProvider', 'paginationTemplateProvider', '$analyticsProvider',
    function ($routeProvider, $httpProvider, growlProvider, paginationTemplateProvider, $analyticsProvider) {
        $httpProvider.defaults.withCredentials = true;
        $httpProvider.interceptors.push('UnauthorizedInterceptor');
        paginationTemplateProvider.setPath('bower_components/angular-utils-pagination/dirPagination.tpl.html');
        $routeProvider.
                when('/viewmetrics', {
                    templateUrl: 'views/viewmetrics/viewmetrics.html',
                    controller: 'ViewMetricsCtrl',
                    label: 'Metrics',
                    activeTab: 'metrics'
                }).
                when('/batches', {
                    templateUrl: 'views/batches/batches.html',
                    controller: 'BatchExpressionsCtrl',
                    activeTab: 'batches'
                }).
                when('/dashboards', {
                    templateUrl: 'views/dashboards/dashboard-list.html',
                    controller: 'DashboardListCtrl',
                    label: 'Dashboard List',
                    activeTab: 'dashboards'
                }).
                when('/dashboards/:dashboardId', {
                    templateUrl: 'views/dashboards/dashboard-detail.html',
                    controller: 'DashboardDetailCtrl',
                    label: '{{dashboards.dashboardId}}',
                    activeTab: 'dashboards'
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
                when('/topkheatmap', {
                    templateUrl: 'views/mockups/topkheatmap.html',
                    controller: 'TopkheatmapCtrl',
                    label: 'Top Heatmap'
                }).
                when('/topkheatmaporg', {
                    templateUrl: 'views/mockups/topkheatmaporg.html',
                    controller: 'TopkheatmapCtrlOrg',
                    label: 'Top Heatmap org'
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
    }]);

argus.run(['CONFIG', '$rootScope', '$location', '$route', 'Auth', 'growl', function (CONFIG, $rootScope, $location, $route, Auth, growl) {

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
