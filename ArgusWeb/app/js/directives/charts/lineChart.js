'use strict';
/*global angular:false, d3:false, $:false, window:false, screen:false, console:false */

angular.module('argus.directives.charts.lineChart', [])
.directive('lineChart', ['$timeout', 'Storage', 'ChartToolService', 'ChartElementService', 'UtilService', function($timeout, Storage, ChartToolService, ChartElementService, UtilService) {
	//--------------------resize all charts-------------------
	var resizeTimeout = 250; //the time for resize function to fire
	var resizeJobs = [];
	var timer;
	var fullscreenChartID;

	function resizeHelper(){
		$timeout.cancel(timer); //clear to improve performance
		timer = $timeout(function () {
			if (fullscreenChartID === undefined) {
				resizeJobs.forEach(function (resizeJob) { //resize all the charts
					resizeJob.resize();
				});
			} else { // resize only one chart that in fullscreen mode
				var chartToFullscreen = resizeJobs.filter(function (item) {
					return item.chartID === fullscreenChartID;
				});
				chartToFullscreen[0].resize();
				if (window.innerHeight !== screen.height) {
					// reset the ID after exiting full screen
					fullscreenChartID = undefined;
				}
			}
		}, resizeTimeout); //only execute resize after a timeout
	}

	d3.select(window).on('resize', resizeHelper);

	//---------------------sync all charts-----------------------
	var syncChartJobs = {};
	function syncChartMouseMoveAll(mouseX, focusChartId){
		for(var key in syncChartJobs){
			if(!syncChartJobs.hasOwnProperty(key) || key === focusChartId) continue;
			syncChartJobs[key].syncChartMouseMove(mouseX);
		}
	}

	function syncChartMouseOutAll(focusChartId){
		for(var key in syncChartJobs){
			if(!syncChartJobs.hasOwnProperty(key) || key === focusChartId) continue;
			syncChartJobs[key].syncChartMouseOut();
		}
	}

	return {
		restrict: 'E',
		replace: true,
		scope: {
			chartConfig: '=chartconfig',
			series: '=series',
			dateConfig: '=dateconfig'
		},
		templateUrl: 'js/templates/charts/topToolbar.html',
		controller: ['$scope', '$filter', '$uibModal', '$window', 'Metrics', 'DownloadHelper', 'growl',  '$routeParams', function($scope, $filter, $uibModal, $window, Metrics, DownloadHelper, growl, $routeParams) {
			$scope.hideMenu = true;
			$scope.dateRange = '';
			$scope.changeToFullscreen = false;
			$scope.dashboardId = $routeParams.dashboardId;

			$scope.sources = [];
			$scope.otherSourcesHidden = false;
			$scope.noDataSeries = [];
			$scope.invalidSeries = [];

			$scope.updateStorage = function () {
				Storage.set('menuOption_' + $scope.dashboardId + '_' + $scope.chartConfig.chartId, $scope.menuOption);
			};
			// read menuOption from local storage; use default one if there is none
			$scope.menuOption = angular.copy(Storage.get('menuOption_' + $scope.dashboardId + '_' + $scope.chartConfig.chartId));
			if ($scope.menuOption === null) {
				$scope.menuOption = angular.copy(ChartToolService.defaultMenuOption);
				$scope.updateStorage();
			}
			// reformat existing stored menuOption
			if ($scope.menuOption.tooltipConfig === undefined) {
				$scope.menuOption.tooltipConfig = {
					rawTooltip: $scope.menuOption.rawTooltip,
					customTooltipFormat: $scope.menuOption.customTooltipFormat,
					leadingNum: $scope.menuOption.leadingNum,
					trailingNum: $scope.menuOption.trailingNum,
					isTooltipSortOn: $scope.menuOption.isTooltipSortOn
				};
			}
			if ($scope.menuOption.yAxisConfig === undefined){
				$scope.menuOption.yAxisConfig = {
					formatYaxis: $scope.menuOption.formatYaxis,
					numTicksYaxis: $scope.menuOption.numTicksYaxis
				};
			}

			var dashboardId = $routeParams.dashboardId; //this is used in chartoptions scope
			$scope.openChartOptions = function(chartId, chartTitle) {
				if (!chartId) return;

				var optionsModal = $uibModal.open({
					// scope: $scope,
					resolve: {
						menuOption: function () {
							return $scope.menuOption;
						}
					},
					templateUrl: 'js/templates/modals/chartOptions.html',
					windowClass: 'chartOptions',
					size: 'lg',
					// controller: chartOptions,     // ['optionsModal', 'menuOption', ChartOptions],
					// controllerAs: 'ChartOptions',     // modal options
					controller: ['$scope', function($scope) {

						// set $scope items from $resolve method above - only way to 'watch' $scope for changes in chart options.
						$scope.menuOption = $scope.$resolve.menuOption;

						$scope.chartId = chartId;
						$scope.chartTitle = chartTitle;
						$scope.applyToAllGraphs = false;

						// display current date in 'sample' format
						var currDate = new Date();
						var sampleDateFormat = '%-m/%-d/%y %H:%M:%S'; // 'Sat Nov 5 1929 11:58'

						$scope.dateFormatOutput = d3.timeFormat(sampleDateFormat)(currDate);

						// update date format to show sample date in modal view
						$scope.updateDateFormatOutput = function() {
							var userInputDateFormat = $scope.menuOption.dateFormat ? $scope.menuOption.dateFormat : sampleDateFormat;
							$scope.dateFormatOutput = d3.timeFormat(userInputDateFormat)(new Date());
						};

						// display date in correct format when modal opens: either menuOptions OR current date with 'sampleDateFormat'
						$scope.updateDateFormatOutput();

						$scope.resetSettings = function () {
							// set menuOption back to default (both in the modal scope and outer chart scope). can be stored in separate properties file for charts
							$scope.menuOption = angular.copy(ChartToolService.defaultMenuOption);
							angular.element('#topTb-' + $scope.chartId).scope().menuOption = $scope.menuOption;
							// update localStorage
							// Storage.set('menuOption_' + dashboardId + '_' + $scope.chartId, $scope.menuOption);
						};

						$scope.updateSettingsToAllGraphs = function () {
							// update all graphs on dashboard with current scope.menuOption settings
							if ($scope.applyToAllGraphs) {
								//update localStorage for each chart
								resizeJobs.forEach(function (job) {
									Storage.set('menuOption_' + dashboardId + '_' + job.chartID, $scope.menuOption);
								});
							}
						};

						$scope.saveSettings = function () {
							Storage.set('menuOption_' + dashboardId + '_' + $scope.chartId, angular.copy($scope.menuOption));
							optionsModal.close();
							if ($scope.applyToAllGraphs) {
								// manually refresh page so all charts are updated with settings
								$window.location.reload();
							}
						};

						$scope.close = function () {
							optionsModal.close();
						};

						// add lightMask class when modal is opened
						optionsModal.opened.then(function () {
							$('body').addClass('lightMask');
						});

						// remove lightMask class when modal is closed
						optionsModal.result.then(function () {
							angular.noop();
						}, function () {
							$('body').removeClass('lightMask');
						});
					}]
				});
			};

			$scope.updateFullscreenChartID= function (clickedChartID) {
				// using the button to toggle on and off full screen
				fullscreenChartID = clickedChartID;
				$scope.changeToFullscreen = screen.height !== window.innerHeight;
			};

			$scope.downloadData = function (queryFunction) {
				// each metric expression will be a separate file
				var dataHandler, filename, chartTitle;
				if ($scope.chartConfig.title !== undefined && $scope.chartConfig.title.text !== undefined) {
					chartTitle = $scope.chartConfig.title.text;
				} else {
					chartTitle = 'data';
				}
				switch (queryFunction) {
					case 'query':
						dataHandler = function (data) { return JSON.stringify(data.slice(0, data.length)); };
						filename = chartTitle + '.json';
						break;
					case 'downloadCSV':
						dataHandler = function (data) { return data[0]; };
						filename = chartTitle + '.csv';
						break;
				}
				$scope.chartConfig.expressions.map(function (expression) {
					growl.info('Downloading data...');
					Metrics[queryFunction]({expression: expression}).$promise.then(function (data) {
						DownloadHelper.downloadFile(dataHandler(data), filename);
					}, function (error) {
						growl.error('Data cannot be download this time');
						console.log(error);
					});
				});
			};

			$scope.labelTextColor = function(source) {
				var color;
				var chartType = $scope.chartConfig.chartType;
				if (source.displaying) {
					var elementWithColor = d3.select('.' + source.graphClassName + '_brush' + UtilService.capitalizeString(chartType));
					if (elementWithColor.empty()) {
						// have a default color
						color = source.color;
					} else {
						switch (chartType) {
							case 'area':
								color = elementWithColor.style('fill');
								break;
							// case 'line':
							default:
								color = elementWithColor.style('stroke');
						}
					}
				} else {
					color = '#FFF';
				}
				return color;
			};

			$scope.toggleSource = function(source) {
				toggleGraphOnOff(source);
				updateGraphScale();
			};

			// show ONLY this 1 source, hide all others
			$scope.hideOtherSources = function(sourceToShow) {
				var sources = $scope.sources;
				for (var i = 0; i < sources.length; i++) {
					if (sourceToShow.name !== sources[i].name) {
						toggleGraphOnOff(sources[i]);
					}
				}
				updateGraphScale();
				$scope.otherSourcesHidden = !$scope.otherSourcesHidden;
			};

			function toggleGraphOnOff (source) {
				// d3 select with dot in ID name: http://stackoverflow.com/questions/33502614/d3-how-to-select-element-by-id-when-there-is-a-dot-in-id
				// var graphID = source.name.replace(/\s+/g, '');
				var displayProperty = source.displaying? 'none' : null;
				source.displaying = !source.displaying;
				d3.selectAll('.' + source.graphClassName)
					.style('display', displayProperty)
					.attr('displayProperty', displayProperty);//this is for recording the display property when circle is outside range
			}

			function updateGraphScale () {
				$scope.reScaleY();
				$scope.redraw();
			}
		}],
		// compile: function (iElement, iAttrs, transclude) {},
		link: function (scope, element) {
			/**
			 * not using chartId because when reload the chart by 'sumbit' button
			 * or other single page app navigate button the chartId is not reset
			 * to 1, only by refreshing the page would the chartId be reset to 0
			 */

			var chartId = scope.chartConfig.chartId;
			var chartType = scope.chartConfig.chartType;
			var series = scope.series;
			var startTime = scope.dateConfig.startTime;
			var endTime = scope.dateConfig.endTime;
			var GMTon = scope.dateConfig.gmt;
			var chartOptions = scope.chartConfig;

			/** 'smallChart' settings:
				height: 150
				no timeline, date range, option menu
				only left-side Y axis
				fewer x-axis tick labels
			*/

			var agYMin, agYMax, yScaleType = 'linear';
			var yScaleConfigValue;
			if (chartOptions.yAxis){
				agYMin = chartOptions.yAxis.min;
				agYMax = chartOptions.yAxis.max;
				if ((chartOptions.yAxis.type !== undefined) && (typeof chartOptions.yAxis.type === 'string')) {
					var yScaleTypeInput = chartOptions.yAxis.type.toLowerCase();
					switch (yScaleTypeInput){
						case 'log':
						case 'logarithmic':
							yScaleType = 'log';
							yScaleConfigValue = parseInt(chartOptions.yAxis.base);
							break;
						case 'pow':
						case 'power':
							yScaleType = 'power';
							yScaleConfigValue = parseInt(chartOptions.yAxis.exponent);
							break;
					}
				}
			}
			// provide support for yaxis lower case situation.
			if (chartOptions.yaxis !== undefined) {
				agYMin = agYMin || chartOptions.yaxis.min;
				agYMax = agYMax || chartOptions.yaxis.max;
			}
			if (isNaN(agYMin)) agYMin = undefined;
			if (isNaN(agYMax)) agYMax = undefined;

			var dateExtent; //extent of non empty data date range
			var topToolbar = $(element); //jquery selection
			var container = topToolbar.parent()[0];//real DOM

			var maxScaleExtent = 100; //zoom in extent
			var currSeries = series;

			//---------------- check if it is a small chart (need to be called first) --------------
			var isSmallChart = chartOptions.smallChart === undefined? false: chartOptions.smallChart;

			var dateFormatter = ChartToolService.generateDateFormatter(GMTon, scope.menuOption.dateFormat, isSmallChart);
			var messagesToDisplay = [ChartToolService.defaultEmptyGraphMessage];
			// color scheme
			var z = ChartToolService.setColorScheme(scope.menuOption.colorPalette);
			// determine chart layout and dimensions
			var containerHeight = isSmallChart ? 150 : 330;
			var containerWidth = $('#' + chartId).width();
			// remember the original size
			var defaultContainerWidth = -1;
			if (chartOptions.chart !== undefined) {
				containerHeight = chartOptions.chart.height === undefined ? containerHeight: chartOptions.chart.height;
				if (chartOptions.chart.width !== undefined) {
					containerWidth = chartOptions.chart.width;
					defaultContainerWidth = containerWidth;
				}
			}
			var defaultContainerHeight = containerHeight;
			// calculate width, height and margin for both brush and graphs
			var allSize = ChartToolService.calculateDimensions(containerWidth, containerHeight, isSmallChart, scope.menuOption.isBrushOn);

			//setup graph variables
			var x, x2, y, y2, yScalePlain,
				xAxis, xAxis2, yAxis, yAxisR, xGrid, yGrid,
				graph, graph2,
				brush, brushMain, zoom,
				svg, svg_g, mainChart, xAxisG, xAxisG2, yAxisG, yAxisRG, xGridG, yGridG, //g
				focus, context, clip, brushG, brushMainG, chartRect,//g
				tooltip, tipBox, tipItems,
				crossLine,
				names, colors, graphClassNames,
				flagsG, labelTip;

			// setup: initialize all the graph variables
			function setUpGraphs() {
				var xy = ChartToolService.getXandY(scope.dateConfig, allSize, yScaleType, yScaleConfigValue);
				x = xy.x;
				y = xy.y;
				yScalePlain = xy.yScalePlain;

				var axises = ChartElementService.createAxisElements(x, y, isSmallChart, scope.menuOption.yAxisConfig);
				var grids = ChartElementService.createGridElements(x, y, allSize, isSmallChart, scope.menuOption.yAxisConfig.numTicksYaxis);

				xAxis = axises.xAxis;
				yAxis = axises.yAxis;
				yAxisR = axises.yAxisR;
				xGrid = grids.xGrid;
				yGrid = grids.yGrid;

				graph = ChartElementService.createGraph(x, y, chartType);

				var smallBrush = ChartElementService.createBushElements(scope.dateConfig, allSize, isSmallChart, chartType, brushed, yScaleType, yScaleConfigValue);
				xAxis2 = smallBrush.xAxis;
				x2 = smallBrush.x;
				y2 = smallBrush.y;
				graph2 = smallBrush.graph;
				brush = smallBrush.brush;

				brushMain = ChartElementService.createMainBush(allSize, brushedMain);

				var chartContainerElements = ChartElementService.generateMainChartElements(allSize, container);
				svg = chartContainerElements.svg;
				svg_g = chartContainerElements.svg_g; // clip, flags, brush area,
				mainChart = chartContainerElements.mainChart; // zoom, axis, grid

				zoom = ChartElementService.createZoom(allSize, zoomed, mainChart);

				var axisesElement = ChartElementService.appendAxisElements(allSize, mainChart, axises, chartOptions.xAxis, chartOptions.yAxis);
				xAxisG = axisesElement.xAxisG;
				yAxisG = axisesElement.yAxisG;
				yAxisRG = axisesElement.yAxisRG;

				var gridsElement = ChartElementService.appendGridElements(allSize, mainChart, grids);
				xGridG = gridsElement.xGridG;
				yGridG = gridsElement.yGridG;

				//Brush, zoom, pan
				//clip path
				clip = ChartElementService.appendClip(allSize, svg_g, chartId);

				//brush area
				var smallBrushElement = ChartElementService.appendBrushWithXAxisElements(allSize, svg_g, xAxis2);
				context = smallBrushElement.context;
				xAxisG2 = smallBrushElement.xAxisG2;

				// flags and annotations
				var flagsElement = ChartElementService.appendFlagsElements(svg_g, chartId);
				flagsG = flagsElement.flagsG;
				labelTip = flagsElement.labelTip;

				// Mouseover focus and crossline
				focus = ChartElementService.appendFocus(mainChart);
				crossLine = ChartElementService.appendCrossLine(focus);

				// tooltip setup
				var tooltipElement = ChartElementService.appendTooltipElements(svg_g);
				tooltip = tooltipElement.tooltip;
				tipBox = tooltipElement.tipBox;
				tipItems = tooltipElement.tipItems;
				// get color
				ChartToolService.bindDefaultColorsWithSources(z, names);
			}

			function renderGraphs (series) {
				// downsample if its needed
				currSeries = ChartToolService.downSample(series, containerWidth, scope.menuOption.downSampleMethod);

				var xyDomain = ChartToolService.getXandYDomainsOfSeries(currSeries);
				var xDomain = xyDomain.xDomain;
				var yDomain = xyDomain.yDomain;

				//startTime/endTime will not be 0
				if(!startTime) startTime = xDomain[0];
				if(!endTime) endTime = xDomain[1];
				x.domain(xDomain); //doing this cause some date range are defined in metric queries and regardless of ag-date

				// if only a straight line is plotted
				if (yDomain[0] === yDomain[1]) {
					yDomain[0] -= ChartToolService.yAxisPadding;
					yDomain[1] += 3 * ChartToolService.yAxisPadding;
				}
				// check if user has provide any y domain requirement
				var mainChartYDomain = yDomain.slice();
				if (agYMin !== undefined) mainChartYDomain[0] = agYMin;
				if (agYMax !== undefined) mainChartYDomain[1] = agYMax;
				if (yScaleType === 'log') {
					// log(0) does not exist
					if (mainChartYDomain[0] === 0) mainChartYDomain[0] = 1;
					if (mainChartYDomain[1] === 0) mainChartYDomain[1] = 1;
					if (yDomain[0] === 0) yDomain[0] = 1;
					if (yDomain[1] === 0) yDomain[1] = 1;

				}
				y.domain(mainChartYDomain);

				// update brush's x and y
				x2.domain(xDomain);
				y2.domain(yDomain);

				dateExtent = xDomain;

				currSeries.forEach(function (metric, index) {
					if (metric.data.length === 0) return;
					var tempColor = metric.color === null ? z(metric.name) : metric.color;
					// TODO: make this a constant somewhere else
					var chartOpacity = chartType === 'area'? 0.9 - 0.7*(index/currSeries.length): 1;
					ChartElementService.renderGraph(mainChart, tempColor, metric, graph, chartId, chartType, chartOpacity);
					ChartElementService.renderBrushGraph(context, tempColor, metric, graph2, chartType, chartOpacity);
					ChartElementService.renderFocusCircle(focus, tempColor, metric.graphClassName);
					ChartElementService.renderTooltip(tipItems, tempColor, metric.graphClassName);
					// annotations
					if (!metric.flagSeries) return;
					var flagSeries = metric.flagSeries.data;
					flagSeries.forEach(function (d) {
						ChartElementService.renderAnnotationsLabels(flagsG, labelTip, tempColor, metric.graphClassName, d, dateFormatter);
					});
				});

				maxScaleExtent = ChartToolService.setZoomExtent(series, zoom);
				ChartElementService.updateAnnotations(series, scope.sources, x, flagsG, allSize.height);
			}

			//this function add the overlay element to the graph when mouse interaction takes place
			//need to call this after drawing the lines in order to put mouse interaction overlay on top
			function addOverlay() {
				//the graph rectangle area
				chartRect = ChartElementService.appendChartRect(allSize, mainChart, mouseOverChart, mouseOutChart, mouseMove, zoom);
				// the brush overlay
				brushG = ChartElementService.appendBrushOverlay(context, brush, x.range());
				brushMainG = ChartElementService.appendMainBrushOverlay(mainChart, mouseOverChart, mouseOutChart, mouseMove, zoom, brushMain);
			}

			function mouseMove() {
				if (!currSeries || currSeries.length === 0) return;
				var mousePositionData = ChartElementService.getMousePositionData(x, y, d3.mouse(this));
				var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);

				ChartElementService.updateFocusCirclesToolTipsCrossLines(allSize, dateFormatter,
					scope.menuOption.yAxisConfig.formatYaxis, scope.menuOption.tooltipConfig, focus, tipItems, tipBox,
					series, scope.sources, x, y, mousePositionData, brushInNonEmptyRange);

				if(chartId in syncChartJobs) syncChartMouseMoveAll(mousePositionData.mouseX, chartId);
			}

			function mouseOverChart() {
				var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
				ChartElementService.showFocusAndTooltip(focus, tooltip, scope.menuOption.isTooltipOn, brushInNonEmptyRange);
				crossLine.selectAll('.crossLineY').style('display', null);
			}

			function mouseOutChart() {
				ChartElementService.hideFocusAndTooltip(focus, tooltip);
				syncChartMouseOutAll();
			}

			//sync vertical focus line across charts, mouseX is the timestamp
			function syncChartMouseMove(mouseX) {
				if(mouseX < x.domain()[0] || mouseX > x.domain()[1]){
					// mouseOutChart
					ChartElementService.hideFocusAndTooltip(focus, tooltip);
				} else {
					// moueOverChart
					var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
					ChartElementService.showFocusAndTooltip(focus, tooltip, scope.menuOption.isTooltipOn, brushInNonEmptyRange);
					crossLine.selectAll('.crossLineY').style('display', 'none');
					// mouseMove
					var mousePositionData = {
						mouseX: mouseX,
						positionX: x(mouseX),
						positionY: focus.select('[name=crossLineTipX]').node().getBBox().height + 3  // crossLineTipPadding
					};
					ChartElementService.updateFocusCirclesToolTipsCrossLines(
						allSize, dateFormatter, scope.menuOption.yAxisConfig.formatYaxis, scope.menuOption.tooltipConfig, focus, tipItems, tipBox,
						series, scope.sources, x, y, mousePositionData, brushInNonEmptyRange);
				}
			}

			//clear vertical lines and tooltip when move mouse off the focus chart
			function syncChartMouseOut() {
				ChartElementService.hideFocusAndTooltip(focus, tooltip);
			}

			function addToSyncCharts() {
				syncChartJobs[chartId] = {
					syncChartMouseMove: syncChartMouseMove,
					syncChartMouseOut: syncChartMouseOut
				};
			}

			function removeFromSyncCharts() {
				delete syncChartJobs[chartId];
			}

			//brushed
			function brushed() {
				// ignore the case when it is called by the zoomed function
				if (d3.event.sourceEvent && (d3.event.sourceEvent.type === 'zoom' )) return;
				var s = d3.event.selection || x2.range();
				x.domain(s.map(x2.invert, x2));     //rescale the domain of x axis
													//invert the x value in brush axis range to the
													//value in domain

				//ajust currSeries to the brushed period
				currSeries = ChartElementService.adjustSeriesAndTooltips(series, scope.sources, x, tipItems, containerWidth, scope.menuOption.downSampleMethod);

				//rescale domain of y axis
				ChartElementService.reScaleYAxis(currSeries, scope.sources, x, y, yScalePlain, agYMin, agYMax);

				//redraw
				redraw();

				//sync with zoom
				chartRect.call(zoom.transform, d3.zoomIdentity
					.scale(allSize.width / (s[1] - s[0]))
					.translate(-s[0], 0));

				if (brushMainG) {
					brushMainG.call(zoom.transform, d3.zoomIdentity
						.scale(allSize.width / (s[1] - s[0]))
						.translate(-s[0], 0));
				}
			}

			function brushedMain() {
				var selection = d3.event.selection; //the brushMain selection
				if (selection) {
					var start = x.invert(selection[0]);
					var end = x.invert(selection[1]);
					var range = end - start;
					brushMainG.call(brushMain.move, null);
					if (range * maxScaleExtent < x2.domain()[1] - x2.domain()[0]) return;
					x.domain([start, end]);
					brushG.call(brush.move, [x2(start), x2(end)]);
				}
			}

			//zoomed
			function zoomed() {
				// ignore the case when it is called by the brushed function
				if (d3.event.sourceEvent && (d3.event.sourceEvent.type === 'brush' || d3.event.sourceEvent.type === 'end'))return;
				var t = d3.event.transform;
				x.domain(t.rescaleX(x2).domain());  //rescale the domain of x axis
													//invert the x value in brush axis range to the
													//value in domain
				//ajust currSeries to the brushed period
				currSeries = ChartElementService.adjustSeriesAndTooltips(series, scope.sources, x, tipItems, containerWidth, scope.menuOption.downSampleMethod);

				//rescale domain of y axis
				ChartElementService.reScaleYAxis(currSeries, scope.sources, x, y, yScalePlain, agYMin, agYMax);

				//redraw
				redraw();
				// sync the brush
				context.select('.brush').call(brush.move, x.range().map(t.invertX, t));

				// sync the crossLine
				var mousePositionData = ChartElementService.getMousePositionData(x, y, d3.mouse(this));
				var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
				ChartElementService.updateFocusCirclesPositionWithZoom(x, y, focus, brushInNonEmptyRange);
				ChartElementService.updateCrossLines(allSize, dateFormatter, scope.menuOption.yAxisConfig.formatYaxis, focus, mousePositionData);
			}

			//redraw the line with restrict
			function redraw() {
				//redraw
				if (ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent)) {
					ChartElementService.redrawGraphs(currSeries, scope.sources, chartType, graph, mainChart);
				}
				ChartElementService.redrawAxis(xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG);
				ChartElementService.redrawGrid(xGrid, xGridG, yGrid, yGridG);
				ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
				ChartElementService.updateAnnotations(series, scope.sources, x, flagsG, allSize.height);
			}

			//have to register this as scope function cause toggleGraphOnOff is outside link function
			scope.reScaleY = function () {
				ChartElementService.reScaleYAxis(currSeries, scope.sources, x, y, yScalePlain, agYMin, agYMax);
			};

			//have to register this as scope function cause toggleGraphOnOff is outside link function
			scope.redraw = redraw;

			//precise resize without removing and recreating everything
			function resize () {
				if (series === 'series' || !series) {
					return;
				}
				var tempSizeInfo = ChartToolService.updateContainerSize(container, defaultContainerHeight, defaultContainerWidth, isSmallChart, scope.menuOption.isBrushOn, scope.changeToFullscreen);
				allSize = tempSizeInfo.newSize;
				containerHeight = tempSizeInfo.containerHeight;
				containerWidth = tempSizeInfo.containerWidth;

				if (allSize.width < 0) return; //it happens when click other tabs (like 'edit'/'history', the charts are not destroyed

				if (series.length > 0) {
					var tempX = x.domain(); //remember that when resize
					var needToAdjustHeight = (fullscreenChartID !== undefined) ||
											(!scope.menuOption.isBrushOn && allSize.height2 === 0) ||
											(scope.menuOption.isBrushOn && allSize.height2 !== 0);

					ChartElementService.resizeClip(allSize, clip, needToAdjustHeight);
					ChartElementService.resizeChartRect(allSize, chartRect, needToAdjustHeight);
					ChartToolService.updateXandYRange(allSize, x, y, needToAdjustHeight);
					ChartElementService.resizeBrush(allSize, brush, brushG, context, x2, xAxis2, xAxisG2, y2, needToAdjustHeight);
					ChartElementService.resizeMainBrush(allSize, brushMain, brushMainG);
					ChartElementService.resizeZoom(allSize, zoom);
					ChartElementService.resizeMainChartElements(allSize, svg, svg_g, needToAdjustHeight);
					ChartElementService.resizeGrid(allSize, xGrid, xGridG, yGrid, yGridG, needToAdjustHeight);
					if (chartType === 'scatter') {
						graph.x = x;
						graph.y = y;
						graph2.x = x2;
						graph2.y = y2;
					}
					ChartElementService.resizeGraphs(svg_g, graph, chartType);
					ChartElementService.resizeBrushGraphs(svg_g, graph2, chartType);


					ChartElementService.resizeAxis(allSize, xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, needToAdjustHeight, mainChart, chartOptions.xAxis);

					if (tempX[0].getTime() === x2.domain()[0].getTime() &&
						tempX[1].getTime() === x2.domain()[1].getTime()) {
						ChartElementService.resetBothBrushes(svg_g, ['.brush', '.brushMain'], brush);
					} else {
						//restore the zoom&brush
						context.select('.brush').call(brush.move, [x2(tempX[0]), x2(tempX[1])]);
					}
					currSeries = ChartElementService.adjustSeriesAndTooltips(series, scope.sources, x, tipItems, containerWidth, scope.menuOption.downSampleMethod);
				} else {
					svg = ChartElementService.appendEmptyGraphMessage(allSize, svg, container, messagesToDisplay);
				}
			}

			function setupMenu () {
				//button set up

				//dynamically enable button for brush time period(1h/1d/1w/1m/1y)
				var range = dateExtent[1] - dateExtent[0];
				if (range > 3600000) {
					//enable 1h button
					$('[name=oneHour]', topToolbar).prop('disabled', false)
						.click(ChartElementService.setBrushInTheMiddleWithMinutes(60, x, x2, context, brush));
				}
				if (range > 3600000 * 24) {
					//enable 1d button
					$('[name=oneDay]', topToolbar).prop('disabled', false)
						.click(ChartElementService.setBrushInTheMiddleWithMinutes(60*24, x, x2, context, brush));
				}
				if (range > 3600000 * 24 * 7) {
					//enable 1w button
					$('[name=oneWeek]', topToolbar).prop('disabled', false)
						.click(ChartElementService.setBrushInTheMiddleWithMinutes(60*24*7, x, x2, context, brush));
				}
				if (range > 3600000 * 24 * 30) {
					//enable 1month button
					$('[name=oneMonth]', topToolbar).prop('disabled', false)
						.click(ChartElementService.setBrushInTheMiddleWithMinutes(60*24*30, x, x2, context, brush));
				}
				if (range > 3600000 * 24 * 365) {
					//enable 1y button
					$('[name=oneYear]', topToolbar).prop('disabled', false)
						.click(ChartElementService.setBrushInTheMiddleWithMinutes(60*24*365, x, x2, context, brush));
				}

				if (scope.menuOption.isBrushMainOn) {
					brushMainG.style('display', null);
				} else {
					brushMainG.style('display', 'none');
				}
				// no wheel zoom on page load
				if (!scope.menuOption.isWheelOn) {
					chartRect.on('wheel.zoom', null);   // does not disable 'double-click' to zoom
					brushMainG.on('wheel.zoom', null);
				}
			}

			// create graph only when there is data
			if (!series || series.length === 0) {
				//this should never happen
				console.log('Empty data from chart data processing');
			} else {
				// set up legend
				names = series.map(function(metric) { return metric.name; });
				colors = series.map(function(metric) { return metric.color; });
				graphClassNames = series.map(function(metric) { return metric.graphClassName; });
				scope.sources = ChartToolService.createSourceListForLegend(names, graphClassNames, colors, z);
				// filter out series with no data
				var hasNoData, emptyReturn, invalidExpression;
				var tempSeries = [];
				for (var i = 0; i < series.length; i++) {
					if (series[i].invalidMetric) {
						scope.invalidSeries.push(series[i]);
						invalidExpression = true;
					} else if (series[i].noData) {
						scope.noDataSeries.push(series[i]);
						emptyReturn = true;
					} else if (series[i].data.length === 0) {
						hasNoData = true;
					} else {
						// only keep the metric that's graphable
						tempSeries.push(series[i]);
					}
				}
				series = tempSeries;

				if (series.length > 0) {
					scope.hideMenu = false;
					// Update graph on new metric results
					setUpGraphs();
					renderGraphs(series);
					addOverlay();

					// dont need to setup everything for a small chart
					ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
					ChartElementService.resetBothBrushes(svg_g, ['.brush', '.brushMain'], brush); //to remove the brush cover first for user the drag
					setupMenu();
				} else {
					// generate content for no graph message
					if (invalidExpression) {
						messagesToDisplay.push('Metric does not exist in TSDB');
						for (i = 0; i < scope.invalidSeries.length; i ++) {
							messagesToDisplay.push(scope.invalidSeries[i].errorMessage);
						}
						messagesToDisplay.push('(Failed metrics are black in the legend)');
					}
					if (emptyReturn) {
						messagesToDisplay.push('No data returned from TSDB');
						messagesToDisplay.push('(Empty metrics are labeled maroon)');
					}
					if (hasNoData) {
						messagesToDisplay.push('No data found for metric expressions');
						messagesToDisplay.push('(Valid sources have normal colors)');
					}
					svg = ChartElementService.appendEmptyGraphMessage(allSize, svg, container, messagesToDisplay);
				}
			}

			//TODO improve the resize efficiency if performance becomes an issue
			element.on('$destroy', function(){
				if(resizeJobs.length){
					resizeJobs = [];
					syncChartJobs = {};//this get cleared too
				}
			});

			resizeJobs.push({
				chartID: chartId,
				resize: resize
			});

			if (scope.menuOption.isSyncChart) {
				addToSyncCharts();
			}

			// watch changes from chart options modal to update graph
			scope.$watch('menuOption.colorPalette', function (newValue) {
				ChartElementService.updateColors(newValue, names, colors, graphClassNames, chartType);
			}, true);

			scope.$watch('menuOption.dateFormat', function (newValue) {
				dateFormatter = ChartToolService.generateDateFormatter(GMTon, newValue, isSmallChart);
				ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
			}, true);

			scope.$watch('menuOption.isBrushMainOn', function (newValue) {
				ChartElementService.toggleElementShowAndHide(newValue, brushMainG);
			}, true);

			scope.$watch('menuOption.isWheelOn', function (newValue) {
				ChartElementService.toggleWheel(newValue, zoom, chartRect, brushMainG);
			}, true);

			scope.$watch('menuOption.yAxisConfig', function (newValue) {
				yAxis.ticks(newValue.numTicksYaxis)
					.tickFormat(d3.format(newValue.formatYaxis));
				yGrid.ticks(newValue.numTicksYaxis);
				yAxisG.call(yAxis);
				yGridG.call(yGrid);
			}, true);

			scope.$watch('menuOption.isBrushOn', function (newValue) {
				ChartElementService.toggleElementShowAndHide(newValue, context);
				resize();
			}, true);

			scope.$watch('menuOption.isTooltipOn', function (newValue) {
				ChartElementService.toggleElementShowAndHide(newValue, tooltip);
				if (scope.menuOption.isTooltipOn) mouseOutChart(); // hide the left over tooltip on the chart
			}, true);

			scope.$watch('menuOption.isSyncChart', function (newValues) {
				if (newValues) {
					addToSyncCharts();
				} else {
					removeFromSyncCharts();
				}
			}, true);

			scope.$watch('menuOption.downSampleMethod', function (newValue) {
				currSeries = ChartElementService.adjustSeriesAndTooltips(series,  scope.sources, x, tipItems, containerWidth, newValue);
				ChartElementService.reScaleYAxis(currSeries, scope.sources, x, y, yScalePlain, agYMin, agYMax);
				redraw();
			}, true);
		}
	};
}]);
