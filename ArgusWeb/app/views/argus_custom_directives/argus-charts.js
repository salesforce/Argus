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
'use strict';

var chartsModule = angular.module('argusCharts',['argusControls']);

chartsModule.directive('agChart', ['$rootScope','DashboardService','growl',function($rootScope, DashboardService, growl){
    var chartNameIndex=100;
    return {
        restrict: 'E',
        transclude: true,
        scope:{},
        require:'^agDashboard',
        controller: function($scope){
            $scope.metrics=[];
            $scope.annotations={};
            $scope.options={};

            this.updateMetric = function(name,expression, metricOptions) {
            	var objMetric = {};
            	objMetric.name=name;
            	objMetric.expression=expression;
            	objMetric.metricOptions=metricOptions;
                $scope.metrics.push(metricOptions);
            };

            this.updateAnnotation = function(name,expression){
                $scope.annotations[name] = expression;
            };

            this.updateOption = function(name,value){
                $scope.options[name]=value;
            };
            
        },

        template:'<div ng-transclude=""> </div>',
        link: function(scope,element,attributes, dashboardCtrl){

            var chartName='chart_'+ (attributes.name?attributes.name:'') + chartNameIndex++;
            var chartType= attributes.type?attributes.type:'LINE';


            element.prepend('<div id=' + chartName + '></div');

            scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event,controls){
                console.log(dashboardCtrl.getSubmitBtnEventName() + ' event received.');
                populateView(controls);
            });

            function populateView(controls){
                var updatedMetricList=[];
                var updatedAnnotationList=[];
                var updatedOptionList = [];

           /*     for (var key in scope.metrics) {
                    if (scope.metrics.hasOwnProperty(key)) {
                        var processedExpression = replaceWithControlsData(scope.metrics[key],controls);
                        if(processedExpression.length>0) {
                            updatedMetricList.push(processedExpression);
                        }
                    }
                }
             */   
                for(var i=0;i<scope.metrics.length;i++){
                	var objMetric = scope.metrics[i];
                	var updatedMetric = {};
                	updatedMetric.name=objMetric.name;
                	updatedMetric.expression=replaceWithControlsData(objMetric.expression,controls);
                	updatedMetric.metricOptions=objMetric.metricOptions;
                	updatedMetricList.push(updatedMetric);
                }

                for (var key in scope.annotations) {
                    if (scope.annotations.hasOwnProperty(key)) {
                        var processedExpression = replaceWithControlsData(scope.annotations[key],controls);
                        if(processedExpression.length>0 /* && (/\$/.test(processedExpression)==false) */) {
                            updatedAnnotationList.push(processedExpression);
                        }
                    }
                }
                for (var key in scope.options) {
                    if (scope.options.hasOwnProperty(key)) {
                        updatedOptionList.push({name: key, value: scope.options[key]});

                    }
                }

                if(updatedMetricList.length>0) {
                    DashboardService.populateView(updatedMetricList, updatedAnnotationList, updatedOptionList, chartName,chartType);
                }else{
                    growl.error('The valid metric expression(s) is required to display the chart.', {referenceId: 'growl-error'});
                    $('#' + chartName).hide();
                }
            }

            function replaceWithControlsData(metricExpression,controls){
                var result=metricExpression;
                for(var controlIndex in controls){
                    var controlName = '$' + controls[controlIndex].name + '$';
                    var controlValue = controls[controlIndex].value;
                    result = result.replace(controlName,controlValue);
                }
                result = result.replace(/(\r\n|\n|\r|\s+)/gm,"");
                return result;
            }
        }
    }
}]);


chartsModule.directive('agMetric', function(){
    var metricNameIndex=100;
    return {
        restrict: 'E',
        require: '^agChart',
        scope:{
            expression:'@'
        },
        controller: function($scope){
            $scope.metricOptions=[];
            this.updateMetricOption = function(name,value){
            	var metricOption={};
            	metricOption.name=name;
            	metricOption.value=value;
                $scope.metricOptions.push(metricOption);
            };
        },
        template: '',
        link: function(scope,element,attributes, chartCtrl){
            var metricName='metric_'+ metricNameIndex++;

            if(element.text() && element.text().length>0){
                chartCtrl.updateMetric(metricName, element.text().replace(/(\r\n|\n|\r|\s+)/gm,""),scope.metricOptions);
            }

            scope.$watch('expression', function(newValue, oldValue){
                if(newValue) {
                	chartCtrl.updateMetric(metricName, newValue, scope.metricOptions); //TODO verify if metricOptions are being passed as it is in link but we initialized it in controller
                }
            });
            element.html('<span> </span>');
        }
    }
});

chartsModule.directive('agFlags', function(){
    var flagNameIndex=100;
    return {
        restrict: 'E',
        require: '^agChart',
        scope:{
            expression:'@'
        },
        template:'',
        link: function(scope,element,attributes, chartCtrl){
            var flagName='flag_' + flagNameIndex++;

            if(element.text() && element.text().length>0){
                chartCtrl.updateAnnotation(flagName, element.text().replace(/(\r\n|\n|\r|\s+)/gm,""));
            }

            scope.$watch('expression', function(newValue,oldValue){
                if(newValue) {
                    chartCtrl.updateAnnotation(flagName, newValue);
                }
            });

            element.html('<span> </span>');
        }
    }
});

chartsModule.directive('agOption', function(){
    return{
        restrict:'E',
        require:'^agChart',
        scope:{},
        template:'',
        link: function(scope,element,attributes, chartCtrl){
            var value='';
            if(attributes.value && attributes.value.length>0){
                value=attributes.value;
            }else{
                value=element.text();
            }
            chartCtrl.updateOption(attributes.name,value);
            element.html('<span> </span>');
        }
    }
});


chartsModule.directive('agMetricOption', function(){
    return{
        restrict:'E',
        require:'^agMetric',
        scope:{},
        template:'',
        link: function(scope,element,attributes, metricCtrl){
            var value='';
            if(attributes.value && attributes.value.length>0){
                value=attributes.value;
            }else{
                value=element.text();
            }
            metricCtrl.updateMetricOption(attributes.name,value);
            element.html('<span> </span>');
        }
    }
});
