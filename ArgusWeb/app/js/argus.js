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
  'argus.directives.charts.d3LineChartTest',
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
  'argus.services.tableListService'
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
  'argus.controllers.viewMetrics',
  'argus.controllers.d3test'
]);

// Directives
angular.module('argus.directives', [
]);

// Filters
angular.module('argus.filters', [
]);
