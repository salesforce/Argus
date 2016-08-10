angular.module('argus.directives.controls.text', [])
.directive('agText', ['CONFIG', function(CONFIG) {
    return {
        restrict: 'EA',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default'
        },
        require: '^agDashboard',
        //templateUrl:  CONFIG.templatePath + 'argus-text-control.html',
        template: '<B>{{labelName}} : </B> <input type="text" ng-model="controlValue">',
        link: function(scope, element, attributes, dashboardCtrl) {
            dashboardCtrl.updateControl(scope.controlName, scope.controlValue, "agText");
            scope.$watch('controlValue', function(newValue, oldValue) {
                dashboardCtrl.updateControl(scope.controlName, newValue, "agText");
            });
        }
    }
}]);