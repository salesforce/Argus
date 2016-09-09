angular.module('argus.controllers.viewMetrics', ['ngResource'])
.controller('ViewMetrics', ['$location', '$routeParams', '$scope', 'growl', 'Metrics', 'Annotations', 'SearchService', 'Controls',
    function ($location, $routeParams, $scope, growl, Metrics, Annotations, SearchService, Controls) {
        
        $scope.expression = $routeParams.expression ? $routeParams.expression : null;
        
        // sub-views: (1) single chart, (2) metric discovery 
        $scope.checkMetricExpression = function() {
            if ($scope.expression) {
                $scope.showMetricDiscovery = false;
                $scope.showChart = true;
                $scope.showLoading = false;
            } else {
                $scope.showMetricDiscovery = true;
                $scope.showChart = false;
                $scope.showLoading = true;
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
            if ($scope.expression !== null && $scope.expression.length) {
                $scope.checkMetricExpression();
                Metrics.query({expression: $scope.expression}, function (data) {
                    $scope.showLoading = true;
                    $scope.updateChart({}, data);
                }, function (error) {
                    $scope.updateChart({}, null);
                    growl.error(error.data.message, {referenceId: 'viewmetrics-error'});
                });
            } else {
                $scope.checkMetricExpression();
                $scope.updateChart({}, $scope.expression);
            }
        };

        $scope.searchMetrics = function(value, category) {

            var defaultParams = {
                namespace: '*',
                scope: '*',
                metric: '*',
                tagk: '*',
                tagv: '*',
                limit: 100,
                page: 1
            };

            var newParams = JSON.parse(JSON.stringify(defaultParams));
            
            // update params with values in $scope if they exist
            newParams['scope'] = ($scope.scope) ? $scope.scope : '*';
            newParams['metric'] = ($scope.metric) ? $scope.metric : '*';
            newParams['namespace'] = ($scope.namespace) ? $scope.namespace : '*';
            newParams['tagk'] = ($scope.tagk) ? $scope.tagk : '*';
            newParams['tagv'] = ($scope.tagv) ? $scope.tagv : '*';

            return SearchService.search(newParams)
                .then(SearchService.processResponses);
        };

        $scope.isSearchMetricDisabled = function () {
            var s = $scope.scope, m = $scope.metric;
            return (s === undefined || s.length < 1) && (m === undefined || m.length < 1);
        };

        // -------------

        $scope.updateChart = function (config, data) {
            var options = config ? angular.copy(config) : {};
            var series = $scope.copySeries(data);
            options.credits = {enabled: false},
            options.rangeSelector = {selected: 1, inputEnabled: false};
            options.xAxis = {
            	type: 'datetime',
            	ordinal: false
            };

            //options.chart={renderTo: 'container',defaultSeriesType: 'line'};
            options.lang = {noData: 'No Data to Display'};
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
            options.series = series;
            options.plotOptions = {
            	series: {
            		animation: false,
            		connectNulls: true
            	},
            	line : {
            		gapSize:1.5
            	}
            };
            
            options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
            
            $('#container').highcharts('StockChart', options);
            
            $scope.series = series;
            $scope.addAlertFlags(data);
        };

        // TODO: move all below scope functions to a puclic Scope service

        $scope.addAlertFlags = function (metrics) {
            if (metrics && metrics.length) {
                for (var i = 0; i < metrics.length; i++) {
                    $scope.addAlertFlag(metrics[i]);
                }
            }
        };

        $scope.addAlertFlag = function(metric) {
            var forName = $scope.createSeriesName(metric);
            Annotations.query({expression: $scope.getAlertFlagExpression(metric)}, function (data) {
                var series = $scope.copyFlagSeries(data);
                var chart = $('#container').highcharts();
                series.linkedTo = forName;
                series.color = chart.get(forName).color;
                chart.addSeries(series);
            });
        };

        $scope.getDatapointRange = function (datapoints) {
            var result = {start: Number.MAX_VALUE, end: Number.MIN_VALUE};
            for (var key in datapoints) {
                if (datapoints.hasOwnProperty(key)) {
                    if (key < result.start) {
                        result.start = key;
                    }
                    if (key > result.end) {
                        result.end = key;
                    }
                }
            }
            return result;
        };

        $scope.getAlertFlagExpression = function (metric) {
            if (metric && metric.datapoints) {
                var range = $scope.getDatapointRange(metric.datapoints);
                var scopeName = metric.scope;
                var metricName = metric.metric;
                var tagData = metric.tags;
                var result = range.start + ":" + range.end + ":" + scopeName + ":" + metricName;
                result += $scope.createTagString(tagData);
                result += ":ALERT";
                return result;
            } else {
                return null;
            }
        };

        $scope.copyFlagSeries = function (data) {
            var result;
            if (data) {
                result = {type: 'flags', shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2};
                result.data = [];
                for (var i = 0; i < data.length; i++) {
                    var flagData = data[i];
                    result.data.push({x: flagData.timestamp, title: 'A', text: $scope.formatFlagText(flagData.fields)});
                }
            } else {
                result = null;
            }
            return result;
        };

        $scope.formatFlagText = function (fields) {
            var result = '';
            if (fields) {
                for (var field in fields) {
                    if (fields.hasOwnProperty(field)) {
                        result += (field + ': ' + fields[field] + '<br/>');
                    }
                }
            }
            return result;
        };

        $scope.copySeries = function (data) {
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
                    var id = $scope.createSeriesName(data[i]);
                    result.push({name: id, id: id, data: series,marker : {enabled : true, radius: 1}});
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        };

        $scope.createSeriesName = function (metric) {
            var scope = metric.scope;
            var name = metric.metric;
            var tags = $scope.createTagString(metric.tags);
            return scope + ':' + name + tags;
        };

        $scope.createTagString = function (tags) {
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
        };

        $scope.getMetricData(null);
    }]);
