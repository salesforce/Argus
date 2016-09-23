angular.module('argus.directives.controls.date', [])
.directive('agDate', ['CONFIG', '$routeParams', function(CONFIG, $routeParams) {
    return {
        restrict: 'E',
        scope: {
            controlName: '@name',
            labelName: '@label',
            controlValue: '@default'
        },
        controller: function($scope, $filter) {
            $scope.ctrlVal = $scope.controlValue;

            for (var prop in $routeParams) {
                if (prop == $scope.controlName) {
                    $scope.ctrlVal = $routeParams[prop];
                }
            }

        	$scope.datetimepickerConfig = {
    			dropdownSelector: '.my-toggle-select',
    			minuteStep: 1
        	};

        	$scope.onSetTime = function(newDate, oldDate) {
        		$scope.ctrlVal = $filter('date')(newDate, "short");
        	};
        },
        require: '^agDashboard',
        template: // TODO: move to external template
            '<strong>{{labelName}} : </strong>' +
            '<div class="dropdown" style="display: inline;">' +
                '<a class="dropdown-toggle my-toggle-select" id="dLabel" role="button" data-toggle="dropdown" data-target="#" href="">' +
                    '<input type="text" class="input-medium" style="color:black;" ng-model="ctrlVal">' +
                '</a>' +
                '<ul class="dropdown-menu" role="menu" aria-labelledby="dLabel">' +
                    '<datetimepicker ng-model="data.date" on-set-time="onSetTime(newDate, oldDate)" data-datetimepicker-config="datetimepickerConfig"></datetimepicker>' +
                '</ul>' +
            '</div>',
        link: function(scope, element, attributes, dashboardCtrl) {
            dashboardCtrl.updateControl(scope.controlName, scope.ctrlVal, "agDate");
            scope.$watch('ctrlVal', function(newValue, oldValue) {
                dashboardCtrl.updateControl(scope.controlName, newValue, "agDate", true);
            });
        }
    }
}]);