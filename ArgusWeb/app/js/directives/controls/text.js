angular.module('argus.directives.controls.text', [])
.directive('agText', ['$routeParams', 'CONFIG', function($routeParams, CONFIG) {
    return {
        restrict: 'EA',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default'
        },
        // require: '^agDashboard',
        // //templateUrl:  CONFIG.templatePath + 'argus-text-control.html',
        // template: '<B>{{labelName}} : </B> <input type="text" ng-model="controlValue">',
        // link: function(scope, element, attributes, dashboardCtrl) {
        //     dashboardCtrl.updateControl(scope.controlName, scope.controlValue, "agText");
        //     scope.$watch('controlValue', function(newValue, oldValue) {
        //         dashboardCtrl.updateControl(scope.controlName, newValue, "agText");
        //     });
        // }

        controller: function($scope) {
            $scope.ctrlVal = $scope.controlValue;

            // check $routeParams to override controlValue
            for (var prop in $routeParams) {
                if (prop == $scope.controlName) {
                    $scope.ctrlVal = $routeParams[prop];
                }
            };
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