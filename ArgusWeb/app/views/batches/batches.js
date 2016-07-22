'use strict';

var argusBatches = angular.module('argusBatches', [
    'ngResource'
]);

argusBatches.controller('BatchExpressionsCtrl', ['$scope', 'AsyncMetrics', 'Batches', 'growl', 'BATCH_CHART_OPTIONS', 
    function($scope, AsyncMetrics, Batches, growl, BATCH_CHART_OPTIONS) {
        $('[data-toggle="tooltip"]').tooltip();
        $scope.hudModes = ['graphs', 'JSON'];
        $scope.hudMode = 0;
        $scope.batches = [];
        $scope.expressions = [{expression: ''}];
        $scope.currTtl = '';
        $scope.currBatchState = 'Refresh for current batch status';
        $scope.currBatchId = '';
        var statusToString = ['queued', 'processing', 'done', 'error'];

        $scope.toggleHudMode = function() {
            if ($scope.hudMode === 0) {
                $scope.hudMode = 1;
            } else {
                $scope.hudMode = 0;
            }
            $scope.refreshBatchState();
        };

        $scope.getBatches = function() {
            $scope.batches = [{id: 'Loading...', status: 'Loading...'}];
            Batches.query().$promise.then(function(batchMap) {
                $scope.batches = [];
                for (var id in batchMap) {
                    if (id.length == 36) {
                        $scope.batches.push({id: id, status: statusToString[batchMap[id]]});
                    }
                }
            });
        };
        $scope.getBatches();

        $scope.addExpression = function() {
            $scope.expressions.push({expression: ''});
        };

        $scope.removeExpression = function() {
            var lastIndex = $scope.expressions.length - 1;
            if (lastIndex >= 0) {
                $scope.expressions.splice($scope.expressions.length - 1);
            }
        };

        $scope.submitBatch = function() {
            var params = {};
            params.expression = [];
            for (var i = 0; i < $scope.expressions.length; i++) {
                if ($scope.expressions[i].expression.length > 0) {
                    params.expression.push($scope.expressions[i].expression);
                }
            }
            params.ttl = $scope.currTtl;
            AsyncMetrics.create(params)
                .then(function success(response) {
                    $scope.expressions = [{expression: ''}];
                    $scope.submitted = true;
                    $scope.currBatchId = response.data.id;
                    $scope.getBatches();
                    $scope.refreshBatchState($scope.currBatchId);
                }, function error(response) {
                    growl.error(response.data.message);
                });
        };

        $scope.refreshBatchState = function(batchId) {
            if (batchId) {
                $scope.currBatchId = batchId;
            }
            if ($scope.currBatchId === '') {
                $scope.currBatchState = 'No batch selected';
                return;
            }
            $scope.currBatchState = 'Loading...';
            if ($scope.hudMode === 0) {
                Batches.query({batchId: $scope.currBatchId}, displayBatchAsJson, displayBatchAsError);
            } else {
                Batches.query({batchId: $scope.currBatchId}, displayBatchAsGraphs, displayBatchAsError);
            }
        };

        // These functions and the below constant may better refactor into an angular service? Somewhat copied from viewmetrics
        function displayBatchAsError() {
            $scope.currBatchState = 'Batch has expired';
            for (var i = 0; i < $scope.batches.length; i++) {
                if ($scope.batches[i].id == $scope.currBatchId) {
                    $scope.batches.splice(i, 1);
                    break;
                } 
            }
        }

        function displayBatchAsJson(batchData) {
            if (batchData.queries) {
                for (var i in batchData.queries) {
                    var query = batchData.queries[i];
                    if (query.result && query.result.datapoints && Object.keys(query.result.datapoints).length > 10) {
                        var firstTen = Object.keys(query.result.datapoints).slice(0, 10);
                        var numDatapointsLeft = Object.keys(query.result.datapoints).length - 10;
                        var truncatedDatapoints = {};
                        for (var j in firstTen) {
                            var time = firstTen[j];
                            truncatedDatapoints[time] = query.result.datapoints[time];
                        }
                        query.result.datapoints = truncatedDatapoints;
                        query.result.datapoints.and = numDatapointsLeft + ' more datapoints';
                    }
                }
            }
            $scope.currBatchState = angular.toJson(batchData, 2);
        }

        function displayBatchAsGraphs(batchData) {
            $('#graphs-container').empty();
            if (batchData.queries) {
                for (var i in batchData.queries) {
                    var metric = batchData.queries[i].result;
                    if (metric) {
                        var series = [];
                        for(var key in metric.datapoints) {
                            var timestamp = parseInt(key);
                            if(metric.datapoints[key]){
                                var value = parseFloat(metric.datapoints[key]);
                                series.push([timestamp, value]);
                            }
                        }
                        var id = createSeriesName(metric);
                        var options = angular.copy(BATCH_CHART_OPTIONS);
                        options.series = [{name: id, id: id, data: series, marker : {enabled : true, radius: 1}}];
                        options.title = {text: batchData.queries[i].expression};

                        var graphDiv = document.createElement('div');
                        $('#graphs-container').append(graphDiv);
                        $(graphDiv).highcharts('StockChart', options).css({"min-width": "310px", "height": "400px", "margin": "24px auto"});
                    }
                }
            }
        }

        function createSeriesName(metric) {
            var scope = metric.scope;
            var name = metric.metric;
            var tags = createTagString(metric.tags);
            return scope + ':' + name + tags;
        }

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
    }]);

argusBatches.constant('BATCH_CHART_OPTIONS', {
    credits: {
        enabled: false
    },
    rangeSelector: {
        selected: 1,
        inputEnabled: false
    },
    xAxis: {
        type: 'datetime',
        ordinal: false
    },
    lang: {
        noData: 'No Data to Display'
    },
    legend: {
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
    },
    plotOptions :{
        series: {
            animation: false,
            connectNulls: true
        }, 
        line : {
            gapSize:1.5
        }
    },
    chart: {
        animation: false,
        borderWidth: 1,
        borderColor: 'lightGray',
        borderRadius: 5
    }
});

argusBatches.factory('AsyncMetrics', ['$http', 'CONFIG', function ($http, CONFIG) {
    return {
        create: function (params) {
            return $http({
                url: CONFIG.wsUrl + 'metrics/batch',
                method: 'GET',
                params: params
            });
        }
    };
}]);

argusBatches.factory('Batches', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'batches/:batchId', {}, {
            query: {method: 'GET', params: {batchId: ''}}
        });
    }]);
