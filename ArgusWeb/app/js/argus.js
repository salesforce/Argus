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
/*global angular:false, require:false */

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
	'angularScreenfull',
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
	'argus.directives.controls.compute',
	'argus.directives.controls.dashboard',
	'argus.directives.controls.date',
	'argus.directives.controls.submit',
	'argus.directives.controls.text',
	'argus.directives.controls.select',
	'argus.directives.controls.queryVariable',
	'argus.directives.charts.chart',
	'argus.directives.charts.lineChart',
	'argus.directives.charts.flags',
	'argus.directives.charts.heatmap',
	'argus.directives.charts.metric',
	'argus.directives.charts.option',
	'argus.directives.charts.statusIndicator',
	'argus.directives.charts.table',
	'argus.directives.headerMenu',
	'argus.directives.modals.confirmClick'
]);
// TODO: not sure if this is even used
// .run(['$http', '$templateCache', function ($http, $templateCache) {
// 	// template caching
// 	$http.get('js/templates/breadcrumbs.html', {cache: $templateCache});
// 	$http.get('js/templates/login.html', {cache: $templateCache});
// 	$http.get('js/templates/alert-list.html', {cache: $templateCache});
// 	$http.get('js/templates/alert-detail.html', {cache: $templateCache});
// 	$http.get('js/templates/dashboard-list.html', {cache: $templateCache});
// 	$http.get('js/templates/dashboard-detail.html', {cache: $templateCache});
// 	$http.get('js/templates/viewmetrics.html', {cache: $templateCache});
// 	$http.get('js/templates/charts/topToolbar.html', {cache: $templateCache});
// }]);

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
	'argus.services.charts.elements',
	'argus.services.dashboard',
	'argus.services.dashboards',
	'argus.services.history',
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
	'argus.services.downloadHelper',
	'argus.services.agTableService',
	'argus.services.tokenAuthInterceptor',
	'argus.services.users',
	'argus.services.confirmClick',
	'argus.services.browsing'
]);

// Controllers
angular.module('argus.controllers', [
	'argus.services',
	'argus.controllers.about',
	'argus.controllers.admin',
	'argus.controllers.alerts',
	'argus.controllers.alerts.detail',
	'argus.controllers.batches',
	'argus.controllers.beta',
	'argus.controllers.dashboards',
	'argus.controllers.dashboards.detail',
	'argus.controllers.login',
	'argus.controllers.main',
	'argus.controllers.metricelements',
	'argus.controllers.namespace',
	'argus.controllers.viewelements',
	'argus.controllers.viewMetrics',
	'argus.controllers.metricsBrowsing',
	'argus.controllers.oauthConfirmation',
	'argus.controllers.oauthManagement'
]);

// Directives
angular.module('argus.directives', [
]);

// Filters
angular.module('argus.filters', [
]);

// Argus module
require('./config');
require('./argusConfig');

// utils
require('./utils/constants');
require('./utils/filters');

// controllers
require('./controllers/about');
require('./controllers/admin');
require('./controllers/alerts');
require('./controllers/alertsDetail');
require('./controllers/batches');
require('./controllers/betaFeatures');
require('./controllers/dashboards');
require('./controllers/dashboardsDetail');
require('./controllers/login');
require('./controllers/main');
require('./controllers/metricElements');
require('./controllers/namespace');
require('./controllers/viewElements');
require('./controllers/viewMetrics');
require('./controllers/metricsBrowsing');
require('./controllers/oauthConfirmation');
require('./controllers/oauthManagement');

// services
require('./services/auth');
require('./services/breadcrumbs');
require('./services/controls');
require('./services/dashboard');
require('./services/inputTracker');
require('./services/search');
require('./services/storage');
require('./services/tags');
require('./services/utilService');
require('./services/jsonUnflatten');
require('./services/tableListService');
require('./services/downloadHelper');
require('./services/agTableService');
require('./services/tokenAuthInterceptor');
require('./services/ConfirmClickService');
require('./services/charts/chartOptions');
require('./services/charts/chartRendering');
require('./services/charts/chartTools');
require('./services/charts/dataProcessing');
require('./services/charts/dateHandler');
require('./services/charts/chartElements');

require('./services/factories/alerts');
require('./services/factories/asyncMetrics');
require('./services/factories/annotations');
require('./services/factories/batches');
require('./services/factories/dashboards');
require('./services/factories/history');
require('./services/factories/jobExecutionDetails');
require('./services/factories/metrics');
require('./services/factories/namespace');
require('./services/factories/notifications');
require('./services/factories/reinstateUser');
require('./services/factories/triggers');
require('./services/factories/triggersMap');
require('./services/factories/users');
require('./services/factories/browsing');

// directives
require('./directives/headerMenu');
require('./directives/tableList');
require('./directives/tableTabs');
require('./directives/breadcrumbs');
require('./directives/dashboardResource');
require('./directives/controls/compute');
require('./directives/controls/dashboard');
require('./directives/controls/date');
require('./directives/controls/submit');
require('./directives/controls/text');
require('./directives/controls/select');
require('./directives/controls/queryVariable');
require('./directives/charts/chart');
require('./directives/charts/lineChart');
require('./directives/charts/flags');
require('./directives/charts/heatmap');
require('./directives/charts/metric');
require('./directives/charts/option');
require('./directives/charts/statusIndicator');
require('./directives/charts/table');
require('./directives/UItools/autoFocus');
require('./directives/UItools/stopEvent');
require('./directives/UItools/ngConfirm');
require('./directives/UItools/ngLoading');
require('./directives/UItools/ngEnter');
require('./directives/modals/confirmClick');
require('./directives/agRequest');
require('./directives/agInfiniteScroll');
// css
require('../css/main.css');
// img
require('../img/argus_logo_rgb.png');