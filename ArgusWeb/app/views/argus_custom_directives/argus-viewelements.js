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

var viewElementsModule = angular.module('argusViewElements', ['argusControls', 'angular-table']);

viewElementsModule.controller('ViewElementCtrl', function($scope) {
	
    $scope.metrics={};
    $scope.annotations={};
    $scope.options={};

    this.updateMetric = function(name,expression, metricSpecicOptions) {
        var metric={'expression':expression,'metricSpecicOptions':metricSpecicOptions};
    	$scope.metrics[name]=metric;
    };

    this.updateAnnotation = function(name,expression){
        $scope.annotations[name] = expression;
    };

    this.updateOption = function(name,value){
        $scope.options[name]=value;
    };
    
});

viewElementsModule.controller('metricElementCtrl', function($scope) {
	
    $scope.metricOptions={};

    this.updateOption = function(name,value){
        $scope.metricOptions[name]=value;
    };
    
});

viewElementsModule.directive('agChart', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var chartNameIndex=1;
    return {
        restrict: 'E',
        transclude: true,
        scope:{},
        require:'^agDashboard',
        controller: 'ViewElementCtrl',
        template:'<div ng-transclude=""> </div>',
        link: function(scope, element, attributes, dashboardCtrl){
        	/*
        	if(attributes.segments && attributes.segmentSize && attributes.segmentOffset) {
        		//TODO: This is incomplete. Implemented to support segmenting of a single chart into separate time windows.
        		var segments = parseInt(attributes.segments);
        		var segSizeInMillis = getMillis(attributes.segmentSize);
        		var segOffsetInMillis = getMillis(attributes.segmentOffset);
        		
        	}
        	*/
        	buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.chart, chartNameIndex++, DashboardService);
        }
    }
}]);

viewElementsModule.directive('agHeatmap', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var heatmapNameIndex=1;
    return {
        restrict: 'E',
        transclude: true,
        scope:{},
        require:'^agDashboard',
        controller: 'ViewElementCtrl',
        template:'<div ng-transclude=""> </div>',
        link: function(scope, element, attributes, dashboardCtrl){
            buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.heatmap, heatmapNameIndex++, DashboardService);
        }
    }
}]);


/**Segment size or Offset can be specified in terms of seconds, minutes, hours or days.
 * For e.g.: segmentOffset="7d" segmentSize="5h"
 * 
 * The last digit will always be either of "s", "m", "h" or "d". And the first to penultimate digits will be the amount.
**/
var TimeUnit = Object.freeze({"SECOND": {"key": "s", "value": 1000}, 
							  "MINUTE": {"key": "m", "value": 60*1000},
							  "HOUR": {"key": "h", "value": 3600*1000},
							  "DAY": {"key": "d", "value": 24*3600*1000},});
function getMillis(str) {
	var timeUnit = str.substring(str.length-1);
	var time = parseInt(str.substring(0, str.lenght-1));
	
	
	if(timeUnit === TimeUnit.SECOND.key || timeUnit == TimeUnit.MINUTE.key || 
			timeUnit == TimeUnit.HOUR.key || timeUnit == TimeUnit.DAY.key) {
		return time * 100;
	}
	
}

viewElementsModule.directive('agTable', ['DashboardService', 'growl', 'VIEWELEMENT', function(DashboardService, growl, VIEWELEMENT) {
    var tableNameIndex = 1;
    return {
        restrict: 'E',
        transclude: true,
        scope: {},
        require: '^agDashboard',
        controller: 'ViewElementCtrl',
        template: '<div ng-transclude=""></div>',
        link: function(scope, element, attributes, dashboardCtrl) {
        	buildViewElement(scope, element, attributes, dashboardCtrl, VIEWELEMENT.table, tableNameIndex++, DashboardService, growl);
        }
    }
}]);

function buildViewElement(scope, element, attributes, dashboardCtrl, elementType, index, DashboardService, growl) {
	
	var elementId = 'element_' + elementType + index;
    element.prepend('<div id=' + elementId + '></div>');
    
    scope.$on(dashboardCtrl.getSubmitBtnEventName(), function(event, controls){
        console.log(dashboardCtrl.getSubmitBtnEventName() + ' event received.');
        populateView(event, controls);
    });

    function populateView(event, controls) {
        var updatedMetricList=[];
        var updatedAnnotationList=[];
        var updatedOptionList = [];

        for (var key in scope.metrics) {
            if (scope.metrics.hasOwnProperty(key)) {
            	var metric = scope.metrics[key];
                var processedExpression = augmentExpressionWithControlsData(event, metric.expression,controls);
                if(processedExpression.length>0 /* && (/\$/.test(processedExpression)==false) */) {
                	var processedMetric={};
                	processedMetric['expression']=processedExpression;
                	processedMetric['metricSpecicOptions']=getMetricSpecificOptionsInArray(metric.metricSpecicOptions);
                    updatedMetricList.push(processedMetric);
                }
            }
        }

        for (var key in scope.annotations) {
            if (scope.annotations.hasOwnProperty(key)) {
                var processedExpression = augmentExpressionWithControlsData(event, scope.annotations[key],controls);
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
            DashboardService.populateView(updatedMetricList, updatedAnnotationList, updatedOptionList, elementId, attributes, elementType, scope);
        } else {
            growl.error('The valid metric expression(s) is required to display the chart.', {referenceId: 'growl-error'});
            $('#' + elementId).hide();
        }
    }
    
    function getMetricSpecificOptionsInArray(metricSpecicOptions){
    	
    	var options=[];
    	for (var key in metricSpecicOptions) {
            if (metricSpecicOptions.hasOwnProperty(key)) {
            	options.push({'name': key, 'value': metricSpecicOptions[key]});

            }
        }
    	return options;
    }
    
    function augmentExpressionWithControlsData(event, expression, controls) {
        var result = expression;
        
        for(var controlIndex in controls) {
            var controlName = '\\$' + controls[controlIndex].name + '\\$';
            var controlValue = controls[controlIndex].value;
            var controlType = controls[controlIndex].type;
            if(controlType === "agDate") {
            	controlValue = isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
            }
            controlValue = controlValue == undefined ? "" : controlValue;
            //controlValue = controlValue == undefined ? "" : 
            	//isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
            result = result.replace(new RegExp(controlName, "g"), controlValue);
            
            /*
            if(controlValue) {
            	result = result.replace(new RegExp(controlName, "g"), controlValue);
            } else {
            	result = result.replace(new RegExp(controlName, "g"), "");
            }
            
            /*
            if(result.indexOf("{}") != -1) {
            	result = result.replace("{}", "");
            }
            */
        }
        
        result = result.replace(/(\r\n|\n|\r|\s+)/gm, "");
        return result;
    }
    
}


viewElementsModule.directive('agMetric', function() {
    var metricNameIndex=100;
    return {
        restrict: 'E',
        require: ['?^agChart', '?^agHeatmap', '?^agTable'], 
        scope:{
            expression:'@'
        },
        controller: 'metricElementCtrl',
        template: '',
        link: function(scope, element, attributes, controllers){
        	var elementCtrl;
        	if(controllers[0]) {
        		elementCtrl = controllers[0];
        	} else if (controllers[1]) {
        		elementCtrl = controllers[1];
        	} else {
                elementCtrl = controllers[2];
            }
        	
            var metricName='metric_'+ metricNameIndex++;

            var value = '';
            if(attributes.value && attributes.value.length>0) {
                value = attributes.value;
            } else {
                value = element.text();
            }
            if(value && value.length>0){
                elementCtrl.updateMetric(metricName, value.replace(/(\r\n|\n|\r|\s+)/gm,""),scope.metricOptions);
            }

            scope.$watch('expression', function(newValue, oldValue){
                if(newValue) {
                    elementCtrl.updateMetric(metricName, newValue,scope.metricOptions);
                }
            });
            element.html('<span> </span>');
        }
    }
});

viewElementsModule.directive('agFlags', function() {
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

/*
viewElementsModule.directive('segments', function() {
    var flagNameIndex=100;
    return {
        restrict: 'A',
        require: ['agChart', 'segmentOffset'],
        template: '',
        priority: 0,
        link: function(scope, element, attributes, chartCtrl){
            console.log("I am segments");
        }
    }
});

viewElementsModule.directive('segmentOffset', function() {
    var flagNameIndex=100;
    return {
        restrict: 'A',
        require: 'agChart',
        template: '',
        priority: 1,
        link: function(scope, element, attributes, chartCtrl){
            console.log("I am segment offset");
        }
    }
});
*/

viewElementsModule.directive('agOption', function() {
    return {
        restrict:'E',
        require: ['?^agChart', '?^agHeatmap', '?^agTable', '?^agMetric'], 
        scope:{},
        template:'',
        link: function(scope, element, attributes, controllers) {
        	var elementCtrl;
        	if (controllers[3]) {
        		elementCtrl = controllers[3];
        	} else if(controllers[0]) {
                elementCtrl = controllers[0];
            } else if (controllers[1]) {
                elementCtrl = controllers[1];
            } else {
                elementCtrl = controllers[2];
            }
        	
            var value = '';
            if(attributes.value && attributes.value.length>0) {
                value = attributes.value;
            } else {
                value = element.text();
            }
            
            elementCtrl.updateOption(attributes.name,value);
            element.html('<span> </span>');
        }
    }
});
