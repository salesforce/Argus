angular.module('argus.directives.charts.table', [])
.directive('agTable', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var tableNameIndex = 1;
    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElements',
        template: '<div ng-transclude=""></div>',
        link: function(scope, element, attributes, dashboardCtrl) {
        	DashboardService.buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.table, tableNameIndex++, DashboardService, growl);
        }
    }
}]);