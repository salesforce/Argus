/*global angular:false, $:false */
// Core Argus module
angular.module('argus', [
  // Dependencies
  // 'ui.router',
  // 'ui.bootstrap',
  'ngRoute',
  'ngAnimate',
  'ngStorage',
  'angular-growl',
  'angularUtils.directives.dirPagination',
  'argus.services',
  'argus.controllers',
  'argus.directives',
  'argus.filters',
  // 'ngStorage',
  // 'ngDraggable'
]).run(['$http', '$templateCache', function ($http, $templateCache) {
  "use strict";
  // template caching
  // $http.get('js/templates/directives/headerTabs.html', {cache: $templateCache});
  // TODO: add rest of templates to cache
}]);

// Services
angular.module('argus.services', [
  'argus.services.auth',
  'argus.services.breadcrumbs'
  'argus.services.jobexecutiondetails',
  'argus.services.history',
  'argus.services.interceptor',
  'argus.services.storage',
  'argus.services.triggers',
  'argus.services.triggerMap'
  // 'argus.services.',
]);

// Controllers
angular.module('argus.controllers', [
  'argus.services',
  'argus.controllers.login',
  'argus.controllers.main',
  'argus.controllers.namespace',
  // 'argus.controllers.',
  // 'argus.controllers.',
]);

// Directives
angular.module('argus.directives', [
  // 'argus.services.graphRenderer',
  // 'argus.services.api'
]);

// Filters
angular.module('argus.filters', [
]);
