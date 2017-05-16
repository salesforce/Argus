/**
 * Created by liuxizi.xu on 1/6/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.directives')
	.directive('tableTabs', function () {
		return {
			restrict: 'E',
			templateUrl: 'js/templates/tableTabs.html',
			scope: {
				tabNames: '=',
				properties: '=',
				loaded: '=',
				selectedTab: '=',
				getList: '&'
			},
			controller: ['$scope', '$sessionStorage', function ($scope, $sessionStorage) {
				if ($sessionStorage[$scope.properties.type] !== undefined &&
					$sessionStorage[$scope.properties.type].selectedTab !== undefined) {
					// when user switch from privileged user to regular user in the same session
					if ($sessionStorage[$scope.properties.type].selectedTab === 3 && !$scope.tabNames.userPrivileged) {
						$scope.selectedTab = 1;
					}
					$scope.selectedTab = $sessionStorage[$scope.properties.type].selectedTab;
				} else {
					$scope.selectedTab = 1;
				}
				$sessionStorage[$scope.properties.type].selectedTab = $scope.selectedTab;

				$scope.isTabSelected = function (tab) {
					return $scope.selectedTab === tab;
				};
				$scope.selectTab = function (tab) {
					$scope.selectedTab = tab;
					$sessionStorage[$scope.properties.type].selectedTab = tab;
				};
			}]
		};
	});
