angular.module('argus.controllers.viewelements', [])
.controller('ViewElements', function($scope) {
    $scope.metrics = {};
    $scope.annotations = {};
    $scope.options = {};

    this.updateMetric = function(name, expression, metricSpecificOptions, seriesData) {
        var metric = {
            'name': seriesData.name,
            'color': seriesData.color,
            'expression': expression,
            'metricSpecificOptions': metricSpecificOptions
        };
    	$scope.metrics[name] = metric;
    };

    this.updateAnnotation = function(name, expression) {
        $scope.annotations[name] = expression;
    };

    this.updateOption = function(name, value) {
        $scope.options[name] = value;
    };
});