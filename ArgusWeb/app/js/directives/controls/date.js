'use strict';
/*global angular:false */

angular.module('argus.directives.controls.date', [])
.directive('agDate', ['$routeParams', function($routeParams) {
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
				if (prop === $scope.controlName) {
					$scope.ctrlVal = $routeParams[prop];
					// remove GMT from page refreshing
					if( $scope.ctrlVal.indexOf('GMT') >= 0){
						$scope.ctrlVal = $scope.ctrlVal.replace('GMT','').trim();
						$scope.GMTon = true;
					}
				}
			}

			$scope.datetimepickerConfig = {
				dropdownSelector: '.my-toggle-select',
				minuteStep: 1
			};

			$scope.onSetTime = function(newDate) {
				$scope.ctrlVal = $filter('date')(newDate, 'short');
			};
		},
		require: '^agDashboard',
		template:
			'<strong>{{labelName}} </strong>' +
			'<div class="dropdown" style="display: inline;">' +
				'<a class="dropdown-toggle my-toggle-select" id="dLabel" role="button" data-toggle="dropdown">' +
					'<input type="text" class="input-medium" style="color:#000;" ng-model="ctrlVal">' +
				'</a>' +
				'<ul class="dropdown-menu" role="menu" aria-labelledby="dLabel">' +
					'<datetimepicker ng-model="data.date" on-set-time="onSetTime(newDate, oldDate)" data-datetimepicker-config="datetimepickerConfig"></datetimepicker>' +
				'</ul>' +
			'</div>' +
			'<label class="GMT-select">GMT: <input type="checkbox" ng-model="GMTon" ng-checked="GMTon || (ctrlVal[0] === \'-\')" ng-disabled="ctrlVal[0] === \'-\'"></label>',
		link: function(scope, element, attributes, dashboardCtrl) {
			dashboardCtrlUpdateControlGMTHelper(scope.ctrlVal, scope.GMTon);
			scope.$watch('ctrlVal', function(newValue) {
				dashboardCtrlUpdateControlGMTHelper(newValue, scope.GMTon);
			});
			scope.$watch('GMTon', function(newValue) {
				dashboardCtrlUpdateControlGMTHelper(scope.ctrlVal, newValue);
			});

			function dashboardCtrlUpdateControlGMTHelper(controlValue, GMTon) {
				if (GMTon) {
					dashboardCtrl.updateControl(scope.controlName, controlValue + ' GMT', 'agDate', true);
				} else {
					dashboardCtrl.updateControl(scope.controlName, controlValue, 'agDate', true);
				}
			}
		}
	};
}]);
