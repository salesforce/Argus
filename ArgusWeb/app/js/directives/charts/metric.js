angular.module('argus.directives.charts.metric', [])
.directive('agMetric', function() {
    var metricNameIndex = 100;
    return {
        restrict: 'E',
        require: ['?^agChart', '?^agStatusIndicator', '?^agHeatmap', '?^agTable'],
        scope: {
            expression: '@'
        },
        controller: 'metricElements',
        template: '',
        link: function(scope, element, attributes, controllers) {
            var elementCtrl;
            var value = '';
            var seriesData = {};
            var metricName = 'metric_' + metricNameIndex++;

            // TODO: refactor this to assign correct controllers. if an additional controller is added, this will break!
            if (controllers[0]) {
                elementCtrl = controllers[0];
            } else if (controllers[1]) {
                elementCtrl = controllers[1];
            } else if (controllers[2]) {
                elementCtrl = controllers[2];
            } else {
                elementCtrl = controllers[3];
            }

            // separate specific series data from other attributes
            // 'color' & 'name' are used to supplement the 'series' data when rendering a chart
            seriesData.color = attributes.seriescolor;
            seriesData.name = attributes.seriesname;

            if (attributes.value && attributes.value.length > 0) {
                value = attributes.value;
            } else {
                value = element.text();
            }

            if (value && value.length > 0) {
                elementCtrl.updateMetric(metricName, value.replace(/(\r\n|\n|\r|\s+)/gm,""), scope.metricOptions, seriesData);
            }

            scope.$watch('expression', function(newValue, oldValue) {
                if (newValue) {
                    elementCtrl.updateMetric(metricName, newValue, scope.metricOptions, seriesData);
                }
            });
            element.html('<span> </span>');
        }
    }
});