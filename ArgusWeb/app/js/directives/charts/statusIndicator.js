'use strict';
/*global angular:false */

angular.module('argus.directives.charts.statusIndicator', [])
.directive('agStatusIndicator', ['ChartDataProcessingService', 'ChartRenderingService', 'DashboardService',
	function(ChartDataProcessingService, ChartRenderingService, DashboardService) {
		return {
			restrict: 'E',
			transclude: true,
			scope: {
				serviceName: '@name',
				hi: '@hi',
				lo: '@lo'
			},
			require: '^agDashboard',
			controller: 'ViewElements',
			template: '<div ng-transclude=""> </div>',
			link: function(scope, element, attributes, dashboardCtrl) {
				var metricExpression;
				var indicatorHTML =
					'<div class="serviceItem">' +
						'<div class="serviceName"><p>' + attributes.name + '</p></div>' +
						'<div id="'+ attributes.name + '-status" class="statusIndicator"></div>' +
					'</div>';

				// render status indicator
				element.html(indicatorHTML);

				// listen to scope for event and controls info
				scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
					for (var key in scope.metrics) {
						if (scope.metrics.hasOwnProperty(key)) {
							// get metricExpression from scope
							metricExpression = scope.metrics[key].expression;

							// process mertricExpression from controls if present
							if ( controls ) {
								metricExpression = ChartDataProcessingService.augmentExpressionWithControlsData(metricExpression, controls);
							}
						}
					}

					// get datapoints from metric expression
					if (metricExpression) {
						DashboardService.getMetricData(metricExpression)
							.then(function( result ) {
								// get the last data point from the result data
								var lastStatusVal = ChartDataProcessingService.getLastDataPoint(result.data[0].datapoints);

								// update status indicator
								ChartRenderingService.updateIndicatorStatus(attributes, lastStatusVal);
							});
					}
				});
			}
		};
	}]);
