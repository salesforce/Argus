/**
 * Created by pfu on 4/25/17.
 */
'use strict';
/*global angular:false, Set:false */

angular.module('argus.directives.controls.select', ['selectize'])
	.directive('agSelect', ['$routeParams', function($routeParams) {
		return {
			restrict: 'E',
			transclude: true,
			scope: {
				controlName: '@name',
				labelName: '@label',
				controlValue: '@default'
			},
			controller: function($scope) {
				$scope.ctrlVal = $scope.controlValue;

				for (var prop in $routeParams) {
					if (prop === $scope.controlName) {
						$scope.ctrlVal = $routeParams[prop];
					}
				}

				$scope.selectizeOptions = [];
				$scope.selectizeConfig = {
					delimiter: '|',
					sortField: '$order',
					maxItems: 1,
					create: false
				};
			},
			require: '^agDashboard',
			template:
			'<ng-transclude></ng-transclude>'+
			'<B>{{labelName}} : </B>' +
			'<div style="display: inline-block; ">' +
			'<selectize config="selectizeConfig" options="selectizeOptions" ng-model="ctrlVal"/>' +
			'</div>'
			,
			link: function(scope, element, attributes, dashboardCtrl) {
				var selectize = element.find('selectize')[0].selectize;

				var optionSet = new Set();
				//find all option tags in the ag-select tag, and add their contents
				element.find('ng-transclude option').each(function(){
					selectize.addOption({
						text: this.innerText,
						value: this.value
					});
					optionSet.add(this.value);
				});

				for (var prop in $routeParams) {
					if (prop === scope.controlName) {
						var val = $routeParams[prop];
						if(val in optionSet){
							scope.ctrlVal = val; //to disable adding new option through url parameter
						}
						break;
					}
				}

				element.find('ng-transclude').remove();
				selectize.refreshOptions(false);

				dashboardCtrl.updateControl(scope.controlName, scope.ctrlVal, 'agSelect');
				scope.$watch('ctrlVal', function(newValue){
					dashboardCtrl.updateControl(scope.controlName, newValue, 'agSelect');
				});
			}
		};
	}]);