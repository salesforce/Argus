angular.module('argus.controllers.metricelements', [])
.controller('metricElements', function($scope) {
    $scope.metricOptions = {};

    this.updateOption = function(name, value) {
        $scope.metricOptions[name] = value;
    };
});