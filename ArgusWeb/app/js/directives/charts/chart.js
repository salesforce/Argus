angular.module('argus.directives.charts.chart', [])
.directive('agChart', ['Metrics', 'ChartRenderingService', 'ChartDataProcessingService', 'ChartOptionService', 'LineChartService', 'CONFIG', 'VIEWELEMENT',
function(Metrics, ChartRenderingService, ChartDataProcessingService, ChartOptionService, LineChartService, CONFIG, VIEWELEMENT) {
    var chartNameIndex = 1;

    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""></div>',
        link: function(scope, element, attributes, dashboardCtrl) {

            // generate a new chart ID, set css options for main chart container
            var newChartId = 'element_' + VIEWELEMENT.chart + chartNameIndex++;
            var smallChart = attributes.smallchart ? true : false;
            var chartType = attributes.type ? attributes.type : 'LINE';
            var cssOpts = ( smallChart ) ? 'smallChart' : '';

            // set the charts container for rendering
            ChartRenderingService.setChartContainer(element, newChartId, cssOpts);

            scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {

                var data = {
                    metrics: scope.metrics,
                    annotations: scope.annotations,
                    options: scope.options
                };

                // process data for: metrics, annotations, options
                var processedData = ChartDataProcessingService.processMetricData(data, event, controls);

                // re-assign each list for: metrics, annotations, options
                var updatedMetricList = processedData.updatedMetricList;
                var updatedAnnotationList = processedData.updatedAnnotationList;
                var updatedOptionList = processedData.updatedOptionList;

                // add options to Highcharts options
                // var highChartOptions = ChartOptionService.getOptionsByChartType(CONFIG, chartType, smallChart);

                // set options to Highcharts
                // ChartOptionService.setCustomOptions(highChartOptions, updatedOptionList);

                // load chart w/Highchart options
                // ChartRenderingService.loadChart(newChartId, highChartOptions);

                // define series first, then build list for each metric expression
                var series = [];
                var metricCount = updatedMetricList.length;

                /* Process Metric List:
                    1. query data for each Metric Expression
                    2. update status indicator if needed
                    3. set options to the series data
                    4. bind series data to charts, display chart
                    5. populate annotations post chart rendering
                **/
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
                            bindDataToChart(newChartId, series, updatedAnnotationList);
                        }

                    }, function (error) {
                        // growl.error(data.message);
                        console.log( 'no data found', data.message );

                        metricCount = metricCount - 1;

                        if (metricCount == 0) {
                            // display chart with series data and populate annotations
                            bindDataToChart(newChartId, series, updatedAnnotationList);
                        }
                    });
                }
            });

            function bindDataToChart(newChartId, series, updatedAnnotationList) {

                // LineChartService

                // empty any previous content
                $("#" + newChartId).empty();

                LineChartService.render(newChartId, series);

                // bind series data to highchart options
                // highChartOptions.series = series;

                // display chart with data
                // ChartRenderingService.displayChart(newChartId, highChartOptions);    // issue: highcharts not rendering from here
                // ChartRenderingService.getChart(newChartId, highChartOptions);

                // populate annotations
                // var chart = ChartRenderingService.getChart(newChartId);
                // ChartDataProcessingService.populateAnnotations(updatedAnnotationList, chart);
            };
        }
    }
}]);
