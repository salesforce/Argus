angular.module('argus.directives.charts.chart', [])
.directive('agChart', ['Metrics', 'ChartRenderingService', 'ChartDataProcessingService', 'ChartOptionService', 'CONFIG', 'VIEWELEMENT', '$compile',
function(Metrics, ChartRenderingService, ChartDataProcessingService, ChartOptionService, CONFIG, VIEWELEMENT, $compile) {
    var chartNameIndex = 1;

    function renderLineChart(scope, newChartId, series, updatedAnnotationList, startTime, endTime) {
        // empty any previous content
        $("#" + newChartId).empty();

        // create a new scope to pass to compiled line-chart directive
        var lineChartScope = scope.$new(false);     // true will set isolate scope, false = inherit

        // assign chartId, series data, time domain to new $scope
        lineChartScope.chartId = newChartId;
        lineChartScope.series = series;
        lineChartScope.startTime = startTime;
        lineChartScope.endTime = endTime;
        debugger;
        // append, compile, & attach new scope to line-chart directive
        angular.element("#" + newChartId).append( $compile('<line-chart chartid="chartId" series="series" starttime="startTime" endtime="endTime"></line-chart>')(lineChartScope) );
    }

    // TODO: below functions 'should' be refactored to the chart services.
    function setupChart(scope, element, attributes, controls) {
        // remove/clear any previous chart rendering from DOM
        element.empty();
        // generate a new chart ID, set css options for main chart container
        var newChartId = 'element_' + VIEWELEMENT.chart + chartNameIndex++;
        var smallChart = attributes.smallchart ? true : false;
        var chartType = attributes.type ? attributes.type : 'LINE';
        var cssOpts = ( smallChart ) ? 'smallChart' : '';

        // set the charts container for rendering
        ChartRenderingService.setChartContainer(element, newChartId, cssOpts);

        var data = {
            metrics: scope.metrics,
            annotations: scope.annotations,
            options: scope.options
        };

        // get start and end time for the charts
        var startTime, endTime;
        for (var i = 0; i < controls.length; i++) {
            if (controls[i].type === "agDate") {
                var timeValue = controls[i].value;
                if (controls[i].name === "start") {
                    startTime = timeProcessingHelper(timeValue);
                } else if (controls[i].name === "end"){
                    endTime = timeProcessingHelper(timeValue);
                }
            }
        }

        function timeProcessingHelper(timeValue) {
            var result;
            if (timeValue[0] === '-') {
                timeValue = timeValue.toLowerCase().trim();
                // apply offset to current time
                var offsetValue = parseInt(timeValue.substring(1, timeValue.length - 1));
                var offsetUnit = timeValue[timeValue.length - 1];
                result = new Date();
                switch (offsetUnit) {
                    case "s":
                        result = result.setSeconds(result.getSeconds() - offsetValue);
                        break;
                    case "m":
                        result = result.setMinutes(result.getMinutes() - offsetValue);
                        break;
                    case "h":
                        result = result.setHours(result.getHours() - offsetValue);
                        break;
                    case "d":
                        result = result.setDate(result.getDate() - offsetValue);
                        break;
                }
                return new Date(result);
            } else {
                result = new Date(timeValue);
                return result.toString() === 'Invalid Date' ? new Date() : result;
            }
        }

        // process data for: metrics, annotations, options
        var processedData = ChartDataProcessingService.processMetricData(data, event, controls);

        // re-assign each list for: metrics, annotations, options
        var updatedMetricList = processedData.updatedMetricList;
        var updatedAnnotationList = processedData.updatedAnnotationList;
        var updatedOptionList = processedData.updatedOptionList;

        // define series first, then build list for each metric expression
        var series = [];
        var metricCount = updatedMetricList.length;

        for (var i=0; i < updatedMetricList.length; i++) {
            var metricItem = updatedMetricList[i];

            // make api call to get data for each metric item
            Metrics.query({expression: metricItem.expression}, function (data) {
                if (data && data.length > 0) {

                    // check to update statusIndicator with correct status color
                    if (smallChart) {
                        // get the last data point
                        var lastStatusVal = ChartDataProcessingService.getLastDataPoint(data[0].datapoints);

                        // update status indicator
                        ChartRenderingService.updateIndicatorStatus(attributes, lastStatusVal);
                    }

                    // metric item attributes are assigned to the data (i.e. name, color, etc.)
                    var seriesWithOptions = ChartDataProcessingService.copySeriesDataNSetOptions(data, metricItem);

                    // add each metric item & data to series list
                    Array.prototype.push.apply(series, seriesWithOptions);

                } else {
                    console.log( 'No data found for the metric expression: ', JSON.stringify(metricItem.expression) );
                    // growl.info('No data found for the metric expression: ' + JSON.stringify(metricItem.expression));
                }

                // decrement metric count each time an expression is added to the series.
                metricCount = metricCount - 1;

                if (metricCount == 0) {
                    // display chart with series data and populate annotations
                    // bindDataToChart(newChartId, series, updatedAnnotationList);

                    renderLineChart(scope, newChartId, series, updatedAnnotationList, startTime, endTime);
                }

            }, function (error) {
                // growl.error(data.message);
                console.log( 'no data found', data.message );

                metricCount = metricCount - 1;

                if (metricCount == 0) {
                    // display chart with series data and populate annotations
                    // bindDataToChart(newChartId, series, updatedAnnotationList);

                    renderLineChart(scope, newChartId, series, updatedAnnotationList, startTime, endTime);
                }
            });
        }
    }

    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""></div>',
        compile: function (element, attrs, transclude) {
            return {
                post: function postLink(scope, element, attributes, dashboardCtrl, transcludeFn) {
                    scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
                        setupChart(scope, element, attributes, controls);
                    });
                }
            };
        }
    }
}]);
