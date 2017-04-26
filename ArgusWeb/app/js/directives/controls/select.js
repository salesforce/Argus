/**
 * Created by pfu on 4/25/17.
 */
'use strict';
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
                    if (prop == $scope.controlName) {
                        $scope.ctrlVal = $routeParams[prop];
                    }
                }

                $scope.selectizeOptions = [];
                $scope.selectizeConfig = {
                    delimiter: '|',
                    sortField: "text",
                    maxItems: 1,
                    create: false
                };
            },
            require: '^agDashboard',
            template:
            '<ng-transclude></ng-transclude>'+
            '<B>{{labelName}} : </B>' +
            '<div style="display: inline-block; width: 20%;">' +
            '<selectize config="selectizeConfig" options="selectizeOptions" ng-model="ctrlVal"/>' +
            '</div>'
            ,
            link: function(scope, element, attributes, dashboardCtrl) {
                var selectize = element.find('selectize')[0].selectize;

                //find all option tags in the ag-select tag, and add their contents
                element.find('ng-transclude option').each(function(){
                    selectize.addOption({
                        text: this.innerHTML,
                        value: this.value
                    });
                });

                element.find('ng-transclude').remove();
                selectize.refreshOptions(false);

                dashboardCtrl.updateControl(scope.controlName, scope.controlValue, 'agSelect');
                scope.$watch('ctrlVal', function(newValue, oldVal){
                    dashboardCtrl.updateControl(scope.controlName, newValue, 'agSelect');
                });
            }
        };
    }]);