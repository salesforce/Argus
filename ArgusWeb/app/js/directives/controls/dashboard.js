'use strict';
/*global angular:false */

angular.module('argus.directives.controls.dashboard', [])
.directive('agDashboard', ['$location', '$routeParams', 'Controls', function($location, $routeParams, Controls) {
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
				var controlExists = false;
				var control = {
					name: controlName,
					value: controlValue,
					type: controlType
				};

				if (!localSubmit) {
					for (var prop in $routeParams) {
						if (prop === $scope.controlName) {
							controlValue = $routeParams[prop];
						}
					}
				}

				for (var i in $scope.controls) {
					if ($scope.controls[i].name === controlName) {
						$scope.controls[i].value = controlValue;
						controlExists = true;
						break;
					}
				}

				if (!controlExists) {
					$scope.controls.push(control);
				}

				//add controls to url
				this.addControlsToUrl();

				// broadcast update
				this.broadcastEvent(this.getControlChangeEventName(), control);
			};

			this.addControlsToUrl = function () {
				var controls = $scope.controls;
				// update url with controls params
				var urlStr = Controls.getUrl(controls);
				$location.search(urlStr);
			};

			this.getAllControls = function(){
				return $scope.controls;
			};

			this.getSubmitBtnEventName = function(){
				return 'submitButtonEvent';
			};

			this.getControlChangeEventName = function() {
				return 'updateControl';
			};

			this.broadcastEvent = function(eventName, data){
				$scope.$broadcast(eventName, data);
			};
		},
		link:function(scope, element, attributes){
			if (!attributes.onload || attributes.onload === true) {
				scope.$broadcast('submitButtonEvent', scope.controls);
			}
		}
	};
}]);