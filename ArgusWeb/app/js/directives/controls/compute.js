'use strict';
/*global angular:false */

angular.module('argus.directives.controls.compute', [])
.directive('agCompute', ['CONFIG', '$routeParams', '$interpolate', function(CONFIG, $routeParams, $interpolate) {
	var refreshControl = function(scope, expression, control, dashboardCtrl) {
		if (control['name'] === scope.controlName) {
			return;
		}

		var controlsMap = {};
		dashboardCtrl.getAllControls().forEach(function(c) {
			controlsMap[c['name']] = c['value'];
		});

		var newValue = $interpolate(expression)(controlsMap);
		if (newValue !== controlsMap[scope.controlName]) {
			dashboardCtrl.updateControl(scope.controlName, newValue, 'agCompute');
		}
	};

	return {
		restrict: 'E',
		scope: {
			controlName: '@name',
			elemId: '@id'
		},
		terminal: true,
		priority: 1000,
		require: '^agDashboard',
		link: function(scope, element, attributes, dashboardCtrl) {
			element.hide();

			scope.$on(dashboardCtrl.getControlChangeEventName(), function(evt, control) {
				refreshControl(scope, element.text(), control, dashboardCtrl)
			});

			refreshControl(scope, element.text(), {}, dashboardCtrl);
		}
	};
}]);