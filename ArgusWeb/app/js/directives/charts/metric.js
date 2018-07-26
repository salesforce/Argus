'use strict';
/*global angular:false */

angular.module('argus.directives.charts.metric', [])
.directive('agMetric', ['UtilService', function(UtilService) {
	var metricNameIndex = 100;
	return {
		restrict: 'E',
		require: ['?^agChart', '?^agStatusIndicator', '?^agHeatmap', '?^agTable'],
		scope: {
			expression: '@'
		},
		controller: 'metricElements',
		template: '',
		link: function(scope, element, attributes, controllers) {
			var elementCtrl;
			var value = '';
			var seriesData = {};
			var metricName = 'metric_' + metricNameIndex++;

			// assign proper controller
			elementCtrl = UtilService.assignController(controllers);

			// separate specific series data from other attributes
			// 'color' & 'name' are used to supplement the 'series' data when rendering a chart
			seriesData.color = attributes.seriescolor;
			seriesData.name = attributes.seriesname;
			seriesData.extraYAxis = attributes.extraYaxis;
			seriesData.hideTags = attributes.hidetags;
			seriesData.hideScope = attributes.hidescope;
			seriesData.hideMetric = attributes.hidemetric;

			if (attributes.value && attributes.value.length > 0) {
				value = attributes.value;
			} else {
				value = element.text();
			}

			if (value && value.length > 0) {
				elementCtrl.updateMetric(metricName, value.replace(/(\r\n|\n|\r|\s+)/gm, ''), scope.metricOptions, seriesData);
			}

			scope.$watch('expression', function(newValue) {
				if (newValue) {
					elementCtrl.updateMetric(metricName, newValue, scope.metricOptions, seriesData);
				}
			});
			element.html('<span> </span>');
		}
	};
}]);
