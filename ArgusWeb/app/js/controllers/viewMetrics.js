/*global angular:false, console:false */
'use strict';

angular.module('argus.controllers.viewMetrics', ['ngResource'])
.controller('ViewMetrics', ['$location', '$routeParams', '$scope', '$compile', 'growl', 'Metrics', 'Annotations', 'SearchService', 'Controls', 'ChartDataProcessingService', 'DateHandlerService', 'InputTracker',
	function ($location, $routeParams, $scope, $compile, growl, Metrics, Annotations, SearchService, Controls, ChartDataProcessingService, DateHandlerService, InputTracker) {
		var lastParams;
		var noMorePages = false;
		$scope.expression = $routeParams.expression ? $routeParams.expression : null;
		$scope.includeAnnotations = InputTracker.getDefaultValue('viewMetricsWithAnnotation', true);
		$scope.$watch('includeAnnotations', function (newValue) {
			InputTracker.updateDefaultValue('viewMetricsWithAnnotation', true, newValue);
		});
		// sub-views: (1) single chart, (2) metric discovery
		$scope.checkMetricExpression = function() {
			if ($scope.expression) {
				$scope.showMetricDiscovery = false;
				$scope.showChart = true;
			} else {
				$scope.showMetricDiscovery = true;
				$scope.showChart = false;
			}
		};

		$scope.checkMetricExpression();

		//sync the expression to URL param
		$scope.$watch('expression', function(val){
			// if val is empty, clear url string
			var urlStr = (val) ? Controls.getUrl([{name: 'expression', value: val}]) : '';
			$location.search(urlStr);
		});

		$scope.getMetricData = function () {
			var tempSeries = [];
			var annotationInfo = [];
			if ($scope.expression !== null && $scope.expression.length) {
				// clear old chart and annotation label tip
				angular.element('#' + 'container').empty();
				angular.element('.d3-tip').remove();
				$scope.checkMetricExpression();
				// show loading spinner
				$scope.chartLoaded = false;

				Metrics.query({expression: $scope.expression}, function (data) {
					if (data && data.length > 0) {
						tempSeries = ChartDataProcessingService.copySeriesDataNSetOptions(data, {});
						if ($scope.includeAnnotations) {
							for (var i = 0; i < data.length; i++) {
								annotationInfo.push(ChartDataProcessingService.getAlertFlagExpression(data[i]));
							}
						}
					} else {
						tempSeries = [{
							noData: true,
							errorMessage: 'Empty result returned for the metric expression',
							name: JSON.stringify($scope.expression).slice(1, -1),
							color: 'Maroon'
						}];
					}
					$scope.updateChart(tempSeries, annotationInfo, [$scope.expression]);
				}, function (error) {
					// prevent error.data.message being null breaks the message
					if (error.data.message === null) {
						error.data.message = 'Something was wrong. No info.';
					} else {
						growl.error(error.data.message, {referenceId: 'viewmetrics-error'});
					}
					tempSeries = [{
						invalidMetric: true,
						errorMessage: error.statusText + '(' + error.status + ') - ' + error.data.message.substring(0, 31),
						name: error.config.params.expression,
						color: 'Black'
					}];
					$scope.updateChart(tempSeries, annotationInfo, []);
				});
			} else {
				// empty expression
				$scope.checkMetricExpression();
				$scope.updateChart(tempSeries, annotationInfo, []);
			}
		};

		$scope.searchMetrics = function(value, category) {
			// TODO: move param processing to search service
			noMorePages = false;
			var defaultParams = {
				namespace: '*',
				scope: '*',
				metric: '*',
				tagk: '*',
				tagv: '*',
				limit: 25,
				page: 1,
				type: 'scope'
			};

			var newParams = angular.copy(defaultParams);

			// update params with values in $scope if they exist
			newParams.scope = ($scope.scope) ? $scope.scope : '*';
			newParams.metric = ($scope.metric) ? $scope.metric : '*';
			newParams.namespace = ($scope.namespace) ? $scope.namespace : '*';
			newParams.tagk = ($scope.tagk) ? $scope.tagk : '*';
			newParams.tagv = ($scope.tagv) ? $scope.tagv : '*';
			newParams.type = category ? category : 'scope';

			if(category) {
				if(category === 'scope') {
					newParams.scope = newParams.scope + '*';
				} else if(category === 'metric') {
					newParams.metric = newParams.metric + '*';
				} else if(category === 'tagk') {
					newParams.limit = 10;
					newParams.tagk = newParams.tagk + '*';
				} else if(category === 'tagv') {
					newParams.tagv = newParams.tagv + '*';
				} else if(category === 'namespace') {
					newParams.namespace = newParams.namespace + '*';
				}
			} else {
				newParams.scope = newParams.scope + '*';
			}

			lastParams = newParams;
			// end TODO
			//return a promise for template but later assign the data to the variable
			var result = SearchService.search(newParams)
				.then(function(response) {
					if(response.data.length < newParams.limit){
						noMorePages = true;
					}
					return response.data;
				});
			return result;
		};

		$scope.loadMore = function(matches, loadingAttr){
			if(noMorePages) return;

			lastParams.page = lastParams.page + 1;
			eval('$scope.'+loadingAttr +'= true;');
			SearchService.search(lastParams)
				.then(function(response) {
						if(response.data.length < lastParams.limit){
							noMorePages = true;
						}
						response.data.forEach(function(name){
							matches.push({
							model: name
							});
						});
						eval('$scope.'+loadingAttr +'= false;');

				}, function(){
						eval('$scope.'+loadingAttr +'= false;');
				});

		};

		$scope.isSearchMetricDisabled = function () {
			var s = $scope.scope, m = $scope.metric;
			return (s === undefined || s.length < 1) && (m === undefined || m.length < 1);
		};

		// add search metrics to $scope expression
		$scope.addSearchExpression = function () {
			// set 'addDefaultValues' to false
			$scope.expression = constructSearchStr(false);
		};

		// construct & build a graph, with search values
		$scope.graphSearchExpression = function () {
			// set 'addDefaultValues' to true
			$scope.expression = constructSearchStr(true);

			// graph new epxression with default values
			$scope.getMetricData();
		};

		// TODO: create service for this form reset/clear
		$scope.setPristine = function () {
			$scope.scope = '';
			$scope.metric = '';
			$scope.metric = '';
			$scope.tagk = '';
			$scope.tagv = '';
			$scope.namespace = '';
			$scope.search_metrics.$setPristine();
		};

		// construct full search string from search fields
		function constructSearchStr(addDefaultValues) {
			var s = $scope.scope, m = $scope.metric, tagk = $scope.tagk, tagv = $scope.tagv, n = $scope.namespace;

			/* expression str format & rules:
				search fields:      scope:metric{tags}:aggregator
				expression field:   start*:end:scope*:metric*{tags}:aggregator*:downsampler:namespace
			**/
			var start_Str = '';
			var scope_Str = (s && s.length > 1) ? s + ':' : '';
			var metric_Str = (m && m.length > 1) ? m : '';

			var tag_Str = '';
			if (tagk && tagv) {
				tag_Str = '{' + tagk + '=' + tagv + '}';
				$scope.enterTagsErr = false;
			} else if ( (tagk && !tagv) || (!tagk && tagv) ) {
				// both tag key AND tag value input must be entered
				$scope.enterTagsErr = true;
				return null;
			}

			var agg_Str = '';
			var namespace_Str = (n && n.length > 1) ? ':' + n : '';

			/* Add default settings for: start, aggregator
				full:  -1h:scope:metric{tags}:avg:namespace
				start: -1h
				aggregator: avg
			**/
			if (addDefaultValues) {
				start_Str = '-1h:';
				agg_Str = ':avg';
			}

			return start_Str + scope_Str + metric_Str + tag_Str + agg_Str + namespace_Str;
		}

		// show newExpression in page view
		// function showSearchExpression() {
		// 	var searchStr = constructSearchStr();
		// 	$('#searchExpression').html(searchStr);
		// }

		// -------------

		$scope.updateChart = function (series, annotationInfo, expressions) {
			// if the metric expression is not empty
			if (series && series.length > 0) {
				var chartScope = $scope.$new(false);
				chartScope.chartConfig = {
					chartId: 'container',
					expressions: expressions,
					chartType: 'line'
				};
				chartScope.dateConfig = {};
				chartScope.series = series;

				// all graph class name and sort sources alphabetically
				for (var i = 0; i < series.length; i++) {
					chartScope.series[i].graphClassName = chartScope.chartConfig.chartId + '_graph' + (i + 1);
				}
				chartScope.series.sort(function(a, b) {
					var textA = a.name.toUpperCase();
					var textB = b.name.toUpperCase();
					return (textA < textB) ? -1 : (textA > textB) ? 1 : 0;
				});

				// get start and end time info based on data range
				if (series[0].data && series[0].data.length > 0) {
					chartScope.dateConfig.startTime = DateHandlerService.getStartTimestamp(series);
					chartScope.dateConfig.endTime = DateHandlerService.getEndTimestamp(series);
				}

				// query annotations
				if (annotationInfo.length > 0) {
					var annotationCount = {};
					annotationCount.tot = annotationInfo.length;
					for (i = 0; i < annotationInfo.length; i++) {
						Annotations.query({expression: annotationInfo[i]}).$promise.then(function (data) {
							//prevent empty annotation returns
							if (data !== undefined && data.length !== 0) {
								var flagSeries = ChartDataProcessingService.copyFlagSeries(data);
								if (flagSeries === null || flagSeries === undefined) return;
								flagSeries.linkedTo = ChartDataProcessingService.createSeriesName(data[0]);
								chartScope.series = chartScope.series.map(function (item) {
									if (item.name === flagSeries.linkedTo) item.flagSeries = flagSeries;
									return item;
								});
							}
							annotationCount.tot--;
							if (annotationCount.tot === 0) {
								$scope.chartLoaded = true;
								angular.element('#' + 'container').append($compile(
									'<div ngsf-fullscreen>' +
									'<line-chart chartConfig="chartConfig" series="series" dateConfig="dateConfig"></line-chart>' +
									'</div>')(chartScope)
								);
							}
						}, function (error) {
							console.log('no annotation found;', error.statusText);
							annotationCount.tot--;
							if (annotationCount.tot === 0) {
								$scope.chartLoaded = true;
								angular.element('#' + 'container').append($compile(
									'<div ngsf-fullscreen>' +
									'<line-chart chartConfig="chartConfig" series="series" dateConfig="dateConfig"></line-chart>' +
									'</div>')(chartScope)
								);
							}
						});
					}
				} else {
					$scope.chartLoaded = true;
					angular.element('#' + 'container').append($compile(
						'<div ngsf-fullscreen>' +
						'<line-chart chartConfig="chartConfig" series="series" dateConfig="dateConfig"></line-chart>' +
						'</div>')(chartScope)
					);
				}
			}
		};

		$scope.getMetricData(null);
	}]);
