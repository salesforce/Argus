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

            if (controllers[0]) {
                elementCtrl = controllers[0];
            } else if (controllers[1]) {
                elementCtrl = controllers[1];
            } else {
                elementCtrl = controllers[2];
            }

            var metricName = 'metric_' + metricNameIndex++;
            var value = '';

            if (attributes.value && attributes.value.length > 0) {
                value = attributes.value;
            } else {
                value = element.text();
            }

            if (value && value.length > 0) {
                elementCtrl.updateMetric(metricName, value.replace(/(\r\n|\n|\r|\s+)/gm,""),scope.metricOptions);
            }

            scope.$watch('expression', function(newValue, oldValue) {
                if (newValue) {
                    elementCtrl.updateMetric(metricName, newValue,scope.metricOptions);
                }
            });
            element.html('<span> </span>');
        }
    }
});