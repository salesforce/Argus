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

var controlsModule = angular.module('argusControls',[]);

controlsModule.directive('agDashboardResource', ['DashboardService','$sce','$compile','$rootScope','$timeout', function(DashboardService,$sce,$compile,$rootScope,$timeout){
    return{
        restrict:'E',
        scope:false,
        link: function(scope,element,attribute){

            var dashboardID;
            if(attribute.id && attribute.id>0) {
               dashboardID = attribute.id;
            }else{
                dashboardID=scope.dashboardId;
            }

            DashboardService.getDashboardById(dashboardID).success(function (data){

                element.html('<div> <h4>' +  data.name + ' - ' + data.description + '</h4> <br>' + $sce.trustAsHtml(data.content) + '</div>');
                $compile(element.contents())(scope);
                //scope.$broadcast('submitButtonEvent', {});

            });

        }
    }
}]);

controlsModule.directive('agDashboard', ['$rootScope',function($rootScope){

    return{
        restrict:'E',
        scope:{
            name:'@'
        },
        transclude:true,
        template:'<div ng-transclude=""></div>',
        controller: function($scope){
            $scope.controls = [];
            this.updateControl = function(controlName, controlValue, controlType){
            	var controlExists = false;
            	for(var i in $scope.controls) {
            		if($scope.controls[i].name === controlName) {
            			$scope.controls[i].value = controlValue;
            			controlExists = true;
            			break;
            		}
            	}
            	
            	if(!controlExists) {
            		var control = {
                			name: controlName,
                			value: controlValue,
                			type: controlType
                	};
                	$scope.controls.push(control);
            	}
            };

            this.getAllControls = function(){
            	return $scope.controls;
            };
            this.getSubmitBtnEventName = function(){
                return 'submitButtonEvent';
            };

            this.broadcastEvent = function(eventName, data){
            	console.log(eventName + ' was broadcast');
            	$scope.$broadcast(eventName, data);
            }

        },
        link:function(scope,element,attributes){
            if(!attributes.onload || attributes.onload == true) {
                scope.$broadcast('submitButtonEvent', scope.controls);
            }

        }
    }
}]);


controlsModule.directive('agText', ['CONFIG',function(CONFIG){

    return{
        restrict:'EA',
        scope:{
            controlName:'@name',
            labelName:'@label',
            controlValue:'@default'
        },
        require:'^agDashboard',
        //templateUrl:  CONFIG.templatePath + 'argus-text-control.html',
        template:'<B>{{labelName}} : </B> <input type="text" ng-model="controlValue">',
        link: function(scope,element,attributes,dashboardCtrl){
            dashboardCtrl.updateControl(scope.controlName, scope.controlValue, "agText");
            scope.$watch('controlValue', function(newValue, oldValue){
                dashboardCtrl.updateControl(scope.controlName, newValue, "agText");
            });
        }
    }
}]);


controlsModule.directive('agDropdown', ['CONFIG', 'Tags', function(CONFIG, Tags){
    return{
        restrict:'E',
        scope:{
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
            	        }
            	    }
            };
        },
        require:'^agDashboard',
        template:'<B>{{labelName}} : </B><div style="display: inline-block; width: 20%;"><selectize config="selectizeConfig" options="selectizeOptions" ng-model="controlValue"></div>',
        //template:'<B>{{labelName}} : </B><div style="display: inline-block; width: 20%;"><input type="text" ng-model="controlValue"></div>',
        link: function(scope, element, attributes, dashboardCtrl){
        	var key = attributes.key;
        	if(key) {
        		var promise = Tags.getDropdownOptions(key);
            	promise.success(function(data, status, headers, config) {
            		if(data && data[key]) {
            	    	var options;
            	    	for(var i in data) {
            	    		options = data[i];
            	    	}
            	    	
            	    	var selectize = element.find('selectize')[0].selectize;
            	    	
            	    	for(var i in options) {
            	    		var option = options[i];
            	    		selectize.addOption({
            	    			text: option,
            	    			value: option
            	    		});
            	    	}
            	    	
            	    	selectize.refreshOptions(false);
            		} else {
            			
            		}
            	}).error(function(data, status, headers, config) {
            		console.log("Error in retrieving tags");
                });
        	}
        	dashboardCtrl.updateControl(scope.controlName, scope.controlValue, "agDropdown");
            scope.$watch('controlValue', function(newValue, oldValue){
                dashboardCtrl.updateControl(scope.controlName, newValue, "agDropdown");
            });
        }
    }
    
}]);


controlsModule.directive('agDate', ['CONFIG',function(CONFIG){

    return{
        restrict:'E',
        scope:{
            controlName:'@name',
            labelName:'@label',
            controlValue:'@default'
        },
        controller: function($scope, $filter) {
        	$scope.datetimepickerConfig = {
        			dropdownSelector: '.my-toggle-select',
        			minuteStep: 1
        	};
        	
        	$scope.onSetTime = function(newDate, oldDate) {
        		$scope.controlValue = $filter('date')(newDate, "short");
        	}
        },
        require:'^agDashboard',
        template:'<B>{{labelName}} : </B><div class="dropdown" style="display: inline;"><a class="dropdown-toggle my-toggle-select" id="dLabel" role="button" data-toggle="dropdown" data-target="#" href=""><input type="text" class="input-medium" style="color:black;" ng-model="controlValue"></a> <ul class="dropdown-menu" role="menu" aria-labelledby="dLabel"> <datetimepicker ng-model="data.date" on-set-time="onSetTime(newDate, oldDate)" data-datetimepicker-config="datetimepickerConfig"></datetimepicker> </ul> </div>',
        link: function(scope, element, attributes, dashboardCtrl){
            dashboardCtrl.updateControl(scope.controlName, scope.controlValue, "agDate");
            scope.$watch('controlValue', function(newValue, oldValue){
                dashboardCtrl.updateControl(scope.controlName, newValue, "agDate");
            });
        }
    }
}]);


controlsModule.directive('agSubmit', ['$rootScope','$http',function($rootScope,$http){

    return{
        restrict:'E',
        require:'^agDashboard',
        template:'',
        link: function(scope,element,attributes,dashboardCtrl){

            var buttonName = 'Submit';
            if(element.text() && element.text().length>0){
                buttonName=element.text();
            }
            element.html('<button class="btn btn-primary btn-md">' +  buttonName + '</button>');

            element.on('click', function(){
                $http.pendingRequests=[]; //This line should be deleted.
                dashboardCtrl.broadcastEvent(dashboardCtrl.getSubmitBtnEventName(), dashboardCtrl.getAllControls());
                //console.log('Submit button event emitted.');
            });
        }
    }

}]);

//TODO delete the following

controlsModule.directive('agTrans', function(){
    return{
        restrict:'E',
        transclude:true,
        template:'<div ng-transclude=""> </div>'
    }
})
