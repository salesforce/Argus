'use strict';
/*global angular:false */

angular.module('argus.directives.controls.dropdown', [])
.directive('agDropdown', ['Tags', function(Tags) {
	return {
		restrict: 'E',
		scope: {
			controlName: '@name',
			labelName: '@label',
			controlValue: '@default',
		},
		controller: function($scope) {
			$scope.selectizeOptions = [];
			$scope.selectizeConfig = {
				delimiter: '|',
				create: function(input) {
					return {
						value: input,
						text: input
					};
				}
			};
		},
		require: '^agDashboard',
		template:
			'<B>{{labelName}} : </B>' +
			'<div style="display: inline-block; width: 20%;">' +
				'<selectize config="selectizeConfig" options="selectizeOptions" ng-model="controlValue">' +
			'</div>',
		link: function(scope, element, attributes, dashboardCtrl) {
			var key = attributes.key;
			if (key) {
				var promise = Tags.getDropdownOptions(key);
				promise.success(function(data) {
					if (data && data[key]) {
						var options;
						for(var i in data) {
							options = data[i];
						}

						var selectize = element.find('selectize')[0].selectize;

						for (var i in options) {
							var option = options[i];
							selectize.addOption({
								text: option,
								value: option
							});
						}

						selectize.refreshOptions(false);
					}
				}).error(function() {
					console.log('Error in retrieving tags');
				});
			}

			dashboardCtrl.updateControl(scope.controlName, scope.controlValue, 'agDropdown');
			scope.$watch('controlValue', function(newValue){
				dashboardCtrl.updateControl(scope.controlName, newValue, 'agDropdown');
			});
		}
	};
}]);