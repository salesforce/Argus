angular.module('argus.directives.charts.statusIndicator', [])
.directive('agStatusIndicator', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var metricNameIndex = 1;
    return {
        restrict: 'E',
        transclude: true,
        scope: {
            serviceName: '@name',
            hi: '@hi',
            lo: '@lo'
        },
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""> </div>',
        link: function(scope, element, attributes, dashboardCtrl) {
            var metricExpression;
            var indicatorHTML = 
                '<div class="serviceItem">' +
                    '<div class="serviceName">' + attributes.name + '</div>' +
                    '<div id="'+ attributes.name + '-status" class="statusIndicator"></div>' +
                '</div>';
            
            // render status indicator
            element.html(indicatorHTML);

            // listen to scope for event and controls info
            scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls) {
                for (var key in scope.metrics) {
                    if (scope.metrics.hasOwnProperty(key)) {
                        // get metricExpression from scope
                        metricExpression = scope.metrics[key].expression;

                        // process mertricExpression from controls if present
                        if ( controls ) {
                            metricExpression = DashboardService.augmentExpressionWithControlsData(event, metricExpression, controls);
                        }
                    }
                }

                // get datapoints from metric expression
                if ( metricExpression) {
                    DashboardService.getMetricData(metricExpression)
                        .then(function( result ) {
                            var datapoints = result.data[0].datapoints;
                            var lastStatusVal = Object.keys(datapoints).sort().reverse()[0];
                            lastStatusVal = datapoints[lastStatusVal];

                            // update status indicator
                            DashboardService.updateIndicatorStatus(attributes, lastStatusVal);
                        });
                }
            });
        }
    }
}]);