'use strict';
/*global angular:false */

angular.module('argus.directives.breadcrumbs', [])
.directive('breadcrumbsHtml', function() {
	return {
		restrict: 'E',
		templateUrl: 'js/templates/breadcrumbs.html',
		scope: {},
		controller: ['$scope', 'breadcrumbs', function ($scope, breadcrumbs) {
			$scope.breadcrumbs = breadcrumbs;
		}]
		// link: function(scope, element, attribute) {
		// }
	};
});
