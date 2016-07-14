'use strict';

var argusBatches = angular.module('argusBatches', [
    'ngResource'
]);

argusBatches.controller('BatchExpressionsCtrl', ['$scope', 'AsyncMetrics', 'Batches',
    function($scope, AsyncMetrics, Batches) {
        $('[data-toggle="tooltip"]').tooltip();
        $scope.batches = [];
        $scope.expressions = [{expression: ''}];
        $scope.currTtl = '';
        $scope.currBatchState = 'Refresh for current batch status';
        $scope.currBatchId = '';
        var statusToString = ['queued', 'processing', 'done', 'error'];

        $scope.getBatches = function(is) {
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
                    console.log('Error in batches.submitBatch:\n' + response);
                });
        };

        $scope.refreshBatchState = function(batchId) {
            if (batchId != null) {
                $scope.currBatchId = batchId;
            }
            if ($scope.currBatchId == '') {
                $scope.currBatchState = 'No batch selected';
                return;
            }
            $scope.currBatchState = 'Loading...';
            Batches.query({batchId: $scope.currBatchId}, function(data) {
                if (data.queries) {
                    for (var i in data.queries) {
                        var query = data.queries[i];
                        if (query.result && query.result.datapoints && Object.keys(query.result.datapoints).length > 10) {
                            var firstTen = Object.keys(query.result.datapoints).slice(0, 10);
                            var numDatapointsLeft = Object.keys(query.result.datapoints).length - 10;
                            var truncatedDatapoints = {};
                            for (var j in firstTen) {
                                var time = firstTen[j];
                                truncatedDatapoints[time] = query.result.datapoints[time];
                            }
                            query.result.datapoints = truncatedDatapoints;
                            query.result.datapoints['and'] = numDatapointsLeft + ' more datapoints';
                        }
                    }
                }
                $scope.currBatchState = angular.toJson(data, 2);
            }, function() {
                $scope.currBatchState = 'Batch has expired';
                for (var i = 0; i < $scope.batches.length; i++) {
                    if ($scope.batches[i].id == $scope.currBatchId) {
                        $scope.batches.splice(i, 1);
                        break;
                    }
                }
            });
        }
    }]);

argusViewMetrics.factory('AsyncMetrics', ['$http', 'CONFIG', function ($http, CONFIG) {
    return {
        create: function (params) {
            return $http({
                url: CONFIG.wsUrl + 'metrics/batch',
                method: 'GET',
                params: params
            });
        }
    }
}]);

argusViewMetrics.factory('Batches', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'batches/:batchId', {}, {
            query: {method: 'GET', params: {batchId: ''}}
        });
    }]);
