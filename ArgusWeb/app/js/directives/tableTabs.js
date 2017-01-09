/**
 * Created by liuxizi.xu on 1/6/17.
 */
'use strict';

angular.module('argus.directives')
    .directive('tableTabs', function () {
        return {
            restrict: 'E',
            templateUrl: 'js/templates/tableTabs.html',
            scope: {
                tabNames: '=',
                properties: '=',
                loaded: '=',
                shared: '=',
                getList: '&'

            },
            controller: ['$scope', '$sessionStorage', function ($scope, $sessionStorage) {
                if ($sessionStorage[$scope.properties.type] !== undefined &&
                    $sessionStorage[$scope.properties.type].selectedTab !== undefined) {
                    $scope.selectedTab = $sessionStorage[$scope.properties.type].selectedTab;
                    $scope.shared = $sessionStorage[$scope.properties.type].shared;
                } else {
                    $scope.selectedTab = 1;
                    $scope.shared = false;
                }

                $scope.isTabSelected = function (tab) {
                    return $scope.selectedTab === tab;
                };
                $scope.selectTab = function (tab) {
                    $scope.shared = tab !== 1;
                    $scope.selectedTab = tab;
                    $sessionStorage[$scope.properties.type].selectedTab = tab;
                };
            }]
        };
    });
