/*! Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 *      Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 *      Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 *      Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
/**
 * Created by liuxizi.xu on 9/2/16.
 */
'use strict';

angular.module('argus.directives')
    .directive('tableList', function() {
        return {
            restrict: "E",
            templateUrl: 'js/templates/tableList.html',
            scope: {
                properties: '=',
                colName: '=',
                dataSet: '=data',
                allData: '=tot',
                addItem: '&',
                delete: '&',
                disabled: '&',
                enable: '&'
            },
            controller: ['$scope', 'InputTracker', function($scope, InputTracker) {
                // TODO: move this to a service
                // itemsPerPage setting
                $scope.itemsPerPageOptions = [5, 10, 15, 25, 50, 100, 200];
                var itemsPerPageFromStorage = $scope.properties.type + '-itemsPerPage';
                $scope.itemsPerPage = InputTracker.getDefaultValue(itemsPerPageFromStorage, $scope.itemsPerPageOptions[1]);
                $scope.$watch('itemsPerPage', function(newValue) {
                    InputTracker.updateDefaultValue(itemsPerPageFromStorage, $scope.itemsPerPageOptions[1], newValue)
                    $scope.update();
                });

                // searchText setting
                var searchTextFromStorage = $scope.properties.type + '-searchText';
                $scope.searchText = InputTracker.getDefaultValue(searchTextFromStorage, "");
                $scope.$watch('searchText', function(newValue) {
                    InputTracker.updateDefaultValue(searchTextFromStorage, "", newValue);
                });

                // pagination page setting
                var currentPageFromStorage = $scope.properties.type + '-currentPage';
                $scope.currentPage = InputTracker.getDefaultValue(currentPageFromStorage, 1);
                $scope.$watch('currentPage', function (newValue) {
                    InputTracker.updateDefaultValue(currentPageFromStorage, 1, newValue);
                    $scope.update();
                });

                // sort setting
                var sortKeyFromStorage = $scope.properties.type + '-sortKey';
                $scope.sortKey = InputTracker.getDefaultValue(sortKeyFromStorage, 'modifiedDate');
                var sortReverseFromStorage = $scope.properties.type + '-sortReverse';
                $scope.reverse = InputTracker.getDefaultValue(sortReverseFromStorage, true);
                $scope.sort = function (key) {
                    if ($scope.sortKey === key) {
                        $scope.reverse = !$scope.reverse;
                        InputTracker.updateDefaultValue(sortReverseFromStorage, true, $scope.reverse);
                    } else {
                        $scope.sortKey = key;
                        InputTracker.updateDefaultValue(sortKeyFromStorage, 'modifiedDate', $scope.sortKey);
                    }
                };

                //enableAlert, isDisabled & delete setting
                $scope.deleteItem = function(item) {
                    $scope.delete()(item);
                };
                $scope.isDisabled = function(item) {
                    if ($scope.properties.type == 'dashboards') {
                        $scope.disabled()(item);
                    }
                    // do nothing if its alert
                };
                $scope.enableItem = function(item, enabled) {
                    $scope.enable()(item, enabled);
                };

                // total number setting
                $scope.$watch('dataSet.length', function () {
                    $scope.update();
                });
                $scope.update = function(){
                    $scope.start = ($scope.currentPage - 1)* $scope.itemsPerPage + 1;
                    var end = $scope.start + $scope.itemsPerPage - 1;
                    $scope.end = end < $scope.dataSet.length ? end : $scope.dataSet.length;
                };
            }]
        };
    });
