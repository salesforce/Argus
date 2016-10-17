angular.module('argus.directives.controls.text', [])
.directive('agText', ['CONFIG', 'Controls', function(CONFIG, Controls) {
    return {
        restrict: 'EA',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default'
        },
        controller: function($scope) {
            // check if Controls ($routeParams) should override controlValue
            $scope.ctrlVal = Controls.updateControlValue($scope.controlName, $scope.controlValue);
        },
        require:'^agDashboard',
        template:'<B>{{labelName}} : </B> <input type="text" ng-model="ctrlVal">',
        link: function(scope, element, attributes, dashboardCtrl) {
            dashboardCtrl.updateControl(scope.controlName, scope.ctrlVal, "agText");
            scope.$watch('ctrlVal', function(newValue, oldValue){
                dashboardCtrl.updateControl(scope.controlName, newValue, "agText", true);
            });
        }
    }
}]);