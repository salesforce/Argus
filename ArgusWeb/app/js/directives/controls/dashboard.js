angular.module('argus.directives.controls.dashboard', [])
.directive('agDashboard', ['$location', '$rootScope', 'Controls', function($location, $rootScope, Controls) {
    return {
        restrict: 'E',
        scope: {
            name: '@'
        },
        transclude: true,
        template: '<div ng-transclude=""></div>',
        controller: function($scope) {
            $scope.controls = [];
            this.updateControl = function(controlName, controlValue, controlType, localSubmit) {
                localSubmit = true;
            	var controlExists = false;

                if(!localSubmit){
                    controlValue = Controls.updateControlValue(controlName)
                }

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
                //add controls to url
            	this.addControlsToUrl()

            };

            this.addControlsToUrl = function () {
                var controls = $scope.controls;
                // update url with controls params
                var urlStr = Controls.getUrl(controls);
                $location.search(urlStr);
                //$location.search("start=-2d&end=-0d&scope=argus.jvm&metric=mem.heap.used&tags=host=*&aggregator=avgf");
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