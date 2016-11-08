angular.module('argus.directives.charts.chart', [])
.directive('agChart', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var chartNameIndex = 1;
    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""> </div>',
        link: function(scope, element, attributes, dashboardCtrl) {
        	DashboardService.buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.chart, chartNameIndex++, DashboardService);
        }
    }
}]);