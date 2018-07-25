'use strict';
/*global angular:false, $:false, console:false, growl:false, d3:false, window:false */

angular.module('argus.directives.charts.chart', [])
.directive('agChart', ['Metrics', 'Annotations', 'ChartRenderingService', 'ChartDataProcessingService', 'ChartOptionService', 'DateHandlerService', 'CONFIG', 'VIEWELEMENT', '$compile', 'UtilService', 'growl', '$timeout',
	function(Metrics, Annotations, ChartRenderingService, ChartDataProcessingService, ChartOptionService, DateHandlerService, CONFIG, VIEWELEMENT, $compile, UtilService, growl, $timeout) {
		var timer;
		var resizeTimeout = 250;
		var chartNameIndex = 1;

		function compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList) {
			// empty any previous content
			angular.element('#' + newChartId).empty();
			angular.element('.d3-tip').remove();

			// create a new scope to pass to compiled line-chart directive
			var lineChartScope = scope.$new(false);     // true will set isolate scope, false = inherit

			// add more options in addition to 'ag-options'
			lineChartScope.chartConfig = updatedOptionList;
			lineChartScope.chartConfig.chartId = newChartId;
			lineChartScope.chartConfig.smallChart = scope.chartOptions ? scope.chartOptions.smallChart : undefined;

			// // when there is no agDate, use
			if (series[0].data && series[0].data.length > 0) {
				if (dateConfig.startTime === undefined)	dateConfig.startTime = DateHandlerService.getStartTimestamp(series);
				if (dateConfig.endTime === undefined) dateConfig.endTime = DateHandlerService.getEndTimestamp(series);
			}
			lineChartScope.dateConfig = dateConfig;
			scope.seriesDataLoaded = true;
			lineChartScope.series= series;

			for (var i = 0; i < series.length; i++) {
				// use graphClassName to bind all the graph element of a metric together
				lineChartScope.series[i].graphClassName = newChartId + '_graph' + (i + 1);
			}
			// sort series alphabetically
			lineChartScope.series.sort(UtilService.alphabeticalSort);
			// append, compile, & attach new scope to line-chart directive
			// TODO: bind ngsf-fullscreen to the outer container i.e. elements_chartID
			if (updatedOptionList.chartType === 'heatmap') {
				angular.element('#' + newChartId).append(
					$compile(
						'<div ngsf-fullscreen>' +
						'<heatmap chartConfig="chartConfig" series="series" dateconfig="dateConfig"></heatmap>' +
						'</div>')(lineChartScope)
				);
			} else {
				angular.element('#' + newChartId).append(
					$compile(
						'<div ngsf-fullscreen>' +
						'<line-chart chartConfig="chartConfig" series="series" dateconfig="dateConfig"></line-chart>' +
						'</div>')(lineChartScope)
				);
			}
		}

		function queryAnnotationData(scope, annotationItem, newChartId, series, dateConfig, updatedOptionList) {
			Annotations.query({expression: annotationItem}).$promise.then(function(data) {
				if (data && data.length > 0) {
					var flagSeriesNotInSeries = true;
					var forName = ChartDataProcessingService.createSeriesName(data[0]);
					var flagSeries = ChartDataProcessingService.copyFlagSeries(data);
					flagSeries.linkedTo = forName;
					// bind series with its annotations(flag series)
					series = series.map(function (item) {
						if (item.name === flagSeries.linkedTo) {
							item.flagSeries = flagSeries;
							flagSeriesNotInSeries = false;
						}
						return item;
					});
					if (flagSeriesNotInSeries) {
						series.push({
							name: flagSeries.linkedTo,
							color: null,
							extraYAxis: null,
							data: [],
							flagSeries: flagSeries
						});
					}
				}

				// append, compile, & attach new scope to line-chart directive
				compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList);

			}, function (error) {
				console.log( 'no data found', error.data.message );
				// append, compile, & attach new scope to line-chart directive
				compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList);
			});
		}

		function setupAnnotations(scope, newChartId, series, updatedAnnotationList, dateConfig, updatedOptionList) {

			if (updatedAnnotationList.length === 0) {
				// no annotations list, continue to render chart as normal
				compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList);
			} else {
				// check annotations & add to series data for line-chart
				for (var i = 0; i < updatedAnnotationList.length; i++) {
					var annotationItem = updatedAnnotationList[i];
					queryAnnotationData(scope, annotationItem, newChartId, series, dateConfig, updatedOptionList);
				}
			}
		}

		function queryMetricData(scope, metricItem, metricCount, newChartId, series, updatedAnnotationList, dateConfig, attributes, updatedOptionList) {
			if (!metricItem) return;

			var smallChart = !!attributes.smallchart;

			Metrics.query({expression: metricItem.expression}).$promise.then(function(data) {
				var tempSeries;

				if (data && data.length > 0) {
					// check to update statusIndicator with correct status color
					if (smallChart) {
						// get the last data point
						// var lastStatusVal = ChartDataProcessingService.getLastDataPoint(data[0].datapoints);

						// update status indicator
						// ChartRenderingService.updateIndicatorStatus(attributes, lastStatusVal);

						// add 'smallChart' flag to scope
						scope.chartOptions = {smallChart: smallChart};
					}

					// metric item attributes are assigned to the data (i.e. name, color, etc.)
					tempSeries = ChartDataProcessingService.copySeriesDataNSetOptions(data, metricItem);

					// keep metric expression info if the query succeeded
					metricCount.expressions.push(metricItem.expression);
				} else {
					// growl.info('No data found for the metric expression: ' + JSON.stringify(metricItem.expression));
					console.log('Empty result returned for the metric expression');
					tempSeries = [{
						noData: true,
						errorMessage: 'Empty result returned for the metric expression',
						name: metricItem.name? metricItem.name: JSON.stringify(metricItem.expression).slice(1, -1),
						color: (metricItem.color) ? metricItem.color: 'Maroon'
					}];
				}

				Array.prototype.push.apply(series, tempSeries);
				// decrement metric count each time an expression is added to the series.
				metricCount.tot -= 1;
				if (metricCount.tot === 0) {
					// pass in metric expression in as chartConfig
					updatedOptionList.expressions = metricCount.expressions;
					// check for Annotations
					setupAnnotations(scope, newChartId, series, updatedAnnotationList, dateConfig, updatedOptionList);
				}
			}, function (error) {
				// growl.error(error.message);
				var tempSeries = [];
				if (error.message !== undefined) {
					console.log('an unexpected error is caught');
					growl.error(error.message);
					tempSeries.push({
						noData: true,
						errorMessage: 'Unknown error occured',
						name: metricItem.name? metricItem.name: '',
						color: metricItem.color? metricItem.color: 'Maroon'
					});
				} else {
					console.log('Metric expression does not exist in database');
					tempSeries.push({
						invalidMetric: true,
						errorMessage: error.statusText + '(' + error.status + ') - ' + error.data.message.substring(0, 31),
						name: metricItem.name? metricItem.name: error.config.params.expression,
						color: metricItem.color? metricItem.color: 'Black'
					});
				}
				Array.prototype.push.apply(series, tempSeries);

				metricCount.tot -= 1;
				if (metricCount.tot === 0) {
					// display chart with series data and populate annotations
					setupAnnotations(scope, newChartId, series, updatedAnnotationList, dateConfig, updatedOptionList);
				}
			});
		}

		// TODO: below functions 'should' be refactored to the chart services.
		function setupChart(scope, element, attributes, controls) {
			// remove/clear any previous chart rendering from DOM
			var lastEl = element[0].querySelector('[id^=element_chart]');
			var lastId = lastEl? lastEl.id: null;
			element.empty();
			// generate a new chart ID, set css options for main chart container
			// if the element has content previously, leave the id unchanged
			var newChartId = lastId || 'element_' + VIEWELEMENT.chart + chartNameIndex++;

			var chartType = attributes.type ? attributes.type : 'line';
			chartType = chartType.toLowerCase();
			// TODO: make this a constant somewhere else
			var supportedChartTypes = ['line', 'area', 'scatter', 'stackarea', 'bar', 'stackbar', 'heatmap'];
			// check if a supported chartType is used
			if (!supportedChartTypes.includes(chartType)) chartType = 'line';
			var cssOpts = ( attributes.smallchart ) ? 'smallChart' : '';

			// set the charts container for rendering
			ChartRenderingService.setChartContainer(element, newChartId, cssOpts);

			var data = {
				metrics: scope.metrics,
				annotations: scope.annotations,
				options: scope.options
			};

			// get start and end time for the charts as well as whether GMT/UTC scale is used or not
			var dateConfig = {};
			for (var i = 0; i < controls.length; i++) {
				if (controls[i].type === 'agDate') {
					var timeValue = controls[i].value;
					if (controls[i].name === 'start') {
						dateConfig.startTime = DateHandlerService.timeProcessingHelper(timeValue);
					} else if (controls[i].name === 'end'){
						dateConfig.endTime = DateHandlerService.timeProcessingHelper(timeValue);
					}
				}
			}
			// process data for: metrics, annotations, options
			var processedData = ChartDataProcessingService.processMetricData(data, controls);

			if (!processedData) {
				console.log('no processed data returned: ' + newChartId);
				return;
			}

			// re-assign each list for: metrics, annotations, options
			var updatedMetricList = processedData.updatedMetricList;
			var updatedAnnotationList = processedData.updatedAnnotationList;
			var updatedOptionList = processedData.updatedOptionList;
			updatedOptionList.chartType = chartType;

			// define series first, then build list for each metric expression
			var series = [];
			var metricCount = {};
			metricCount.tot = updatedMetricList.length;
			metricCount.expressions = [];

			scope.seriesDataLoaded = false; //used for load spinner
			angular.element('#' + newChartId).append( $compile('<div ng-loading="seriesDataLoaded"></div>')(scope) );

			for (i = 0; i < updatedMetricList.length; i++) {
				var metricItem = updatedMetricList[i];
				// get data for each metric item, bind optional data with metric data
				queryMetricData(scope, metricItem, metricCount, newChartId, series, updatedAnnotationList, dateConfig, attributes, updatedOptionList);
			}
		}

		return {
			restrict: 'E',
			transclude: true,
			scope: {},
			require: '^agDashboard',
			controller: 'ViewElements',
			template: '<div ng-transclude=""></div>',
			compile: function () {
				return {
					post: function postLink(scope, element, attributes, dashboardCtrl) {
						d3.select(window).on('resize', function(){
							$timeout.cancel(timer); //clear to improve performance
							timer = $timeout(function () {
								scope.$apply();
							}, resizeTimeout);
						});
						scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
							setupChart(scope, element, attributes, controls);
						});
						element.on('$destroy', function(){
							chartNameIndex = 1;
						});
					}
				};
			}
		};
	}]);
