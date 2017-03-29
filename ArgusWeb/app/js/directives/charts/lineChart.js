'use strict';

angular.module('argus.directives.charts.lineChart', [])
.directive('lineChart', ['$timeout', 'Storage', '$routeParams', 'ChartToolService', 'ChartElementService', function($timeout, Storage, $routeParams, ChartToolService, ChartElementService) {

    //----------------default chart values----------------------

    var dashboardId = $routeParams.dashboardId;

    // date formats: https://github.com/d3/d3-time-format/blob/master/README.md#timeFormat
    var numericalDate = '%-m/%-d/%y %H:%M:%S';
    var smallChartDate = '%x';  // %x = %m/%d/%Y  11/5/2016

    // default formats & settings for chart options
    var rawDataFormat = ',';
    var sampleCustomFormat = '0,.8';     // scientific notation
    var defaultYaxis = '.3s';
    var defaultTicksYaxis = '5';

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
        controller: ['$scope', '$filter', '$uibModal', '$window', 'Metrics', 'DownloadHelper', 'growl', function($scope, $filter, $uibModal, $window, Metrics, DownloadHelper, growl) {
            $scope.hideMenu = true;
            $scope.changeToFullscreen = false;
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
                    chartTitle = "data";
                }
                switch (queryFunction) {
                    case "query":
                        dataHandler = function (data) { return JSON.stringify(data.slice(0, data.length)); };
                        filename = chartTitle + ".json";
                        break;
                    case "downloadCSV":
                        dataHandler = function (data) { return data[0]; };
                        filename = chartTitle + ".csv";
                        break;
                }
                $scope.chartConfig.expressions.map(function (expression) {
                    growl.info("Downloading data...");
                    Metrics[queryFunction]({expression: expression}).$promise.then(function (data) {
                        DownloadHelper.downloadFile(dataHandler(data), filename);
                    }, function (error) {
                        growl.error("Data cannot be download this time");
                        console.log(error);
                    });
                });
            };

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

                        // display current date in 'sample' format
                        var currDate = new Date();
                        var sampleDateFormat = "%-m/%-d/%y %H:%M:%S"; // "Sat Nov 5 1929 11:58"

                        $scope.dateFormatOutput = d3.timeFormat(sampleDateFormat)(currDate);

                        // update date format to show sample date in modal view
                        $scope.updateDateFormatOutput = function() {
                            var userInputDateFormat = $scope.menuOption.dateFormat ? $scope.menuOption.dateFormat : sampleDateFormat;
                            $scope.dateFormatOutput = d3.timeFormat(userInputDateFormat)(new Date());
                        };

                        // display date in correct format when modal opens: either menuOptions OR current date with 'sampleDateFormat'
                        $scope.updateDateFormatOutput();

                        $scope.resetSettings = function () {
                            // set menuOption back to default. can be stored in separate properties file for charts
                            var menuOption = {
                                dateFormat: numericalDate,
                                formatYaxis: defaultYaxis,
                                numTicksYaxis: defaultTicksYaxis,
                                leadingNum: null,
                                trailingNum: null,
                                downSampleMethod: '',
                                isSyncChart: false,
                                isBrushMainOn: false,
                                isWheelOn: false,
                                isBrushOn: false,
                                isTooltipSortOn: true,
                                rawTooltip: true,
                                customTooltipFormat: sampleCustomFormat,
                                colorPallete: 'schemeCategory20',
                                isTooltipOn: true
                            };

                            // update localStorage
                            Storage.set('menuOption_' + dashboardId + '_' + $scope.chartId, menuOption);

                            // update modal view
                            $scope.menuOption = menuOption;
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
                        optionsModal.result.then(function (menuOption) {
                            angular.noop();
                        }, function (menuOption) {
                            $('body').removeClass('lightMask');
                        });
                    }]
                });
            };

            $scope.sources = [];
            $scope.otherSourcesHidden = false;
            // can be used for future modal window
            $scope.noDataSeries = [];
            $scope.invalidSeries = [];

            $scope.toggleSource = function(source) {
                toggleGraphOnOff(source);
            };

            // show ONLY this 1 source, hide all others
            $scope.hideOtherSources = function(sourceToShow) {
                var sources = $scope.sources;
                for (var i = 0; i < sources.length; i++) {
                    if (sourceToShow.name !== sources[i].name) {
                        toggleGraphOnOff(sources[i]);
                    }
                }
                $scope.otherSourcesHidden = !$scope.otherSourcesHidden;
            };

            $scope.labelTextColor = function(source) {
                return source.displaying? source.color: '#FFF';
            };

            function toggleGraphOnOff(source) {
                // d3 select with dot in ID name: http://stackoverflow.com/questions/33502614/d3-how-to-select-element-by-id-when-there-is-a-dot-in-id
                // var graphID = source.name.replace(/\s+/g, '');
                var displayProperty = source.displaying? 'none' : null;
                source.displaying = !source.displaying;
                d3.selectAll("." + source.graphClassName)
                    .style('display', displayProperty)
                    .attr('displayProperty', displayProperty);//this is for recording the display property when circle is outside range
                $scope.reScaleY();
                $scope.redraw();
            }
        }],
        // compile: function (iElement, iAttrs, transclude) {},
        link: function (scope, element, attributes) {
            /**
             * not using chartId because when reload the chart by 'sumbit' button
             * or other single page app navigate button the chartId is not reset
             * to 1, only by refreshing the page would the chartId be reset to 0
             */

            var chartId = scope.chartConfig.chartId;
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

            // provide support for yaxis lower case situation.
            var agYMin, agYMax;
            if(chartOptions.yAxis){
                agYMin = chartOptions.yAxis.min;
                agYMax = chartOptions.yAxis.max;
            }
            if(chartOptions.yaxis){
                agYMin = agYMin || chartOptions.yaxis.min;
                agYMax = agYMax || chartOptions.yaxis.max;
            }
            if (isNaN(agYMin)) agYMin = undefined;
            if (isNaN(agYMax)) agYMax = undefined;

            scope.hideMenu = false;
            scope.dashboardId = dashboardId;

            var menuOption = Storage.get('menuOption_' + scope.dashboardId + '_' + chartId);

            // set scope values, get them from the local storage before setting default
            scope.menuOption = {
                dateFormat: (menuOption && menuOption.dateFormat) ? menuOption.dateFormat : numericalDate,
                formatYaxis: (menuOption && menuOption.formatYaxis) ? menuOption.formatYaxis : defaultYaxis,
                numTicksYaxis: (menuOption && menuOption.numTicksYaxis) ? menuOption.numTicksYaxis : defaultTicksYaxis,

                leadingNum: (menuOption && menuOption.leadingNum) ? menuOption.leadingNum : null,
                trailingNum: (menuOption && menuOption.trailingNum) ? menuOption.trailingNum : null,

                downSampleMethod: (menuOption && menuOption.downSampleMethod) ? menuOption.downSampleMethod : '',
                isSyncChart: menuOption ? menuOption.isSyncChart : false,

                isBrushMainOn: menuOption ? menuOption.isBrushMainOn : false,
                isWheelOn: menuOption ? menuOption.isWheelOn : false,

                // for 'smallChart' only, does not display timeline brush below graph
                isBrushOn: !chartOptions.smallChart,

                isTooltipSortOn: menuOption ? menuOption.isTooltipSortOn : true,
                rawTooltip: menuOption ? menuOption.rawTooltip : true,
                customTooltipFormat: (menuOption && menuOption.customTooltipFormat) ? menuOption.customTooltipFormat : sampleCustomFormat,

                colorPallete: (menuOption && menuOption.colorPallete) ? menuOption.colorPallete : 'schemeCategory20',

                // TODO: refactor code base for no 'isTooltipOn' property
                isTooltipOn: true
            };

            var dateExtent; //extent of non empty data date range
            var topToolbar = $(element); //jquery selection
            var container = topToolbar.parent()[0];//real DOM

            var maxScaleExtent = 100; //zoom in extent
            var currSeries = series;

            //---------------- check if it is a small chart (need to be called first) --------------
            var isSmallChart = chartOptions.smallChart === undefined? false: chartOptions.smallChart;
            //---------------- loading basic information from  ChartToolService----------------------
            var dateFormatter = ChartToolService.generateDateFormatter(GMTon, scope.menuOption.dateFormat, isSmallChart);
            var messagesToDisplay = [ChartToolService.defaultEmptyGraphMessage];
            // color scheme
            var z = ChartToolService.setColorScheme(scope.menuOption.colorPallete);
            //---------------- determine chart layout and dimensions ----------------------
            var containerHeight = isSmallChart ? 150 : 330;
            var containerWidth = $("#" + chartId).width();

            var defaultContainerWidth = -1;
            if (chartOptions.chart !== undefined) {
                containerHeight = chartOptions.chart.height === undefined ? containerHeight: chartOptions.chart.height;
                if (chartOptions.chart.width !== undefined) {
                    containerWidth = chartOptions.chart.width;
                    defaultContainerWidth = containerWidth;
                }
            }
            var defaultContainerHeight = containerHeight;

            var allSize = ChartToolService.calculateDimensions(containerWidth, containerHeight, isSmallChart);
            var width = allSize.width;
            var height = allSize.height;
            var height2 = allSize.height2;
            var margin = allSize.margin;
            var margin2 = allSize.margin2;

            var circleRadius = 4.5;

            //graph setup variables
            var x, x2, y, y2,
                xAxis, xAxis2, yAxis, yAxisR, xGrid, yGrid,
                line, line2,
                brush, brushMain, zoom,
                svg, svg_g, mainChart, xAxisG, xAxisG2, yAxisG, yAxisRG, xGridG, yGridG, //g
                focus, context, clip, brushG, brushMainG, chartRect,//g
                tip, tipBox, tipItems,
                crossLine,
                names, colors, graphClassNames,
                flagsG, labelTip;

            //downsample threshold
            var downsampleThreshold = 1/2;  // datapoints per pixel

            // Base graph setup, initialize all the graph variables
            function setGraph() {
                var xy = ChartToolService.getXandY(scope.dateConfig, allSize, "linear");
                x = xy.x;
                y = xy.y;

                var axises = ChartElementService.createAxisElements(x, y, isSmallChart, scope.menuOption.formatYaxis, scope.menuOption.numTicksYaxis);
                var grids = ChartElementService.createGridElements(x, y, allSize, isSmallChart, scope.menuOption.numTicksYaxis);

                xAxis = axises.xAxis;
                yAxis = axises.yAxis;
                yAxisR = axises.yAxisR;
                xGrid = grids.xGrid;
                yGrid = grids.yGrid;

                line = ChartElementService.createLine(x, y);

                var smallBrush = ChartElementService.createBushElements(scope.dateConfig, allSize, isSmallChart, 'lineChart', brushed);
                xAxis2 = smallBrush.xAxis;
                x2 = smallBrush.x;
                y2 = smallBrush.y;
                line2 = smallBrush.graphs;
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
                tip = tooltipElement.tip;
                tipBox = tooltipElement.tipBox;
                tipItems = tooltipElement.tipItems;
                // get color
                ChartToolService.bindDefaultColorsWithSources(z, names);
            }

            function renderGraphs (series) {
                // downsample if its needed
                if (scope.menuOption.downSampleMethod !== "") {
                    currSeries = ChartToolService.downSample(series, scope.menuOption.downSampleMethod, containerWidth);
                }

                var xyDomain = ChartToolService.getXandYDomainsOfSeries(currSeries);
                var xDomain = xyDomain.xDomain;
                var yDomain = xyDomain.yDomain;

                //startTime/endTime will not be 0
                if(!startTime) startTime = xDomain[0];
                if(!endTime) endTime = xDomain[1];
                //x.domain([startTime, endTime]);
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
                y.domain(mainChartYDomain);

                // update brush's x and y
                x2.domain(xDomain);
                y2.domain(yDomain);

                dateExtent = xDomain;

                currSeries.forEach(function (metric) {
                    if (metric.data.length === 0) return;
                    var tempColor = metric.color === null ? z(metric.name) : metric.color;
                    ChartElementService.renderLineChart(mainChart, tempColor, metric, line, chartId);
                    ChartElementService.renderBrushLineChart(context, tempColor, metric, line2);
                    ChartElementService.renderFocusCircle(focus, tempColor, metric.graphClassName);
                    ChartElementService.renderTooltip(tipItems, tempColor, metric.graphClassName);
                    // annotations
                    if (!metric.flagSeries) return;
                    var flagSeries = metric.flagSeries.data;
                    flagSeries.forEach(function (d) {
                        ChartElementService.renderAnnotationsLabels(flagsG, labelTip, tempColor, metric.graphClassName, d, dateFormatter);
                    })
                });

                ChartToolService.setZoomExtent(series, zoom);
                ChartElementService.updateAnnotations(series, x, flagsG, height, scope.sources);
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
                var tooltipConfig = {
                    rawTooltip: scope.menuOption.rawTooltip,
                    customTooltipFormat: scope.menuOption.customTooltipFormat,
                    leadingNum: scope.menuOption.leadingNum,
                    trailingNum: scope.menuOption.trailingNum,
                    isTooltipSortOn: scope.menuOption.isTooltipSortOn
                };
                var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);

                ChartElementService.updateFocusCirclesToolTipsCrossLines(
                    allSize, dateFormatter, scope.menuOption.formatYaxis, tooltipConfig, focus, tipItems, tipBox,
                    series, scope.sources, x, y, mousePositionData, brushInNonEmptyRange);

                if(chartId in syncChartJobs) syncChartMouseMoveAll(mousePositionData.mouseX, chartId);
            }

            function mouseOverChart(){
                var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
                ChartElementService.showFocusAndTooltip(focus, tip, scope.menuOption.isTooltipOn, brushInNonEmptyRange);
                crossLine.selectAll('.crossLineY').style('display', null);
            }

            function mouseOutChart(){
                // focus.style('display', 'none');
                // if (scope.menuOption.isTooltipOn) tip.style('display', 'none');
                ChartElementService.hideFocusAndTooltip(focus, tip);
                syncChartMouseOutAll();
            }

            ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            //sync vertical focus line across charts, mouseX is the timestamp
            function syncChartMouseMove(mouseX){
                if(mouseX < x.domain()[0] || mouseX > x.domain()[1]){
                    // mouseOutChart
                    ChartElementService.hideFocusAndTooltip(focus, tip);
                } else {
                    // moueOverChart
                    var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
                    ChartElementService.showFocusAndTooltip(focus, tip, scope.menuOption.isTooltipOn, brushInNonEmptyRange);
                    crossLine.selectAll('.crossLineY').style("display", "none");
                    // mouseMove
                    var mousePositionData = {
                        mouseX: mouseX,
                        positionX: x(mouseX),
                        positionY: focus.select('[name=crossLineTipX]').node().getBBox().height + 3  // crossLineTipPadding
                    };
                    var tooltipConfig = {
                        rawTooltip: scope.menuOption.rawTooltip,
                        customTooltipFormat: scope.menuOption.customTooltipFormat,
                        leadingNum: scope.menuOption.leadingNum,
                        trailingNum: scope.menuOption.trailingNum,
                        isTooltipSortOn: scope.menuOption.isTooltipSortOn
                    };
                    ChartElementService.updateFocusCirclesToolTipsCrossLines(
                        allSize, dateFormatter, scope.menuOption.formatYaxis, tooltipConfig, focus, tipItems, tipBox,
                        series, scope.sources, x, y, mousePositionData, brushInNonEmptyRange);
                }
            }

            //clear vertical lines and tooltip when move mouse off the focus chart
            function syncChartMouseOut(){
                ChartElementService.hideFocusAndTooltip(focus, tip);
            }

            //adjust the series when zoom in/out
            function adjustSeries(){
                var domainStart = x.domain()[0].getTime();
                var domainEnd = x.domain()[1].getTime();
                currSeries = JSON.parse(JSON.stringify(series));
                series.forEach(function (metric, index) {
                    var source = scope.sources[index];
                    if (metric === null || metric.data.length === 0){
                        tipItems.selectAll('.' + source.graphClassName).style('display', 'none');
                        return;
                    }
                    var len = metric.data.length;
                    if (metric.data[0][0] > domainEnd || metric.data[len - 1][0] < domainStart){
                        currSeries[index].data = [];
                        tipItems.selectAll('.'+ source.graphClassName).style('display', 'none');
                        return;
                    }
                    tipItems.selectAll("." + source.graphClassName).style('display', source.displaying? null: 'none');
                    //if this metric time range is within the x domain
                    var start = ChartToolService.bisectDate(metric.data, x.domain()[0]);
                    if(start > 0) start-=1; //to avoid cut off issue on the edge
                    var end = ChartToolService.bisectDate(metric.data, x.domain()[1], start) + 1; //to avoid cut off issue on the edge
                    currSeries[index].data = metric.data.slice(start, end + 1);
                });
                currSeries = ChartToolService.downSample(currSeries, scope.menuOption.downSampleMethod, containerWidth);
            }

            //redraw the line with restrict
            function redraw(){
                var domainStart = x.domain()[0].getTime();
                var domainEnd = x.domain()[1].getTime();
                //redraw
                if(ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent)) {
                    //update the dataum and redraw the line
                    currSeries.forEach(function (metric, index) {
                        if (metric === null || //empty data should also render
                            !scope.sources[index].displaying) return; //hided
                        //the commented part are done in adjustSeries
                        // var len = metric.data.length;
                        // if (metric.data[0][0] > domainEnd || metric.data[len - 1][0] < domainStart){
                        //     mainChart.select('path.line.' + metric.graphClassName)
                        //         .datum([])
                        //         .attr('d', line);
                        //     return;
                        // }
                        //if this metric time range is within the x domain
                        // var start = bisectDate(metric.data, x.domain()[0]);
                        // if(start > 0) start-=1; //to avoid cut off issue on the edge
                        // var end = bisectDate(metric.data, x.domain()[1], start) + 1; //to avoid cut off issue on the edge
                        // var data = metric.data.slice(start, end + 1);

                        //only render the data within the domain
                        mainChart.select('path.line.' + metric.graphClassName)
                            .datum(metric.data)
                            .attr('d', line); //change the datum will call d3 to redraw
                    });
                    //svg_g.selectAll(".line").attr("d", line);//redraw the line
                }
                xAxisG.call(xAxis);  //redraw xAxis
                yAxisG.call(yAxis);  //redraw yAxis
                yAxisRG.call(yAxisR); //redraw yAxis right
                xGridG.call(xGrid);
                yGridG.call(yGrid);
                if (!scope.menuOption.isBrushOn) {
                    context.style("display", "none");
                }
                scope.dateRange = ChartToolService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
                ChartElementService.updateAnnotations(series, x, flagsG, height, scope.sources);
            }

            scope.redraw = redraw; //have to register this as scope function cause toggleGraphOnOff is outside link function

            //brushed
            function brushed() {
                // ignore the case when it is called by the zoomed function
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "zoom" )) return;
                var s = d3.event.selection || x2.range();
                x.domain(s.map(x2.invert, x2));     //rescale the domain of x axis
                                                    //invert the x value in brush axis range to the
                                                    //value in domain

                //ajust currSeries to the brushed period
                adjustSeries();

                reScaleY(); //rescale domain of y axis
                //redraw
                redraw();
                //sync with zoom
                chartRect.call(zoom.transform, d3.zoomIdentity
                    .scale(width / (s[1] - s[0]))
                    .translate(-s[0], 0));

                if (brushMainG) {
                    brushMainG.call(zoom.transform, d3.zoomIdentity
                        .scale(width / (s[1] - s[0]))
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
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "brush" || d3.event.sourceEvent.type === "end"))return;
                var t = d3.event.transform;
                x.domain(t.rescaleX(x2).domain());  //rescale the domain of x axis
                                                    //invert the x value in brush axis range to the
                                                    //value in domain
                //ajust currSeries to the brushed period
                adjustSeries();

                reScaleY(); //rescale domain of y axis
                //redraw
                redraw();
                // sync the brush
                context.select(".brush").call
                (brush.move, x.range().map(t.invertX, t));

                // sync the crossLine
                var mousePositionData = ChartElementService.getMousePositionData(x, y, d3.mouse(this));
                var brushInNonEmptyRange = ChartToolService.isBrushInNonEmptyRange(x.domain(), dateExtent);
                ChartElementService.updateFocusCirclesPositionWithZoom(x, y, focus, brushInNonEmptyRange);
                ChartElementService.updateCrossLines(allSize, dateFormatter, scope.menuOption.formatYaxis, focus, mousePositionData);
            }

            //change brush focus range, k is the number of minutes
            function brushMinute(k) {
                return function () {
                    if (!k) k = (x2.domain()[1] - x2.domain()[0]);
                    //the unit of time value is millisecond
                    //x2.domain is the domain of total
                    var interval = k * 60000; //one minute is 60000 millisecond

                    //take current x domain value and extend it
                    var start = x.domain()[0].getTime();
                    var end = x.domain()[1].getTime();
                    var middle = (start + end) / 2;
                    start = middle - interval / 2;
                    var min = x2.domain()[0].getTime();
                    var max = x2.domain()[1].getTime();
                    if (start < min) start = min;
                    end = start + interval;
                    if (end > max) end = max;
                    x2.domain([start, end]);
                    context.select(".brush").call
                    (brush.move, [x2(new Date(start)), x2(new Date(end))]);
                };
            }

            //rescale YAxis based on XAxis Domain
            function reScaleY() {
                var bufferRatio = 0.2; //the ratio of buffer above/below max/min on yAxis for better showing experience

                if (currSeries === "series" || !currSeries) return;
                if(agYMin !== undefined && agYMax !== undefined) return; //hard coded ymin & ymax

                var xDomain = x.domain();
                var datapoints = [];

                currSeries.forEach(function (metric, index) {
                    if (metric === null || metric.data.length === 0 || //empty
                        !scope.sources[index].displaying) return; //hided

                    var len = metric.data.length;
                    if (metric.data[0][0] > xDomain[1].getTime() || metric.data[len - 1][0] < xDomain[0].getTime()) return;
                    //if this metric time range is within the xDomain
                    var start = ChartToolService.bisectDate(metric.data, xDomain[0]);
                    var end = ChartToolService.bisectDate(metric.data, xDomain[1], start);
                    datapoints = datapoints.concat(metric.data.slice(start, end + 1));
                });

                var extent = d3.extent(datapoints, function (d) {
                    return d[1];
                });
                var diff = extent[1] - extent[0];
                if (diff === 0) diff = ChartToolService.yAxisPadding;
                var buffer = diff * bufferRatio;
                var yMin = (agYMin === undefined) ? extent[0] - buffer : agYMin;
                var yMax = (agYMax === undefined) ? extent[1] + 3 * buffer : agYMax;

                y.domain([yMin, yMax]);
            }
            scope.reScaleY = reScaleY; //have to register this as scope function cause toggleGraphOnOff is outside link function

            //precise resize without removing and recreating everything
            function resize(){
                if (series === "series" || !series) {
                    return;
                }
                if ((window.innerHeight === screen.height || container.offsetHeight === window.innerHeight) && scope.changeToFullscreen) {
                    // set the graph size to be the same as the screen
                    containerWidth = screen.width;
                    containerHeight = screen.height * 0.9;
                } else {
                    // default containerHeight will be used
                    containerHeight = defaultContainerHeight;
                    // no width defined via chart option: window width will be used
                    containerWidth = defaultContainerWidth < 0 ? container.offsetWidth : defaultContainerWidth;
                }

                var newSize = ChartToolService.calculateDimensions(containerWidth, containerHeight, isSmallChart);
                width = newSize.width;
                height = newSize.height;
                height2 = newSize.height2;
                margin = newSize.margin;
                margin2 = newSize.margin2;

                if (width < 0) return; //it happens when click other tabs (like 'edit'/'history', the charts are not destroyed

                if (series.length > 0) {
                    var tempX = x.domain(); //remember that when resize

                    clip.attr('width', width)
                        .attr('height', height);
                    chartRect.attr('width', width);

                    if (fullscreenChartID !== undefined) {
                        // only adjust height related items when full screen chart is used
                        chartRect.attr('height', height);
                        y.range([height, 0]);
                        y2.range([height2, 0]);
                        svg.attr('height', height + margin.top + margin.bottom);
                        svg_g.attr('height', height);
                        xGrid.tickSizeInner(-height);
                        xGridG.attr('transform', 'translate(0,' + height + ')');
                        xAxisG.attr('transform', 'translate(0,' + height + ')');
                        // reposition the brush
                        context.attr("transform", "translate(0," + margin2.top + ")");
                        xAxisG2.attr("transform", "translate(0," + height2 + ")");
                    }

                    //update range
                    x.range([0, width]);
                    x2.range([0, width]);

                    //update brush & zoom
                    brush.extent([
                        [0, 0],
                        [width, height2]
                    ]);
                    brushMain.extent([
                        [0, 0],
                        [width, height]
                    ]);
                    zoom.translateExtent([
                            [0, 0],
                            [width, height]
                        ])
                        .extent([
                            [0, 0],
                            [width, height]
                        ]);
                    brushG.call(brush);
                    brushMainG.call(brushMain);

                    //width related svg element
                    svg.attr('width', width + margin.left + margin.right);
                    svg_g.attr('width', width)
                        .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

                    yGrid.tickSizeInner(-width);
                    yGridG.call(yGrid);

                    yAxisRG.attr('transform', 'translate(' + width + ')')
                        .call(yAxisR);

                    svg_g.selectAll(".line").attr("d", line); //redraw the line
                    svg_g.selectAll(".brushLine").attr("d", line2); //redraw brush line

                    xAxisG.call(xAxis); //redraw xAxis
                    yAxisG.call(yAxis); //redraw yAxis
                    xGridG.call(xGrid);
                    xAxisG2.call(xAxis2);

                    // update x axis label if it's in ag options
                    if (chartOptions.xAxis!== undefined && chartOptions.xAxis.title !== undefined) {
                        mainChart.select(".xAxisLabel")
                                  .attr("transform", "translate(" + (width / 2) + " ," + (height + margin.top + ChartToolService.xAxisLabelHeightFactor) + ")");
                    }

                    if (tempX[0].getTime() === x2.domain()[0].getTime() &&
                        tempX[1].getTime() === x2.domain()[1].getTime()) {
                        ChartElementService.resetBothBrushes(svg_g, [".brush", ".brushMain"], brush);
                    } else {
                        //restore the zoom&brush
                        context.select(".brush").call(brush.move, [x2(tempX[0]), x2(tempX[1])]);
                    }
                    adjustSeries();
                } else {
                    svg = ChartElementService.appendEmptyGraphMessage(allSize, svg, container, messagesToDisplay);
                }
            }

            //toggle time brush
            function toggleBrush() {
                var display = !scope.menuOption.isBrushOn ? 'none' : null;
                svg_g.select('.context').style('display', display);

                updateStorage();
            }

            //toggle time brush
            function toggleBrushMain() {
                //enable main chart brush
                if (scope.menuOption.isBrushMainOn) {
                    brushMainG.style('display', null);
                } else {
                    //disable main chart brush
                    brushMainG.style('display', 'none');
                }
                updateStorage();
            }

            //toggle the mouse wheel for zoom
            function toggleWheel() {
                if (scope.menuOption.isWheelOn) {
                    chartRect.call(zoom);
                    brushMainG.call(zoom)
                        .on("mousedown.zoom", null);
                } else {
                    chartRect.on("wheel.zoom", null);
                    brushMainG.on("wheel.zoom", null);
                }
                updateStorage();
            }

            //toggle tooltip
            function toggleTooltip() {
                if (scope.menuOption.isTooltipOn) {
                    svg_g.select(".tooltip").style("display", 'none');
                } else {
                    svg_g.select(".tooltip").style("display", null);
                }
                updateStorage();
            }

            function setupMenu(){
                // TODO: this is broken after click one of the options; brush is not set correctly
                //button set up

                //dynamically enable button for brush time period(1h/1d/1w/1m/1y)
                var range = dateExtent[1] - dateExtent[0];
                if (range > 3600000) {
                    //enable 1h button
                    $('[name=oneHour]', topToolbar).prop('disabled', false).click(brushMinute(60));
                }
                if (range > 3600000 * 24) {
                    //enable 1d button
                    $('[name=oneDay]', topToolbar).prop('disabled', false).click(brushMinute(60*24));
                }
                if (range > 3600000 * 24 * 7) {
                    //enable 1w button
                    $('[name=oneWeek]', topToolbar).prop('disabled', false).click(brushMinute(60*24*7));
                }
                if (range > 3600000 * 24 * 30) {
                    //enable 1month button
                    $('[name=oneMonth]', topToolbar).prop('disabled', false).click(brushMinute(60*24*30));
                }
                if (range > 3600000 * 24 * 365) {
                    //enable 1y button
                    $('[name=oneYear]', topToolbar).prop('disabled', false).click(brushMinute(60*24*365));
                }

                if (scope.menuOption.isBrushMainOn) {
                    brushMainG.style('display', null);
                } else {
                    brushMainG.style('display', 'none');
                }
                // no wheel zoom on page load
                if (!scope.menuOption.isWheelOn) {
                    chartRect.on("wheel.zoom", null);   // does not disable 'double-click' to zoom
                    brushMainG.on("wheel.zoom", null);
                }
            }

            scope.updateStorage = updateStorage;
            function updateStorage(){
                Storage.set('menuOption_' + scope.dashboardId + '_' + chartId, scope.menuOption);
            }

            scope.updateDownSample = function(){
                adjustSeries();
                reScaleY();
                redraw();
                updateStorage();
            };

            // dont know how to refactor these....

            ///////////////////////////////////////////////////////////////////////////////////////////////

            // create graph only when there is data

            if (!series || series.length === 0) {
                //this should never happen
                console.log("Empty data from chart data processing");
            } else {
                // set up legend
                names = series.map(function(metric) { return metric.name; });
                colors = series.map(function(metric) { return metric.color; });
                graphClassNames = series.map(function(metric) { return metric.graphClassName; });
                scope.sources = ChartToolService.createSourceListForLegend(names, colors, graphClassNames, z);
                // check if there is anything to graph
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
                    setGraph();
                    renderGraphs(series);
                    addOverlay();

                    // dont need to setup everything for a small chart
                    scope.dateRange = ChartToolService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
                    ChartElementService.resetBothBrushes(svg_g, [".brush", ".brushMain"], brush); //to remove the brush cover first for user the drag
                    setupMenu();
                } else {
                    // generate content for no graph message
                    if (invalidExpression) {
                        messagesToDisplay.push('Metric does not exist in TSDB');
                        for (var i = 0; i < scope.invalidSeries.length; i ++) {
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

            function addToSyncCharts(){
                syncChartJobs[chartId] = {
                    syncChartMouseMove: syncChartMouseMove,
                    syncChartMouseOut: syncChartMouseOut
                };
            }

            function removeFromSyncCharts(){
                delete syncChartJobs[chartId];
            }

            scope.toggleSyncChart = function(){
                if (scope.menuOption.isSyncChart){
                    addToSyncCharts();
                }else{
                    removeFromSyncCharts();
                }
                updateStorage();
            };

            if(scope.menuOption.isSyncChart){
                addToSyncCharts();
            }

            // watch changes from chart options modal to update graph
            scope.$watch('menuOption', function() {
                if (!scope.hideMenu) {
                    ChartToolService.setColorScheme(scope.menuOption.colorPallete);
                    dateFormatter = ChartToolService.generateDateFormatter(GMTon, scope.menuOption.dateFormat, isSmallChart);
                    scope.dateRange = ChartToolService.updateDateRangeLabel(dateFormatter, GMTon, chartId, x);
                    toggleBrushMain();
                    toggleWheel();
                    toggleTooltip();
                    scope.updateDownSample();
                    scope.toggleSyncChart();

                    // update any changes for the Y-axis tick formatting & number of ticks displayed
                    yAxis = d3.axisLeft()
                        .scale(y)
                        .ticks(scope.menuOption.numTicksYaxis)
                        .tickFormat(d3.format(scope.menuOption.formatYaxis))
                    ;

                    yGrid = d3.axisLeft()
                        .scale(y)
                        .ticks(scope.menuOption.numTicksYaxis)
                        .tickSizeInner(-width)
                    ;

                    yAxisG.call(yAxis);
                    yGridG.call(yGrid);

                    // mouseMove();
                }
            }, true);
        }
    };
}]);
