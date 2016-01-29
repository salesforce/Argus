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

var argusNamespace = angular.module('argusNamespace', [
    'ngResource'
]);

argusNamespace.controller('NamespaceCtrl', ['Storage', '$scope', 'growl', 'Namespace',
                                         function (Storage, $scope, growl, Namespace) {
	
	$scope.searchText = Storage.get("namespace-searchText") == null ? "" : Storage.get("namespace-searchText");
	$scope.addnamespaceflag=false;
    $scope.namespaces = Namespace.query();
    
    $scope.updateNamespace = function(namespace){
    	 //if ($scope.isAlertDirty()) { TODO: uncomment this
    	var userNames=namespace.usernames.toString();
    	namespace.usernames=(userNames && userNames.length>0)? userNames.split(','):[];
    	
    		Namespace.update({namespaceId: namespace.id}, namespace, function (result) {
                 growl.success(('Updated namespace "') + namespace.qualifier + '"');
                 
                 for(var i=0; i<$scope.namespaces.length;i++){
                	var oldNamespace = $scope.namespaces[i];
                	if(oldNamespace.id==result.id){
                		$scope.namespaces[i]=result;
                		break;
                	}
                	
                 }
            
             }, function (error) {
            	 growl.error('Failed to update namespace "' + namespace.qualifier + '"' + (error && error.data && error.data.message)?error.data.message:error.statusText);
             });
         //}
    };
	
    
    
    
    $scope.saveNamespace = function () {
    	
    	 var newNamespace = {};
    	 var userNames = $scope.users;
    	 
    	 newNamespace.qualifier=$scope.qulifier;
    	 newNamespace.usernames=(userNames && userNames.length>0)? userNames.split(','):[];
    	 
    	 Namespace.save(newNamespace, function (result) {
    		 $scope.namespaces.push(result);
    		 $scope.qulifier="";
    		 $scope.users="";
    		 
    		 growl.success('Created namespace "' + newNamespace.qualifier + '"');
                 
             }, function (error) {
            	 growl.error('Failed to create "' + newNamespace.qualifier + '"' + (error && error.data && error.data.message)?error.data.message:error.statusText);
             });
    };
    
    $scope.$watch('searchText', function(newValue, oldValue) {
    	newValue = newValue == null ? "" : newValue;
    	Storage.set("namespace-searchText", newValue);
    });
	
}]);


argusNamespace.factory('Namespace', ['$resource', 'CONFIG',
                                 function ($resource, CONFIG) {
	return $resource(CONFIG.wsUrl + 'namespace/:namespaceId', {}, {
        update: {method: 'PUT'}
    });
	
 }]);



/*
argusNamespace.factory('NamespaceUsers', ['$resource', 'CONFIG',
                                     function ($resource, CONFIG) {
                                         return $resource(CONFIG.wsUrl + 'namespace/:namespaceId/users');
                                     }]);
*/
