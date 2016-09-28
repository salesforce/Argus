/* currently not being used? */

angular.module('argus.directives.charts.flags', [])
.directive('agFlags', function() {
    var flagNameIndex = 100;
    return {
        restrict: 'E',
        require: '^agChart',
        scope: {
            expression: '@'
        },
        template: '',
        link: function(scope, element, attributes, chartCtrl) {
            var flagName = 'flag_' + flagNameIndex++;

            if (element.text() && element.text().length > 0) {
                chartCtrl.updateAnnotation(flagName, element.text().replace(/(\r\n|\n|\r|\s+)/gm,""));
            }

            scope.$watch('expression', function(newValue, oldValue) {
                if (newValue) {
                    chartCtrl.updateAnnotation(flagName, newValue);
                }
            });

            element.html('<span> </span>');
        }
    }
});