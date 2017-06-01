/*global angular:false, $:false, console:false */
'use strict';

angular.module('argus.controllers.metricsBrowsing', ['ngResource'])
.controller('MetricsBrowsing', ['$scope', 'growl', 'Browsing', function ($scope, growl, Browsing) {
    var defaultExpression = "";
    var startingPoints;
    $scope.expression = defaultExpression;
    Browsing.query({query: defaultExpression}, function (data) {
        console.log(data);
    }, function (error) {
        growl.error(error.data.message);
        console.log(error);
    });
}]);
