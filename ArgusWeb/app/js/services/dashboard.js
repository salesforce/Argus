'use strict';
/*global angular:false, $:false */

angular.module('argus.services.dashboard', [])
.service('DashboardService', ['$filter', '$compile', '$resource', 'CONFIG', 'VIEWELEMENT', 'Metrics', '$sce', '$http', 'Annotations', 'growl',
	function ($filter, $compile, $resource, CONFIG, VIEWELEMENT, Metrics, $sce, $http, Annotations, growl) {

		this.updateIndicatorStatus = updateIndicatorStatus;

		this.getDashboardById = function(dashboardId){
			return $http.get(CONFIG.wsUrl + 'dashboards/' + dashboardId);
		};

		// TODO: refactor this duplicate code also in: viewMetrics.js $scope function
		// 'populateSeries' below makes same API call, refactor both to separate factories
		this.getMetricData = function(metricExpression) {
			if (!metricExpression) return;

			var metricData =
				$http({
					method: 'GET',
					url: CONFIG.wsUrl + 'metrics',
					params: {'expression': metricExpression}
				}).
				success(function(data) {
					if ( data && data.length > 0 ) {
						return data[0];
					} else {
						growl.info('No data found for the metric expression: ' + JSON.stringify(metricExpression));
						return;
					}
				}).
				error(function(data) {
					if (data) {
						growl.error(data.message);
					}
					return;
				});

			return metricData;
		};

		this.augmentExpressionWithControlsData = function(event, expression, controls) {
			var result = expression;

			for (var controlIndex in controls) {
				var controlName = controls[controlIndex].name;
				var controlValue = controls[controlIndex].value;
				var controlType = controls[controlIndex].type;

				if ( controlType === 'agDate' ) {
					controlValue = isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
					// remove GMT from offset input from
					if( typeof (controlValue) === 'string' && controlValue.indexOf('GMT') >= 0){
						controlValue = controlValue.replace('GMT','').trim();
					}

					if(result.match(new RegExp('\\$' + controlName + '\\$', 'g')) !== null) {
						result = result.replace(new RegExp('\\$' + controlName + '\\$', 'g'), controlValue);
					}

					var match = null;
					//Check if it either matches something like $start-7h$ or $start-$diff$$
					if((match = result.match(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', 'g'))) != null) {
						controlValue = modifyControlValue(controlValue, match[0]);
						result = result.replace(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', 'g'), controlValue);
					} else if((match = result.match(new RegExp('\\$' + controlName + '\\-\\$.*\\$\\$', 'g'))) != null) {
						match = match[0].substring(1, match[0].length - 1);
						var subtractControlName = match.match(/\$.*\$/)[0];
						subtractControlName = subtractControlName.substring(1, subtractControlName.length - 1);
						var value = getControlValueFromName(controls, subtractControlName);

						controlValue = modifyControlValue(controlValue, '-' + value);
						result = result.replace(new RegExp('\\$' + controlName + '\\-\\$.*\\$\\$', 'g'), controlValue);
					}
				} else {
					controlValue = controlValue == undefined ? '' : controlValue;
					result = result.replace(new RegExp('\\$' + controlName + '\\$', 'g'), controlValue);
				}
			}

			result = result.replace(/(\r\n|\n|\r|\s+)/gm, '');
			return result;
		};

		function getControlValueFromName(controls, controlName) {
			for(var index in controls) {
				if(controlName === controls[index].name) {
					return controls[index].value;
				}
			}

			return null;
		}

		function modifyControlValue(controlValue, controlName) {
			var match = controlName.match(/\-\d+[smdh]/)[0];
			var subtract = getValue(match);

			if(isNaN(controlValue)) {
				controlValue = getValue(controlValue);
				return '-' + (controlValue + subtract) + 's';
			}

			return controlValue - (subtract * 1000);
		}

		function getValue(timeStr) {
			timeStr = timeStr.substring(1);
			var digits = timeStr.substring(0, timeStr.length - 1);
			var unit = timeStr.substring(timeStr.length - 1);

			var secs = 'invalid';
			switch(unit) {
				case 's':
					secs = parseFloat(digits);
					break;
				case 'm':
					secs = parseFloat(digits) * 60;
					break;
				case 'h':
					secs = parseFloat(digits) * 3600;
					break;
				case 'd':
					secs = parseFloat(digits) * 24 * 3600;
					break;
				default:
					secs = 'Invalid time unit used.';
			}

			return secs;
		}


		this.buildViewElement = function(scope, element, attributes, dashboardCtrl, elementType, index, DashboardService) {
			var elementId = 'element_' + elementType + index;
			var smallChartCss = ( attributes.smallchart ) ? 'class="smallChart"' : '';
			element.prepend('<div id=' + elementId + ' ' + smallChartCss +'></div>');

			scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls){
				// console.log(dashboardCtrl.getSubmitBtnEventName() + ' event received.');
				populateView(event, controls);
			});

			function populateView(event, controls) {
				// processListData(event, controls)
				// metrics, annotations, options

				var updatedMetricList = [];
				var updatedAnnotationList = [];
				var updatedOptionList = [];
				var processedExpression;

				// TODO: move these 3 items to 'utils' folder
				for (var key in scope.metrics) {
					if (scope.metrics.hasOwnProperty(key)) {

						// get metricExpression, and name & color attributes from scope
						var metrics = scope.metrics[key];
						var metricExpression = metrics.expression;
						var metricSpecificOptions = metrics.metricSpecificOptions;

						processedExpression = DashboardService.augmentExpressionWithControlsData(event, metricExpression, controls);

						if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
							var processedMetric = {};
							processedMetric['expression'] = processedExpression;
							processedMetric['name'] = metrics.name;
							processedMetric['color'] = metrics.color;
							processedMetric['metricSpecificOptions'] = getMetricSpecificOptionsInArray(metricSpecificOptions);

							// update metric list with new processed metric object
							updatedMetricList.push(processedMetric);
						}
					}
				}

				for (var key in scope.annotations) {
					if (scope.annotations.hasOwnProperty(key)) {
						processedExpression = DashboardService.augmentExpressionWithControlsData(event, scope.annotations[key],controls);
						if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
							updatedAnnotationList.push(processedExpression);
						}
					}
				}

				for (var key in scope.options) {
					if (scope.options.hasOwnProperty(key)) {
						updatedOptionList.push({name: key, value: scope.options[key]});
					}
				}

				if (updatedMetricList.length > 0) {
					DashboardService.populateView(updatedMetricList, updatedAnnotationList, updatedOptionList, elementId, attributes, elementType, scope);
				}
			}

			function getMetricSpecificOptionsInArray(metricSpecificOptions){
				var options = [];
				for (var key in metricSpecificOptions) {
					if (metricSpecificOptions.hasOwnProperty(key)) {
						options.push({'name': key, 'value': metricSpecificOptions[key]});
					}
				}
				return options;
			}
		};

		this.populateView = function(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope) {
			if (!metricList && !divId) return;

			if (!metricList) {
				growl.error('Valid metric expressions are required to display the chart/table.');
				$('#' + divId).hide();
				return;
			}

			if ( elementType === VIEWELEMENT.chart ) {
				populateChart(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope);
			} else {
				var metricExpressionList = getMetricExpressionList(metricList);

				$http({
					method: 'GET',
					url: CONFIG.wsUrl + 'metrics',
					params: {'expression': metricExpressionList}
				}).success(function(data) {
					if ( data && data.length > 0) {
						$('#' + divId).show();

						if (elementType === VIEWELEMENT.heatmap)
							updateHeatmap({}, data, divId, optionList, attributes);
						else if (elementType === VIEWELEMENT.table)
							updateTable(data, scope, divId, optionList);

					} else {
						updateChart({}, data, divId, annotationExpressionList, optionList, attributes);
						growl.info('No data found for the metric expressions: ' + JSON.stringify(metricExpressionList));
					}
				}).error(function(data) {
					growl.error(data.message);
					$('#' + divId).hide();
				});
			}
		};

		function populateChart(metricList, annotationExpressionList, optionList, divId, attributes){

			$('#' + divId).empty();
			$('#' + divId).show();

			var smallChart = attributes.smallchart ? true : false;
			var chartType = attributes.type ? attributes.type : 'LINE';
			var highChartOptions = getOptionsByChartType(CONFIG, chartType, smallChart);

			setCustomOptions(highChartOptions, optionList);

			$('#' + divId).highcharts('StockChart', highChartOptions);
			var chart = $('#' + divId).highcharts('StockChart');

			// show loading spinner & hide 'no data message' during api request
			chart.showLoading();
			chart.hideNoData();

			// define series first; then build list for each metric expression
			var series = [];
			var objMetricCount = {};

			objMetricCount.totalCount = metricList.length;

			for (var i = 0; i < metricList.length; i++) {
				// make api call to get data for each metric item
				populateSeries(metricList[i], highChartOptions, series, divId, attributes, annotationExpressionList, objMetricCount);
			}
		}

		function populateSeries(metricItem, highChartOptions, series, divId, attributes, annotationExpressionList, objMetricCount) {

			$http({
				method: 'GET',
				url: CONFIG.wsUrl + 'metrics',
				params: {'expression': metricItem.expression}
			}).success(function(data){
				if (data && data.length > 0) {

					// check to update services dashboard
					if (attributes.smallchart) {
						// get last status values & broadcast to 'agStatusIndicator' directive
						var lastStatusVal = Object.keys(data[0].datapoints).sort().reverse()[0];
						lastStatusVal = data[0].datapoints[lastStatusVal];
						// updateServiceStatus(attributes, lastStatusVal);
						updateIndicatorStatus(attributes, lastStatusVal);
					}

					// metric item attributes are assigned to the data (i.e. name, color, etc.)
					var seriesWithOptions = copySeriesDataNSetOptions(data, metricItem);

					// add each metric item & data to series list
					Array.prototype.push.apply(series, seriesWithOptions);

				} else{
					growl.info('No data found for the metric expression: ' + JSON.stringify(metricItem.expression));
				}

				objMetricCount.totalCount = objMetricCount.totalCount - 1;

				if (objMetricCount.totalCount == 0) {
					bindDataToChart(divId, highChartOptions, series, annotationExpressionList);
				}
			}).error(function(data) {
				growl.error(data.message);
				objMetricCount.totalCount = objMetricCount.totalCount - 1;

				if (objMetricCount.totalCount == 0) {
					bindDataToChart(divId, highChartOptions, series, annotationExpressionList);
				}
			});
		}

		function copySeriesDataNSetOptions(data, metricItem) {
			var result = [];
			if (data) {
				for (var i = 0; i < data.length; i++) {
					var series = [];

					for (var key in data[i].datapoints) {
						var timestamp = parseInt(key);
						if (data[i].datapoints[key] != null) {
							var value = parseFloat(data[i].datapoints[key]);
							series.push([timestamp, value]);
						}
					}

					var metricName = (metricItem.name) ? metricItem.name : createSeriesName(data[i]);
					var metricColor = (metricItem.color) ? metricItem.color : null;
					var objSeries = {
						name: metricName,
						color: metricColor,
						data: series
					};
					var objSeriesWithOptions = setCustomOptions(objSeries, metricItem.metricSpecificOptions);

					result.push(objSeriesWithOptions);
				}
			} else {
				result.push({name: 'result', data: []});
			}
			return result;
		}

		// 'dataProcessing'
		function setCustomOptions(options, optionList){
			for(var idx in optionList) {
				var propertyName = optionList[idx].name;
				var propertyValue = optionList[idx].value;
				var result = constructObjectTree(propertyName, propertyValue);
				copyProperties(result,options);
			}
			return options;
		}

		// 'chartRendering' & 'dataProcessing'
		function bindDataToChart(divId, highChartOptions, series, annotationExpressionList) {
			// bind series data to highchart options
			highChartOptions.series = series;

			// display chart in DOM
			$('#' + divId).highcharts('StockChart', highChartOptions);
			var chart = $('#' + divId).highcharts('StockChart');

			// hide the loading spinner after data loads.
			if (chart) {
				chart.hideLoading();
			}

			// check if data exists, otherwise, show the 'no data' message.
			if ( chart && !chart.hasData() ) {
				chart.showNoData();
			}

			// ----------------

			populateAnnotations(annotationExpressionList, chart);
		}

		// 'dataProcessing'
		function populateAnnotations(annotationsList, chart){
			if (annotationsList && annotationsList.length>0 && chart) {
				for (var i = 0; i < annotationsList.length; i++) {
					addAlertFlag(annotationsList[i],chart);
				}
			}
		}

		// 'dataProcessing', update to return promise instead
		function addAlertFlag(annotationExpression, chart) {
			Annotations.query({expression: annotationExpression}, function (data) {
				if(data && data.length>0) {
					var forName = createSeriesName(data[0]);
					var series = copyFlagSeries(data);
					series.linkedTo = forName;

					for(var i=0;i<chart.series.length;i++){
						if(chart.series[i].name == forName){
							series.color = chart.series[i].color;
							break;
						}
					}

					chart.addSeries(series);
				}
			});
		}

		// 'dataProcessing'
		function getMetricExpressionList(metrics){
			var result = [];
			for(var i=0; i < metrics.length; i++){
				result.push(metrics[i].expression);
			}
			return result;
		}

		// 'dataProcessing'
		function copyHeatmapSeries(data, timeSpan) {
			var table = data.map(getHourlyAverage.bind(null, timeSpan));
			for (var i = 0; i < data.length; i++) {
				table[i].push(getAverage(data[i]));
			}
			var dataSeries = [];
			for (var i = 0; i < data.length; i++) {
				for (var j = 0; j < table[0].length; j++) {
					var intValue = table[data.length - 1 - i][j] ? Math.floor(table[data.length - 1 - i][j]) : null;
					dataSeries.push([j, i, intValue]);
				}
			}
			return dataSeries;
		}

		// 'dataProcessing'
		function copySeries(data) {
			var result = [];
			if (data) {
				for (var i = 0; i < data.length; i++) {
					var series = [];
					for(var key in data[i].datapoints) {
						var timestamp = parseInt(key);
						if(data[i].datapoints[key] !=null){
							var value = parseFloat(data[i].datapoints[key]);
							series.push([timestamp, value]);
						}
					}
					result.push({name: createSeriesName(data[i]), data: series});
				}
			} else {
				result.push({name: 'result', data: []});
			}
			return result;
		}

		// 'dataProcessing'
		function createSeriesName(metric) {
			var scope = metric.scope;
			var name = metric.metric;
			var tags = createTagString(metric.tags);
			return scope + ':' + name + tags;
		}

		// 'dataProcessing'
		function createTagString(tags) {
			var result = '';
			if (tags) {
				var tagString ='';
				for (var key in tags) {
					if (tags.hasOwnProperty(key)) {
						tagString += (key + '=' + tags[key] + ',');
					}
				}
				if(tagString.length) {
					result += '{';
					result += tagString.substring(0, tagString.length - 1);
					result += '}';
				}
			}
			return result;
		}

		// 'dataProcessing'
		function copyFlagSeries(data) {
			var result;
			if (data) {
				result = {type: 'flags', shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2};
				result.data = [];
				for (var i = 0; i < data.length; i++) {
					var flagData = data[i];
					result.data.push({x: flagData.timestamp, title: 'A', text: formatFlagText(flagData.fields)});
				}
			} else {
				result = null;
			}
			return result;
		}

		// 'dataProcessing'
		function formatFlagText(fields) {
			var result = '';
			if (fields) {
				for (var field in fields) {
					if (fields.hasOwnProperty(field)) {
						result += (field + ': ' + fields[field] + '<br/>');
					}
				}
			}
			return result;
		}

		// --------

		// 'chartRendering'
		function updateIndicatorStatus(attributes, lastStatusVal) {
			if (lastStatusVal < attributes.lo) {
				$('#' + attributes.name + '-status').removeClass('red orange green').addClass('red');
			} else if (lastStatusVal > attributes.lo && lastStatusVal < attributes.hi) {
				$('#' + attributes.name + '-status').removeClass('red orange green').addClass('orange');
			} else if (lastStatusVal > attributes.hi) {
				$('#' + attributes.name + '-status').removeClass('red orange green').addClass('green');
			}
		}

		// 'chartRendering'
		function updateChart(config, data, divId, annotationExpressionList, optionList, attributes) {
			var chartType = attributes.type ? attributes.type : 'LINE';

			if (data && data.length > 0) {
				var options = getOptionsByChartType(config,chartType);
				options.series = copySeries(data);
				//options.chart={renderTo: 'container',defaultSeriesType: 'line'};
				setCustomOptions(options,optionList);
				$('#' + divId).highcharts('StockChart', options);
			} else {
				$('#' + divId).highcharts('StockChart', getOptionsByChartType(config, chartType));
			}

			var chart = $('#' + divId).highcharts('StockChart');
			//chart.chart={renderTo: 'container',defaultSeriesType: 'line'};
			//chart.renderTo='container';
			//chart.defaultSeriesType='line';

			populateAnnotations(annotationExpressionList, chart);
		}

		// 'chartRendering'
		function updateTable(data, scope, divId, options) {
			if(data && data.length > 0) {

				var allTimestamps = {};
				var dps, timestamp;

				for(var i in data) {
					dps = data[i].datapoints;
					for(timestamp in dps) {
						if(!allTimestamps[timestamp]) {
							allTimestamps[timestamp] = [];
						}
					}
				}

				var columns = [{title: 'timestamp', value: 'Timestamp'}];
				for(var i in data) {
					dps = data[i].datapoints;
					if(dps) {
						columns.push({
							title: 'value' + i,
							value: createSeriesName(data[i])
						});

						for(timestamp in allTimestamps) {
							var values = allTimestamps[timestamp];
							if(dps[timestamp]) {
								values.push(parseFloat(dps[timestamp]));
							} else {
								values.push(undefined);
							}
							allTimestamps[timestamp] = values;
						}
					}
				}

				var tData = [];
				for(timestamp in allTimestamps) {
					var obj = {
						timestamp: parseInt(timestamp),
						date: $filter('date')(timestamp, 'medium')
					};

					for(var i in columns) {
						if(columns[i].title !== 'timestamp')
							obj[columns[i].title] = allTimestamps[timestamp][i-1];
					}
					tData.push(obj);
				}

				var tableConfig = {
					itemsPerPage: 10,
					fillLastPage: true
				};

				for(var i in options) {
					var option = options[i];
					if(option.name && option.value)
						tableConfig[option.name] = option.value;
				}


				scope.tData = tData;
				scope.config = tableConfig;

				var html = '<div style="overflow-x: scroll"><table class="table table-striped table-header-rotated" at-table at-paginated at-list="tData" at-config="config">';

				html += '<thead>';
				html += '<tr>';
				for(var i in columns) {
					html += '<th class="rotate-45" at-attribute="' + columns[i].title + '"><div><span>' + columns[i].value + '</span></div></th>';
				}
				html += '</tr>';
				html += '</thead>';

				html += '<tbody>';
				html += '<tr>';

				for(var i in columns) {
					if(columns[i].title === 'timestamp')
						html += '<td at-sortable at-attribute="' + columns[i].title + '">{{ item.date }}</td>';
					else
						html += '<td at-sortable at-attribute="' + columns[i].title + '">{{ item.' + columns[i].title + '}}</td>';
				}

				html += '</tr>';
				html += '</tbody>';

				html += '</table></div>';

				html += '<at-pagination at-list="tData" at-config="config"></at-pagination>';

				$('#' + divId).empty();
				$compile($('#' + divId).prepend(html))(scope);
			}
		}

		// 'chartRendering'
		function updateHeatmap(config, data, divId, optionList, attributes) {
			if(data && data.length>0) {
				var top = attributes.top? parseInt(attributes.top) : data.length;
				var options = getOptionsByHeatmapType(config, top);
				data.sort(compareAverage);
				data = data.slice(0, Math.min(top, data.length));
				var orgAxis = data.map(createSeriesName);
				var timeSpan = getTimeSpan(data);
				var timeAxis = getTimeAxis(timeSpan);
				var dataSeries = copyHeatmapSeries(data, timeSpan);
				options.series[0].data = dataSeries;
				options.xAxis.categories = timeAxis;
				options.yAxis.categories = orgAxis.reverse();
				setCustomOptions(options,optionList);
				$('#' + divId).highcharts(options);
			}else {
				$('#' + divId).highcharts('StockChart', getOptionsByChartType(config, 'LINE'));
			}
		}

		// 'chartOptions'
		function getOptionsByChartType(config, chartType, smallChart){
			var options = config ? angular.copy(config) : {};
			options.legend = {
				enabled: true,
				maxHeight: 62,
				itemStyle: {
					fontWeight: 'normal',
					fontSize: '10px'
				},
				navigation : {
					style : {
						fontWeight: 'normal',
						fontSize: '10px'
					}
				}
			};
			options.credits = {enabled: false};
			options.rangeSelector = {selected: 1, inputEnabled: false};
			options.xAxis = {
				type: 'datetime',
				ordinal: false
			};

			options.lang = {
				loading: '',    // override default 'Loading...' msg from displaying under spinner img.
				noData: 'No Data to Display'
			};

			// loading spinner for graph
			options.loading = {
				labelStyle: {
					top: '25%',
					backgroundImage: 'url("img/ajax-loader.gif")',
					backgroundSize: '80px 80px',
					backgroundRepeat: 'no-repeat',
					display: 'inline-block',
					width: '80px',
					height: '80px',
					backgroundColor: '#FFF'
				}
			};

			if(chartType && chartType.toUpperCase() === 'AREA'){
				options.plotOptions = {series: {animation: false}};
				options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
			}else  if(chartType && chartType.toUpperCase() === 'STACKAREA'){
				options.plotOptions = {
					area: {
						stacking: 'normal',
						// lineWidth: 1.5,
						dataGrouping: {
							enabled: true//,
							// groupPixelWidth: 2
						},
						animation: false,
						marker: {
							enabled: false
						}
					}
				};
				options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
			}
			else {
				options.plotOptions = {series: {animation: false}};
				options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
			}

			// override options for a 'small' chart, e.g. 'Services Status' dashboard
			if ( smallChart ) {
				options.legend.enabled = false;
				options.rangeSelector.enabled = false;

				options['scrollbar'] = {enabled: false};
				options['navigator'] = {enabled: false};

				options.chart.height = '120';
				options.chart.borderWidth = 0;

				// reset loading options, no spinner required
				options.lang = {
					loading: 'Loading...'
				};
				options.loading = {};
			}

			return options;
		}

		function getOptionsByHeatmapType(config, top){
			var options = config ? angular.copy(config) : {};
			options.credits = {enabled: false};
			options.chart = {
				type: 'heatmap',
				marginTop: 0,
				marginBottom: 60,
				height: 40 * top
			};
			options.title = {text: ''};
			options.xAxis = {
				categories: null
			};
			options.yAxis = {
				categories: null,
				title: null,
				labels: {
				}
			};
			options.colorAxis = {
				dataClasses: [{
					from: 0,
					to: 300,
					color: '#00FF00'
				},{
					from:300,
					to:400,
					color:'#FF8000'
				},{
					from:400,
					color:'#FF0040'
				}]
			};
			options.legend = {enabled: true};
			options.tooltip = {enabled: false};
			options.series = [{
				name: '',
				borderWidth: 1,
				data: null,
				dataLabels: {
					enabled: true,
					color: 'black',
					style: {
						textShadow: 'none',
						HcTextStroke: null
					}
				}
			}];
			return options;
		}

		// 'chartTools' --> moved to 'ChartRenderingService.chartTools'
		function getTimeAxis(timeSpan) {
			var hours = [
				'12AM', '1AM', '2AM', '3AM', '4AM', '5AM',
				'6AM', '7AM', '8AM', '9AM', '10AM', '11AM',
				'12PM', '1PM', '2PM', '3PM', '4PM', '5PM',
				'6PM', '7PM', '8PM', '9PM', '10PM', '11PM'
			];
			var axis = [];
			var firstHour = (new Date(timeSpan.begin)).getHours();
			for (var i = 0; i < timeSpan.span; i++) {
				axis.push(hours[(firstHour + i) % 24]);
			}
			axis.push('<b><i>Average</i></b>');
			return axis;
		}

		// --------

		// 'utilService'

		function compareAverage(a,b) {
			if (getAverage(a) < getAverage(b)) return 1;
			if (getAverage(a) > getAverage(b)) return -1;
			return 0;
		}

		function getTimeSpan(data) {
			var begin = 9999999999999;
			var end = 0;
			for (var i = 0; i < data.length; i++) {
				for (var time in data[i].datapoints) {
					begin = Math.min(begin, parseInt(time));
					end = Math.max(end, parseInt(time));
				}
			}
			var span = Math.floor(end/1000/60/60) - Math.floor(begin/1000/60/60) + 1;
			return {begin: begin, end: end, span: span};
		}

		function getAverage(data) {
			var total = 0;
			var count = 0;
			for (var time in data.datapoints) {
				total += parseInt(data.datapoints[time]);
				count += 1;
			}
			if (count > 0)
				return total / count;
			else
				return 0;
		}

		function getHourlyAverage(timeSpan, data) {
			var sums = Array.apply(null, Array(timeSpan.span)).map(Number.prototype.valueOf,0);
			var counts = Array.apply(null, Array(timeSpan.span)).map(Number.prototype.valueOf,0);
			var pivotHour = Math.floor(timeSpan.begin / 1000 / 60 / 60);
			for (var time in data.datapoints) {
				var hour = Math.floor(parseInt(time) / 1000 / 60 / 60);
				sums[hour - pivotHour] += parseInt(data.datapoints[time]);
				counts[hour - pivotHour] += 1;
			}
			var avgs = [];
			for (var i = 0; i < timeSpan.span; i++) {
				if (counts[i] > 0) avgs.push(sums[i] / counts[i]);
				else avgs.push(null);
			}
			return avgs;
		}

		function copyProperties(from, to){
			for (var key in from) {
				if (from.hasOwnProperty(key)) {
					if(!to[key] || typeof from[key] == 'string' || from[key] instanceof String ){//if from[key] is not an object and is last property then just copy so that it will overwrite the existing value
						to[key]=from[key];
					}else{
						copyProperties(from[key],to[key]);
					}
				}
			}
		}

		function constructObjectTree(name, value) {
			var result = {};
			var index = name.indexOf('.');
			if (index == -1) {
				result[name] = getParsedValue(value);
				return result;
			} else {
				var property = name.substring(0, index);
				result[property] = constructObjectTree(name.substring(index + 1), value);
				return result;
			}
		}

		function getParsedValue(value){

			if(value instanceof Object || value.length==0){
				return value;
			}

			if(value=='true'){
				return true;
			}else if(value=='false'){
				return false;
			}else if(!isNaN(value)){
				return parseInt(value);
			}
			return value;
		}
	}]);
