/**
 * Created by pfu on 9/20/16.
 */
angular.module('argus.controllers.d3test', [])
    .controller('D3test', ['$scope', 'Metrics', 'growl', function ($scope, Metrics, growl) {
        //some default val
        $scope.expression = "-10h:argus.core:alerts.evaluated:sum";

        $scope.getMetricData = function () {
            Metrics.query({expression: $scope.expression}, function (data) {
                $scope.series = $scope.copySeries(data);
            }, function (error) {
                growl.error(error.data.message, {referenceId: 'viewmetrics-error'});
            });
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
                    result.push({name: id, id: id, data: series, marker : {enabled : true, radius: 1}});
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

        $scope.getMetricData();

    }]);