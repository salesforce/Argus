'use strict';
/*global angular:false */

angular.module('argus.directives.dashboardResource', [])
.directive('agDashboardResource', ['DashboardService', '$sce', '$compile', function(DashboardService, $sce, $compile) {
	return {
		restrict: 'E',
		scope: false,
		link: function(scope, element, attribute) {
			var dashboardID;
			if (attribute.id && attribute.id > 0) {
				dashboardID = attribute.id;
			} else {
				dashboardID = scope.dashboardId;
			}

			DashboardService.getDashboardById(dashboardID)
				.success(function (data) {
					element.html('<div>' + $sce.trustAsHtml(data.content) + '</div>');
					$compile(element.contents())(scope);
				});
		}
	};
}]);