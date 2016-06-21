'use strict';

var argusBatches = angular.module('argusBatches', [
    'ngResource'
]);

argusBatches.controller('BatchExpressionsCtrl', ['$scope', 'AsyncMetrics', 'Batches',
    function($scope, AsyncMetrics, Batches) {
        $scope.expressions = [{expression: ''}];
        $scope.batchState = 'Refresh for current batch status';
        $scope.submitted = false;
        $scope.currBatchId = '';
        
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
            AsyncMetrics.create(params)
                .then(function success(response) {
                    $scope.submitted = true;
                    $scope.currBatchId = response.data.id;
                }, function error(response) {
                    console.log('Error in batches.submitBatch:\n' + response);
                });
        }

        $scope.refreshBatch = function() {
            Batches.query({batchId: $scope.currBatchId}, function(data) {
                $scope.batchState = JSON.stringify(data);
                console.log(JSON.stringify(data));
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
