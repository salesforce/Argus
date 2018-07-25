'use strict';
/*global angular:false, d3:false, $:false, window:false, screen:false, console:false */

angular.module('argus.directives.charts.lineChart', [])
.directive('lineChart', ['$timeout', 'Storage', 'ChartToolService', 'ChartElementService', function($timeout, Storage, ChartToolService, ChartElementService) {
	//--------------------resize all charts-------------------
	var AllChartIds = [];
	var fullscreenChartID;
	var syncChartJobs;
	//
	// function resizeHelper(){
	// 	$timeout.cancel(timer); //clear to improve performance
	// 	timer = $timeout(function () {
	// 		if (fullscreenChartID === undefined) {
	// 			resizeJobs.forEach(function (resizeJob) { //resize all the charts
	// 				resizeJob.resize();
	// 			});
	// 		} else { // resize only one chart that in fullscreen mode
	// 			var chartToFullscreen = resizeJobs.filter(function (item) {
	// 				return item.chartID === fullscreenChartID;
	// 			});
	// 			chartToFullscreen[0].resize();
	// 			if (window.innerHeight !== screen.height) {
	// 				// reset the ID after exiting full screen
	// 				fullscreenChartID = undefined;
	// 			}
	// 		}
	// 	}, resizeTimeout); //only execute resize after a timeout
	// }
	//
	// d3.select(window).on('resize', resizeHelper);

	//---------------------sync all charts-----------------------

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
			$scope.showToggleSources = true;
			$scope.hideMenu = true;
			$scope.dateRange = '';
			$scope.changeToFullscreen = false;
			$scope.dashboardId = $routeParams.dashboardId;
			$scope.sources = [];
			$scope.otherSourcesHidden = false;
			$scope.noDataSeries = [];
			$scope.invalidSeries = [];
			var hiddenSourceNames = [];

			syncChartJobs = ChartToolService.getOrCreateSyncChartJobs($scope.dashboardId);

			$scope.extraYAxisSet = new Set();
			$scope.series.forEach(function(e){
				if (e.extraYAxis) $scope.extraYAxisSet.add(e.extraYAxis);
			});

			$scope.updateStorage = function () {
				Storage.set('menuOption_' + $scope.dashboardId + '_' + $scope.chartConfig.chartId, $scope.menuOption);
			};
			// read menuOption from local storage; use default one if there is none
			$scope.menuOption = angular.copy(Storage.get('menuOption_' + $scope.dashboardId + '_' + $scope.chartConfig.chartId));
			if ($scope.menuOption === null || $scope.menuOption === undefined) {
				$scope.menuOption = angular.copy($scope.chartConfig.smallChart ?
					 ChartToolService.defaultMenuOptionSmallChart : ChartToolService.defaultMenuOption);
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
			if ($scope.menuOption.yAxisConfig === undefined) {
				$scope.menuOption.yAxisConfig = {
					formatYaxis: $scope.menuOption.formatYaxis,
					numTicksYaxis: $scope.menuOption.numTicksYaxis
				};
			}
			if ($scope.menuOption.isSnapCrosslineOn === undefined) {
				$scope.menuOption.isSnapCrosslineOn = true;
			}
			if ($scope.menuOption.localTimezone === undefined) {
				$scope.menuOption.localTimezone = false;
			}
			if ($scope.menuOption.showEmptyRange === undefined) {
				$scope.menuOption.showEmptyRange = false;
			}

			var dashboardId = $routeParams.dashboardId; //this is used in chartoptions scope
			// user interactions
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
								AllChartIds.forEach(function (job) {
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
				return source.displaying? source.color: '#FFF';
			};

			$scope.toggleSource = function(source) {
				toggleGraphOnOff(source);
				$scope.updateGraphAndScale(hiddenSourceNames);
			};

			// show ONLY this 1 source, hide all others
			$scope.hideOtherSources = function(sourceToShow) {
				var sources = $scope.sources;
				for (var i = 0; i < sources.length; i++) {
					if (sourceToShow.name !== sources[i].name) {
						toggleGraphOnOff(sources[i]);
					}
				}
				$scope.updateGraphAndScale(hiddenSourceNames);
				$scope.otherSourcesHidden = !$scope.otherSourcesHidden;
			};

			// helper function to hide a graph and its related items
			function toggleGraphOnOff (source) {
				// d3 select with dot in ID name: http://stackoverflow.com/questions/33502614/d3-how-to-select-element-by-id-when-there-is-a-dot-in-id
				// var graphID = source.name.replace(/\s+/g, '');
				var displayProperty;
				if (source.displaying) {
					displayProperty = 'none';
					hiddenSourceNames.push(source.name);
				} else {
					displayProperty = null;
					hiddenSourceNames = hiddenSourceNames.filter(function(i) {
						return i !== source.name;
					});
				}
				source.displaying = !source.displaying;
				ChartElementService.toggleGraphDisplay(source, displayProperty);
				// d3.selectAll('.' + source.graphClassName)
				// 	.style('display', displayProperty)
				// 	.attr('displayProperty', displayProperty);//this is for recording the display property when circle is outside range
			}
		}],
		// compile: function (iElement, iAttrs, transclude) {},
		link: function (scope, element) {
			/**
			 * not using chartIdIndex because when reload the chart by 'sumbit' button
			 * or other single page app navigate button the chartId is not reset
			 * to 1, only by refreshing the page would the chartId be reset to 0
			 */

			var chartId = scope.chartConfig.chartId;
			var chartType = scope.chartConfig.chartType;
			var series = scope.series;
			var GMTon = !scope.menuOption.localTimezone;
			var chartOptions = scope.chartConfig;
			var extraYAxisSet = scope.extraYAxisSet;

			var showEmptyRange = scope.menuOption.showEmptyRange;
			var movedToEmptyRange = false;

			/** 'smallChart' settings:
				height: 150
				no timeline, date range, option menu
				only left-side Y axis
				fewer x-axis tick labels
			*/

			var agYMin, agYMax, yScaleType = 'linear';
			// handle y axis type
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
			var currSeries = series;  // series after being processed
			var seriesBeingDisplayed; //what's being show on the screen

			//---------------- check if it is a small chart (need to be called first) --------------
			var isSmallChart = chartOptions.smallChart === undefined? false: chartOptions.smallChart;

			var dateFormatter = ChartToolService.generateDateFormatter(GMTon, scope.menuOption.dateFormat, isSmallChart);
			var messagesToDisplay = [ChartToolService.defaultEmptyGraphMessage];
			// color scheme
			var z = ChartToolService.setColorScheme(scope.menuOption.colorPalette);
			// determine chart layout and dimensions
			var containerHeight = isSmallChart ? 175 : 330;
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
			var allSize = ChartToolService.calculateDimensions(containerWidth, containerHeight, isSmallChart, scope.menuOption.isBrushOn, extraYAxisSet.size);

			//setup graph variables
			var x, x2, y, y2, yScalePlain, extraY, extraYScalePlain, extraY2,
				xAxis, xAxis2, yAxis, yAxisR, xGrid, yGrid, extraYAxisR,
				graph, graph2, extraGraph, extraGraph2,
				brush, brushMain, zoom,
				svg, svg_g, mainChart, xAxisG, xAxisG2, yAxisG, yAxisRG, xGridG, yGridG, extraYAxisRG,//g
				context, clip, brushG, brushMainG, chartRect,//g
				tooltip, tipBox, tipItems,
				focus, crossLine, mouseOverHighlightBar, highlightBar, mouseMoveElement,
				names, colors, graphClassNames,
				flags, stack;

			var isDataStacked = chartType.includes('stack');
			var isChartDiscrete = chartType.includes('bar');

			// define a few handler functions
			var timestampSelector = ChartToolService.generateTimestampSelector(isDataStacked);
			var dateBisector = isDataStacked ? ChartToolService.bisectDateStackedData : ChartToolService.bisectDate;

			// setup: initialize all the graph variables
			function setUpGraphs() {
				if (isDataStacked) stack = d3.stack();

				var xy = ChartToolService.getXandY(scope.dateConfig, GMTon, allSize, yScaleType, yScaleConfigValue);
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

				// populate brash related items
				var smallBrush = ChartElementService.createBrushElements(scope.dateConfig, GMTon, allSize, isSmallChart, chartType, brushed, yScaleType, yScaleConfigValue);
				xAxis2 = smallBrush.xAxis;
				x2 = smallBrush.x;
				y2 = smallBrush.y;
				graph2 = smallBrush.graph;
				brush = smallBrush.brush;

				brushMain = ChartElementService.createMainBrush(allSize, brushedMain);

				var chartContainerElements = ChartElementService.generateMainChartElements(allSize, container);
				svg = chartContainerElements.svg;
				svg_g = chartContainerElements.svg_g; // clip, flags, brush area,
				mainChart = chartContainerElements.mainChart; // zoom, axis, grid

				zoom = ChartElementService.createZoom(allSize, zoomed, mainChart);

				var axisesElement = ChartElementService.appendAxisElements(allSize, mainChart, chartOptions.xAxis, chartOptions.yAxis);
				xAxisG = axisesElement.xAxisG;
				yAxisG = axisesElement.yAxisG;
				yAxisRG = axisesElement.yAxisRG;

				var gridsElement = ChartElementService.appendGridElements(allSize, mainChart);
				xGridG = gridsElement.xGridG;
				yGridG = gridsElement.yGridG;

				//extra YAxis setup
				if (extraYAxisSet.size > 0) {
					var extraYAxisRelatedElements = ChartElementService.createExtraYAxisRelatedElements(x, x2, extraYAxisSet, allSize, yScaleType, yScaleConfigValue, scope.menuOption.yAxisConfig, mainChart);
					extraY = extraYAxisRelatedElements.extraY;
					extraYScalePlain = extraYAxisRelatedElements.extraYScalePlain;
					extraYAxisR = extraYAxisRelatedElements.extraYAxisR;
					extraYAxisRG = extraYAxisRelatedElements.extraYAxisRG;
					extraGraph = extraYAxisRelatedElements.extraGraph;
					extraY2 = extraYAxisRelatedElements.extraY2;
					extraGraph2 = extraYAxisRelatedElements.extraGraph2;
				}

				//clip path: keep graphs within the container
				clip = ChartElementService.appendClip(allSize, svg_g, chartId);

				//brush area
				var smallBrushElement = ChartElementService.appendBrushWithXAxisElements(allSize, svg_g);
				context = smallBrushElement.context;
				xAxisG2 = smallBrushElement.xAxisG2;

				// flags and annotations
				flags = ChartElementService.appendFlagsElements(svg_g);

				// tooltip setup
				var tooltipElement = ChartElementService.appendTooltipElements(svg_g);
				tooltip = tooltipElement.tooltip;
				tipBox = tooltipElement.tipBox;
				tipItems = tooltipElement.tipItems;
				// set color
				ChartToolService.bindDefaultColorsWithSources(z, names);
				// populate stack if its needed
				if (isDataStacked) {
					stack.order(d3.stackOrderNone)
						.offset(d3.stackOffsetNone);
				}
				// set domain for bandwidth if its a bar chart
				if (chartType ==='bar') {
					graph.x1.domain(graphClassNames);
					graph2.x1.domain(graphClassNames);
				}
			}

			function renderGraphs (series) {
				// downsample if its needed
				currSeries = ChartToolService.downSample(series, containerWidth, scope.menuOption.downSampleMethod);
				// compute stack data if its needed
				if (isDataStacked) {
					stack.keys(currSeries.map(function(metric) { return metric.name; }));
					currSeries = ChartToolService.addStackedDataToSeries(currSeries, stack);
				}
				seriesBeingDisplayed = currSeries;

				var xyDomain = ChartToolService.getXandYDomainsOfSeries(currSeries, isChartDiscrete, isDataStacked, timestampSelector, extraYAxisSet);
				var xDomain = xyDomain.xDomain;
				var yDomain = xyDomain.yDomain;
				var extraYDomain = xyDomain.extraYDomain;

				y.domain(ChartToolService.processYDomain(yDomain, yScalePlain, yScaleType, agYMin, agYMax, isDataStacked, isChartDiscrete));
				for (var iSet of extraYAxisSet){
					extraY[iSet].domain(ChartToolService.processYDomain(extraYDomain[iSet], extraYScalePlain[iSet], yScaleType, undefined, undefined, isDataStacked, isChartDiscrete));
				}

				scope.nonEmptyXDomain = xDomain;
				dateExtent = [scope.dateConfig.startTime, scope.dateConfig.endTime];
				if (!showEmptyRange) { //doing this cause some date range are defined in metric queries and regardless of ag-date
					x.domain(xDomain);
					x2.domain(xDomain);
					dateExtent = xDomain;
				}

				// update brush's x and y
				y2.domain(yDomain);
				for (var iSet of extraYAxisSet){
					extraY2[iSet].domain(ChartToolService.processYDomain(extraYDomain[iSet], extraYScalePlain[iSet], yScaleType, undefined, undefined, isDataStacked, isChartDiscrete));
				}



				// apply the actual x and y domain based on the data for axises and grids
				ChartElementService.redrawAxis(xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, extraYAxisR, extraYAxisRG, extraYAxisSet, isSmallChart);
				ChartElementService.redrawGrid(xGrid, xGridG, yGrid, yGridG);
				xAxisG2.call(xAxis2);

				// minior adjustments based on chart type
				var chartOpacity = chartType.includes('stack')? 0.8: 1;
				if (isChartDiscrete) {
					// update band scale domain from the time scale
					graph.x0.domain(xyDomain.discreteXDomain);
					graph2.x0.domain(xyDomain.discreteXDomain);
					if (chartType === 'bar') {
						graph.x1.rangeRound([0, graph.x0.bandwidth()]);
						graph2.x1.rangeRound([0, graph2.x0.bandwidth()]);
					}
				}

				currSeries.forEach(function (metric, index) {
					var tempColor = metric.color === null ? z(metric.name) : metric.color;
					if (metric.data.length !== 0) {
						var downSampledMetric;
						if (chartType === 'area') chartOpacity = ChartToolService.calculateGradientOpacity(index, currSeries.length);

						var tempGraph, tempGraph2;
						if (!metric.extraYAxis) {
							tempGraph = graph;
							tempGraph2 = graph2;
						} else {
							tempGraph = extraGraph[metric.extraYAxis];
							tempGraph2 = extraGraph2[metric.extraYAxis];
						}

						ChartElementService.renderGraph(mainChart, tempColor, metric, tempGraph, chartId, chartType, chartOpacity);
						// redener brush line in a downsample manner
						downSampledMetric = isChartDiscrete ? metric : ChartToolService.downSampleASingleMetricsDataEveryTenPoints(metric, containerWidth);
						ChartElementService.renderBrushGraph(context, tempColor, downSampledMetric, tempGraph2, chartType, chartOpacity);
						ChartElementService.renderTooltip(tipItems, tempColor, metric.graphClassName);
					}
					// render annotations
					if (metric.flagSeries) {
						var flagSeries = metric.flagSeries.data;
						flagSeries.forEach(function (d) {
							ChartElementService.renderAnnotationsLabels(flags, tempColor, metric.graphClassName, d);
						});
						ChartElementService.bringMouseOverLabelToFront(flags, chartId);
					}
				});
				maxScaleExtent = ChartToolService.setZoomExtent(series, zoom);
				ChartElementService.updateAnnotations(series, scope.sources, x, flags, allSize.height);
			}

			// Add the overlay element to the graph when mouse interaction takes place. This needs to be on top
			function addOverlay() {
				// Mouseover focus and crossline
				if (!isChartDiscrete) {
					focus = ChartElementService.appendFocus(mainChart);
					crossLine = ChartElementService.appendCrossLine(focus);
					currSeries.forEach(function (metric) {
						if (metric.data.length === 0) return;
						var tempColor = metric.color === null ? z(metric.name) : metric.color;
						if (metric.extraYAxis) {
							ChartElementService.renderFocusCircle(focus, tempColor, metric.graphClassName, metric.extraYAxis);
						} else {
							ChartElementService.renderFocusCircle(focus, tempColor, metric.graphClassName, '');
						}
					});
					mouseMoveElement = focus;
				} else {
					mouseOverHighlightBar = ChartElementService.appendMouseOverHighlightBar(mainChart, allSize.height, graph.x0.bandwidth());
					highlightBar = mouseOverHighlightBar.select('.highlightBar');
					mouseMoveElement = mouseOverHighlightBar;
				}
				//the graph rectangle area
				chartRect = ChartElementService.appendChartRect(allSize, mainChart, mouseOverChart, mouseOutChart, mouseMove, zoom);
				// the brush overlay
				brushG = ChartElementService.appendBrushOverlay(context, brush, x.range());
				brushMainG = ChartElementService.appendMainBrushOverlay(mainChart, mouseOverChart, mouseOutChart, mouseMove, zoom, brushMain);
			}

			// mouse interaction on the chart
			function mouseMove(mousePositionData, noSync) {
				if (!seriesBeingDisplayed || seriesBeingDisplayed.length === 0 || movedToEmptyRange) return;
				var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
				if (mousePositionData === undefined) mousePositionData = ChartElementService.getMousePositionData(x, y, d3.mouse(this));
				var dataPoints, newMousePositionData;
				if (isChartDiscrete) {
					// use highlight bar
					if (brushInNonEmptyRange) {
						var distanceToRight = allSize.width + allSize.margin.right;
						dataPoints = ChartElementService.updateHighlightRangeAndObtainDataPoints(graph, mouseOverHighlightBar, tipItems, seriesBeingDisplayed, scope.sources, extraY,
																	mousePositionData, timestampSelector, dateBisector, dateFormatter, isDataStacked, distanceToRight);
						// there is no snap point for bar chart
						newMousePositionData = mousePositionData;
					}
				} else {
					// use focus and crossLine
					var snapPoint;
					if (brushInNonEmptyRange) {
						var dataPointsAndSnapPoint = ChartElementService.updateFocusCirclesAndObtainDataPoints(focus, tipItems, seriesBeingDisplayed, scope.sources, x, y, extraY,
																	mousePositionData, timestampSelector, dateBisector, isDataStacked);
						dataPoints = dataPointsAndSnapPoint.dataPoints;
						snapPoint = dataPointsAndSnapPoint.snapPoint;
					}
					newMousePositionData = snapPoint && scope.menuOption.isSnapCrosslineOn ? snapPoint : mousePositionData;
					ChartElementService.updateCrossLines(allSize, dateFormatter, scope.menuOption.yAxisConfig.formatYaxis, focus, newMousePositionData);
				}
				// sort tooltip items if its needed
				if (scope.menuOption.tooltipConfig.isTooltipSortOn) {
					dataPoints = dataPoints.sort(function (a, b) {
						return b.data[1] - a.data[1];
					});
				}
				ChartElementService.updateTooltipItemsContent(allSize, scope.menuOption, tipItems, tipBox, dataPoints, mousePositionData);

				if (!noSync) {
					// if current chart is snapping mouse position to data point, so will the synced charts
					if (chartId in syncChartJobs) syncChartMouseMoveAll(newMousePositionData.mouseX, chartId);
				}
			}

			// mouse in
			function mouseOverChart(noYCrossLine) {
				var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
				ChartElementService.showFocusAndTooltip(mouseMoveElement, tooltip, scope.menuOption.isTooltipOn, brushInNonEmptyRange);
				if (crossLine) {
					if (noYCrossLine) {
						crossLine.selectAll('.crossLineY').style('display', 'none');
					} else {
						crossLine.selectAll('.crossLineY').style('display', null);
					}
				}
			}

			// mouse out
			function mouseOutChart() {
				ChartElementService.hideFocusAndTooltip(mouseMoveElement, tooltip);
				syncChartMouseOutAll();
			}

			//sync vertical focus line across charts, mouseX is the timestamp
			function syncChartMouseMove(mouseX) {
				if (mouseX < x.domain()[0] || mouseX > x.domain()[1]) {
					// mouseOutChart
					ChartElementService.hideFocusAndTooltip(mouseMoveElement, tooltip);
				} else {
					mouseOverChart(true);
					var mousePositionData = {
						mouseX: mouseX,
						positionX: x(mouseX),
						positionY: mouseMoveElement.select('[name=crossLineTipX]').node().getBBox().height + 3  // crossLineTipPadding
					};
					mouseMove(mousePositionData, true);
				}
			}

			//clear vertical lines and tooltip when move mouse off the focus chart
			function syncChartMouseOut() {
				ChartElementService.hideFocusAndTooltip(mouseMoveElement, tooltip);
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

			// adjust the graph based on small brush
			function brushed () {
				// ignore the case when it is called by the zoomed function
				if (d3.event.sourceEvent && (d3.event.sourceEvent.type === 'zoom' )) return;
				var s = d3.event.selection || x2.range();
				var newDomain = s.map(x2.invert, x2); //invert the x value in brush axis range to the value in domain
				// don't do anything if the domain does not change (initial loading phase)
				if (angular.equals(x.domain(), newDomain)) return;
				x.domain(newDomain); //rescale the domain of x axis
				// update band scale domain if bar chart is plotted
				if (isChartDiscrete) {
					graph.x0.domain(ChartToolService.getSubDiscreteXDomain(graph2.x0.domain(), newDomain));
					highlightBar.attr('width', graph.x0.bandwidth());
					if (chartType === 'bar') graph.x1.rangeRound([0, graph.x0.bandwidth()]);
				}
				//adjust displaying series to the brushed period
				seriesBeingDisplayed = ChartToolService.adjustSeriesBeingDisplayed(currSeries, x, timestampSelector, dateBisector);
				ChartElementService.adjustTooltipItemsBasedOnDisplayingSeries(seriesBeingDisplayed, scope.sources, x, tipItems, timestampSelector);
				scope.updateGraphAndScale();

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

			// adjust the graph based on main brush
			function brushedMain() {
				var selection = d3.event.selection; //the brushMain selection
				if (selection) {
					var start = x.invert(selection[0]);
					var end = x.invert(selection[1]);
					var range = end - start;
					brushMainG.call(brushMain.move, null);
					if (range * maxScaleExtent < x2.domain()[1] - x2.domain()[0]) return;
					brushG.call(brush.move, [x2(start), x2(end)]);
				}
			}

			// adjust the graph based on zooming
			function zoomed() {
				// ignore the case when it is called by the brushed function
				if (d3.event.sourceEvent && (d3.event.sourceEvent.type === 'brush' || d3.event.sourceEvent.type === 'end'))return;
				var t = d3.event.transform;
				// rescale the domain of x axis, invert the x value in brush axis range to the, value in domain
				var tempNewDomain = t.rescaleX(x2).domain();
				x.domain(tempNewDomain);
				// update band scale domain if bar chart is plotted
				if (isChartDiscrete) {
					graph.x0.domain(ChartToolService.getSubDiscreteXDomain(graph2.x0.domain(), tempNewDomain));
					highlightBar.attr('width', graph.x0.bandwidth());
					if (chartType === 'bar') graph.x1.rangeRound([0, graph.x0.bandwidth()]);
				}
				// adjust displaying series to the brushed period
				seriesBeingDisplayed = ChartToolService.adjustSeriesBeingDisplayed(currSeries, x, timestampSelector, dateBisector);
				ChartElementService.adjustTooltipItemsBasedOnDisplayingSeries(seriesBeingDisplayed, scope.sources, x, tipItems, timestampSelector);
				scope.updateGraphAndScale();

				// sync the brush
				context.select('.brush').call(brush.move, x.range().map(t.invertX, t));

				// sync the crossLine
				var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
				if (!isChartDiscrete) {
					var mousePositionData = ChartElementService.getMousePositionData(x, y, d3.mouse(this));
					ChartElementService.updateFocusCirclesPositionWithZoom(x, y, focus, brushInNonEmptyRange, extraY, extraYAxisSet);
					ChartElementService.updateCrossLines(allSize, dateFormatter, scope.menuOption.yAxisConfig.formatYaxis, focus, mousePositionData);
				} else {
					ChartElementService.updateHighlightBarWithZoom(graph, mouseOverHighlightBar, highlightBar, brushInNonEmptyRange);
				}
			}

			//redraw the graph with new series
			function redraw () {
				if (ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent)) {
					if (scope.menuOption.showEmptyRange && movedToEmptyRange) {
						scope.sources.forEach(function(source) {
							if (source.displaying) {
								ChartElementService.toggleGraphDisplay(source, null);
							}
						})
						movedToEmptyRange = false;
					}
					ChartElementService.redrawGraphs(seriesBeingDisplayed, scope.sources, chartType, graph, mainChart, extraGraph);
				} else {
					if (scope.menuOption.showEmptyRange) {
						movedToEmptyRange = true;
						scope.sources.forEach(function(source) {
							if (source.displaying) {
								ChartElementService.toggleGraphDisplay(source, 'none')
							}
						})
					}
				}
				ChartElementService.redrawAxis(xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, extraYAxisR, extraYAxisRG, extraYAxisSet, isSmallChart);
				ChartElementService.redrawGrid(xGrid, xGridG, yGrid, yGridG);
				ChartElementService.updateAnnotations(series, scope.sources, x, flags, allSize.height);
				ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
			}

			//precise resize without removing and recreating everything
			function resize () {
				if (series === 'series' || !series) {
					return;
				}
				var tempSizeInfo = ChartToolService.updateContainerSize(container, defaultContainerHeight, defaultContainerWidth, isSmallChart, scope.menuOption.isBrushOn, scope.changeToFullscreen, extraYAxisSet.size);
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
					needToAdjustHeight && ChartToolService.updateExtraYRange(allSize, extraY, extraYAxisSet);
					ChartElementService.resizeBrush(allSize, brush, brushG, context, x2, xAxis2, xAxisG2, y2, needToAdjustHeight, extraY2, extraYAxisSet);
					ChartElementService.resizeMainBrush(allSize, brushMain, brushMainG);
					ChartElementService.resizeZoom(allSize, zoom);
					ChartElementService.resizeMainChartElements(allSize, svg, svg_g, needToAdjustHeight);
					if (isChartDiscrete) {
						graph.x0.range(x.range());
						graph2.x0.range(x2.range());
						highlightBar.attr('height', allSize.height)
							.attr('width', graph.x0.bandwidth());
						if (chartType === 'bar') {
							graph.x1.rangeRound([0, graph.x0.bandwidth()]);
							graph2.x1.rangeRound([0, graph2.x0.bandwidth()]);
						}
					}
					ChartElementService.resizeGraphs(svg_g, graph, scope.sources, chartType, extraGraph, extraYAxisSet);
					ChartElementService.resizeBrushGraphs(svg_g, graph2, chartType, extraGraph2, extraYAxisSet);

					ChartElementService.resizeAxis(allSize, xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, needToAdjustHeight, mainChart, chartOptions.xAxis, extraYAxisR, extraYAxisRG, extraYAxisSet, isSmallChart);
					ChartElementService.resizeGrid(allSize, xGrid, xGridG, yGrid, yGridG, needToAdjustHeight);

					ChartElementService.updateAnnotations(series, scope.sources, x, flags, allSize.height);
					ChartElementService.bringMouseOverLabelToFront(flags, chartId);

					if (tempX[0].getTime() === x2.domain()[0].getTime() &&
						tempX[1].getTime() === x2.domain()[1].getTime()) {
						ChartElementService.resetBothBrushes(svg_g, [{name: '.brush', brush: brush}, {name: '.brushMain', brush: brushMain}]);
					} else {
						//restore the zoom&brush
						context.select('.brush').call(brush.move, [x2(tempX[0]), x2(tempX[1])]);
					}
					ChartElementService.adjustTooltipItemsBasedOnDisplayingSeries(seriesBeingDisplayed, scope.sources, x, tipItems, timestampSelector);
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

				// turn on wheel zooming for 'smallChart'
				if (isSmallChart) {
					scope.menuOption.isWheelOn = true;
					ChartElementService.toggleWheel(true, zoom, chartRect, brushMainG);
				} else {
					if (!scope.menuOption.isWheelOn) {
						chartRect.on('wheel.zoom', null);   // does not disable 'double-click' to zoom
						brushMainG.on('wheel.zoom', null);
					}
				}

				if (!scope.menuOption.isBrushOn) ChartElementService.toggleElementShowAndHide(false, context);
				if (scope.menuOption.isSyncChart) { addToSyncCharts(); }
				scope.dateRange = ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
			}

			// update series being displayed and redraw the graphs
			scope.updateGraphAndScale = function (hiddenSourceNames) {
				var needToUpdateData = isDataStacked && hiddenSourceNames !== undefined && hiddenSourceNames.length !== names.length;
				if (needToUpdateData) {
					//need to recalculate currSeries since some series are hidden
					currSeries = ChartToolService.downSample(series, containerWidth, scope.menuOption.downSampleMethod);
					currSeries = ChartToolService.addStackedDataToSeries(currSeries, stack, hiddenSourceNames);
					seriesBeingDisplayed = ChartToolService.adjustSeriesBeingDisplayed(currSeries, x, timestampSelector, dateBisector);
				}
				ChartElementService.reScaleYAxis(seriesBeingDisplayed, scope.sources, y, yScalePlain, yScaleType, agYMin, agYMax, isDataStacked, isChartDiscrete, extraY, extraYScalePlain, extraYAxisSet);
				redraw();
			};

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
					} else if (series[i].data.length === 0 || angular.equals(series[i].data, {})) {
						if (!series[i].flagSeries) {
							hasNoData = true;
						} else {
							tempSeries.push(series[i]);
						}
					} else {
						// only keep the metric that's graphable
						tempSeries.push(series[i]);
					}
				}
				series = tempSeries;
				if (series.length > 0) {
					scope.hideMenu = false;
					// render graphs
					setUpGraphs();
					renderGraphs(series);
					addOverlay();
					setupMenu();
					ChartElementService.resetBothBrushes(svg_g, [{name: '.brush', brush: brush}, {name: '.brushMain', brush: brushMain}]);
				} else {
					// generate content for no graph message
					if (invalidExpression) {
						messagesToDisplay.push('Metric does not exist in TSDB');
						for (i = 0; i < scope.invalidSeries.length; i ++) {
							messagesToDisplay.push(scope.invalidSeries[i].errorMessage);
						}
						messagesToDisplay.push('(Failed metrics are black in the legend, unless another color is preset)');
					}
					if (emptyReturn) {
						messagesToDisplay.push('No data returned from TSDB');
						messagesToDisplay.push('(Empty metrics are labeled maroon, unless another color is preset)');
					}
					if (hasNoData) {
						messagesToDisplay.push('No data found for metric expressions');
						messagesToDisplay.push('(Valid sources have normal colors)');
					}
					svg = ChartElementService.appendEmptyGraphMessage(allSize, svg, container, messagesToDisplay);
				}
			}

			//TODO: improve the resize efficiency if performance becomes an issue
			element.on('$destroy', function(){
				if(AllChartIds.length){
					AllChartIds = [];
					ChartToolService.destroySyncChartJobs(scope.dashboardId);//this get cleared too
				}
			});

			AllChartIds.push({
				chartID: chartId
			});

			// watch changes from chart options modal to update graph
			scope.$watch('menuOption.colorPalette', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					ChartElementService.updateColors(newValue, names, colors, graphClassNames, chartType, scope.sources);
				}
			}, true);

			scope.$watch('menuOption.dateFormat', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					dateFormatter = ChartToolService.generateDateFormatter(GMTon, newValue, isSmallChart);
					scope.dateRange = ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
				}
			}, true);

			scope.$watch('menuOption.isBrushMainOn', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					ChartElementService.toggleElementShowAndHide(newValue, brushMainG);
				}
			}, true);

			scope.$watch('menuOption.isWheelOn', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					ChartElementService.toggleWheel(newValue, zoom, chartRect, brushMainG);
				}
			}, true);

			scope.$watch('menuOption.yAxisConfig', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					yAxis.ticks(newValue.numTicksYaxis)
						.tickFormat(d3.format(newValue.formatYaxis));
					yGrid.ticks(newValue.numTicksYaxis);
					yAxisG.call(yAxis);
					yGridG.call(yGrid);

					yAxisR.ticks(newValue.numTicksYaxis)
						.tickFormat(d3.format(newValue.formatYaxis));
					yAxisRG.call(yAxisR);

					for (var iSet of extraYAxisSet) {
						extraYAxisR[iSet].ticks(newValue.numTicksYaxis)
							.tickFormat(d3.format(newValue.formatYaxis));
						extraYAxisRG[iSet].call(extraYAxisR[iSet]);
					}
				}
			}, true);

			scope.$watch('menuOption.isBrushOn', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					ChartElementService.toggleElementShowAndHide(newValue, context);
					resize();
				}
			}, true);

			scope.$watch('menuOption.isTooltipOn', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					ChartElementService.toggleElementShowAndHide(newValue, tooltip);
					if (scope.menuOption.isTooltipOn) mouseOutChart(); // hide the left over tooltip on the chart
				}
			}, true);

			scope.$watch('menuOption.isSyncChart', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					if (newValue) {
						addToSyncCharts();
					} else {
						removeFromSyncCharts();
					}
				}
			}, true);

			scope.$watch('menuOption.downSampleMethod', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					currSeries = ChartToolService.downSample(series, containerWidth, newValue);
					if (isDataStacked) {
						currSeries = ChartToolService.addStackedDataToSeries(currSeries, stack);
					}
					seriesBeingDisplayed = ChartToolService.adjustSeriesBeingDisplayed(currSeries, x, timestampSelector, dateBisector);
					scope.updateGraphAndScale();
				}
			}, true);

			scope.$watch('menuOption.localTimezone', function (newValue, oldValue) {
				if (!scope.hideMenu && newValue !== oldValue) {
					// update x and x2 scale
					if (newValue) {
						GMTon = false;
						x = d3.scaleTime().domain(x.domain()).range(x.range());
						x2 = d3.scaleTime().domain(x2.domain()).range(x2.range());
					} else {
						GMTon = true;
						x = d3.scaleUtc().domain(x.domain()).range(x.range());
						x2 = d3.scaleUtc().domain(x2.domain()).range(x2.range());
					}
					// update axis and grid
					xAxis.scale(x);
					xAxisG.call(xAxis);
					xGrid.scale(x);
					xGridG.call(xGrid);
					xAxis2.scale(x2);
					xAxisG2.call(xAxis2);
					if (isSmallChart) {
						xAxisG.call(xAxis)
							.selectAll("text")
							.attr("y", 5)
							.attr("x", -9)
							.attr("dy", ".35em")
							.attr("transform", "rotate(-65)")
							.style("text-anchor", "end");
					}
					// update main chart and brush graphs
					if (ChartElementService.customizedChartType.includes(chartType)) {
						ChartElementService.updateCustomizedChartTypeGraphX(mainChart, graph, x, chartType);
						ChartElementService.updateCustomizedChartTypeGraphX(context, graph2, x2, chartType);
						for (var iSet of extraYAxisSet) {
							ChartElementService.updateCustomizedChartTypeGraphX(mainChart, extraGraph[iSet], x, chartType);
							ChartElementService.updateCustomizedChartTypeGraphX(context, extraGraph2[iSet], x2, chartType);
						}
					} else {
						ChartElementService.updateGraphsX(graph, x, timestampSelector);
						ChartElementService.updateGraphsX(graph2, x2, timestampSelector);
						for (var iSet of extraYAxisSet) {
							ChartElementService.updateGraphsX(extraGraph[iSet], x, timestampSelector);
							ChartElementService.updateGraphsX(extraGraph2[iSet], x2, timestampSelector);
						}

					}
					// update date formatter
					dateFormatter = ChartToolService.generateDateFormatter(GMTon, scope.menuOption.dateFormat, isSmallChart);
					scope.dateRange = ChartElementService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
				}
			}, true);

			scope.$watch('menuOption.showEmptyRange', function (newValue, oldValue) {
				// TODO: support bar and stackbar chart
				if (newValue !== oldValue) {
					var givenDomain = [scope.dateConfig.startTime, scope.dateConfig.endTime]
					if (!ChartToolService.equalTimeDomain(givenDomain, scope.nonEmptyXDomain) && !isChartDiscrete && !scope.chartConfig.smallChart) {
						if (newValue) {
							x.domain(givenDomain);
							x2.domain(givenDomain);
						} else {
							x.domain(scope.nonEmptyXDomain);
							x2.domain(scope.nonEmptyXDomain);
						}
						redraw();
						xAxisG2.call(xAxis2);
						// redraw brush graph
						ChartElementService.redrawBrushGraphsWithNewXDomain(currSeries, scope.sources, chartType, graph2, context)
						ChartElementService.resetBothBrushes(svg_g, [{name: '.brush', brush: brush}, {name: '.brushMain', brush: brushMain}])
					}
				}
			}, true);

			// smallChart reset brush/zoom`
			scope.resetZoom = function() {
				ChartElementService.resetBothBrushes(svg_g, [{name: '.brush', brush: brush}, {name: '.brushMain', brush: brushMain}]);
			};

			scope.$watch(function(){ return element.width(); }, function () {
				resize();
			});
		}
	};
}]);
