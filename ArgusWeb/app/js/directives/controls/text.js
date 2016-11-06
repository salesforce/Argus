angular.module('argus.directives.controls.text', [])
.directive('agText', ['CONFIG', '$routeParams', function(CONFIG, $routeParams) {
    return {
        restrict: 'EA',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default'
        },
        controller: function($scope) {
            $scope.ctrlVal = $scope.controlValue;

            for (var prop in $routeParams) {
                if (prop == $scope.controlName) {
                    $scope.ctrlVal = $routeParams[prop];
                }
            }
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