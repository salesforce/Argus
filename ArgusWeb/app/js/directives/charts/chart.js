'use strict';

angular.module('argus.directives.charts.chart', [])
.directive('agChart', ['Metrics', 'Annotations', 'ChartRenderingService', 'ChartDataProcessingService', 'ChartOptionService', 'DateHandlerService', 'CONFIG', 'VIEWELEMENT', '$compile',
function(Metrics, Annotations, ChartRenderingService, ChartDataProcessingService, ChartOptionService, DateHandlerService, CONFIG, VIEWELEMENT, $compile) {
    var chartNameIndex = 1;
    function compileLineChart(scope, newChartId, series, dateConfig, updatedOptionList) {
        // empty any previous content
        $("#" + newChartId).empty();

        // create a new scope to pass to compiled line-chart directive
        var lineChartScope = scope.$new(false);     // true will set isolate scope, false = inherit

        // add more options in addition to 'ag-options'
        lineChartScope.chartConfig = updatedOptionList;
        lineChartScope.chartConfig.chartId = newChartId;
        lineChartScope.chartConfig.smallChart = scope.chartOptions ? scope.chartOptions.smallChart : undefined;

        lineChartScope.series = series;
        // when there is no agDate
        if (dateConfig.startTime == undefined || dateConfig.endTime == undefined) {
            if (series[0].data && series[0].data.length > 0) {
                dateConfig.startTime = DateHandlerService.getStartTimestamp(series);
                dateConfig.endTime = DateHandlerService.getEndTimestamp(series);
            }
        }
        lineChartScope.dateConfig = dateConfig;

        // give each series an unique ID if it has data
        for (var i = 0; i < series.length; i++) {
            // use graphClassName to bind all the graph element of a metric together
            lineChartScope.series[i].graphClassName = newChartId + "_graph" + (i + 1);
        }
        // sort series alphabetically
        lineChartScope.series = lineChartScope.series.sort(function(a, b) {
            var textA = a.name.toUpperCase();
            var textB = b.name.toUpperCase();
            return (textA < textB) ? -1 : (textA > textB) ? 1 : 0;
        });

        scope.seriesDataLoaded = true;

        // append, compile, & attach new scope to line-chart directive
        angular.element("#" + newChartId).append( $compile('<line-chart chartConfig="chartConfig" series="series" dateconfig="dateConfig"></line-chart>')(lineChartScope) );
    }

    function queryAnnotationData(scope, annotationItem, newChartId, series, dateConfig, updatedOptionList) {
        Annotations.query({expression: annotationItem}).$promise.then(function(data) {
            if (data && data.length > 0) {
                var forName = ChartDataProcessingService.createSeriesName(data[0]);
                var flagSeries = ChartDataProcessingService.copyFlagSeries(data);
                flagSeries.linkedTo = forName;

                // add flagSeries if any data exists
                series[0].flagSeries = (flagSeries) ? flagSeries: null;
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
            for (var i=0; i < updatedAnnotationList.length; i++) {
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
                    var lastStatusVal = ChartDataProcessingService.getLastDataPoint(data[0].datapoints);

                    // update status indicator
                    ChartRenderingService.updateIndicatorStatus(attributes, lastStatusVal);

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
                    name: JSON.stringify(metricItem.expression).slice(1, -1),
                    color: 'Maroon'
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
            console.log('Metric expression does not exist in database');
            var tempSeries = [{
                invalidMetric: true,
                errorMessage: error.statusText + '(' + error.status + ') - ' + error.data.message.substring(0, 31),
                name: error.config.params.expression,
                color: 'Black'
            }];
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
        var lastEl = element.context.querySelector('[id^=element_chart]');
        var lastId = lastEl? lastEl.id: null;
        element.empty();
        // generate a new chart ID, set css options for main chart container
        var newChartId = lastId || 'element_' + VIEWELEMENT.chart + chartNameIndex++;
        var chartType = attributes.type ? attributes.type : 'LINE';
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
        var GMTon = false;
        for (var i = 0; i < controls.length; i++) {
            if (controls[i].type === "agDate") {
                var timeValue = controls[i].value;
                if (controls[i].name === "start") {
                    dateConfig.startTime = DateHandlerService.timeProcessingHelper(timeValue);
                    GMTon = GMTon || DateHandlerService.GMTVerifier(timeValue);
                } else if (controls[i].name === "end"){
                    dateConfig.endTime = DateHandlerService.timeProcessingHelper(timeValue);
                    GMTon = GMTon || DateHandlerService.GMTVerifier(timeValue);
                }
            }
        }
        dateConfig.gmt = GMTon;

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

        // define series first, then build list for each metric expression
        var series = [];
        var metricCount = {};
        metricCount.tot = updatedMetricList.length;
        metricCount.expressions = [];

        scope.seriesDataLoaded = false; //used for load spinner
        angular.element("#" + newChartId).append( $compile('<div ng-loading="seriesDataLoaded"></div>')(scope) );

        for (var i = 0; i < updatedMetricList.length; i++) {
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
        compile: function (element, attrs, transclude) {
            return {
                post: function postLink(scope, element, attributes, dashboardCtrl, transcludeFn) {
                    scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
                        setupChart(scope, element, attributes, controls);
                    });
                }
            };
        }
    };
}]);