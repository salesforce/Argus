'use strict';
/*global angular:false */

angular.module('argus.directives.controls.text', [])
.directive('agText', ['CONFIG', '$routeParams', function(CONFIG, $routeParams) {
	return {
		restrict: 'EA',
		scope: {
			controlName: '@name',
			labelName: '@label',
			controlValue: '@default',
			elemId: '@id',
			cssName: '@class',
			style: '@style',
			size: '@size'
		},
		controller: function($scope) {
			$scope.ctrlVal = $scope.controlValue;

			for (var prop in $routeParams) {
				if (prop == $scope.controlName) {
					$scope.ctrlVal = $routeParams[prop];
				}
			}
		},
		require: '^agDashboard',
		template: '<strong>{{labelName}} </strong><input id="{{elemId}}" type="text" class="{{cssName}}" size="{{size}}" style="{{style}}" ng-model="ctrlVal">',
		link: function(scope, element, attributes, dashboardCtrl) {
			dashboardCtrl.updateControl(scope.controlName, scope.ctrlVal, 'agText');
			scope.$watch('ctrlVal', function(newValue){
				dashboardCtrl.updateControl(scope.controlName, newValue, 'agText', true);
			});
		}
	};
}]);