'use strict';
/*global angular:false */

angular.module('argus.directives.charts.option', [])
.directive('agOption', ['UtilService', function(UtilService) {
	return {
		restrict: 'E',
		require: ['?^agChart', '?^agHeatmap', '?^agTable', '?^agMetric'],
		scope: {},
		template: '',
		link: function(scope, element, attributes, controllers) {
			var elementCtrl;

			// assign proper controller
			elementCtrl = UtilService.assignController(controllers);

			var value = '';

			if (attributes.value && attributes.value.length > 0) {
				value = attributes.value;
			} else {
				value = element.text();
			}

			elementCtrl.updateOption(attributes.name, value);
			element.html('<span> </span>');
		}
	};
}]);
