angular.module('argus.directives.controls.dashboard', [])
.directive('agDashboard', ['$rootScope', function($rootScope) {
    return {
        restrict: 'E',
        scope: {
            name: '@'
        },
        transclude: true,
        template: '<div ng-transclude=""></div>',
        controller: function($scope) {
            $scope.controls = [];
            this.updateControl = function(controlName, controlValue, controlType) {
            	var controlExists = false;
            	for (var i in $scope.controls) {
            		if ($scope.controls[i].name === controlName) {
            			$scope.controls[i].value = controlValue;
            			controlExists = true;
            			break;
            		}
            	}
            	
            	if (!controlExists) {
            		var control = {
            			name: controlName,
            			value: controlValue,
            			type: controlType
                	};
                	$scope.controls.push(control);
            	}
            };

            this.getAllControls = function(){
            	return $scope.controls;
            };
            this.getSubmitBtnEventName = function(){
                return 'submitButtonEvent';
            };

            this.broadcastEvent = function(eventName, data){
            	console.log(eventName + ' was broadcast');
            	$scope.$broadcast(eventName, data);
            }
        },
        link:function(scope,element,attributes){
            if (!attributes.onload || attributes.onload == true) {
                scope.$broadcast('submitButtonEvent', scope.controls);
            }
        }
    }
}]);