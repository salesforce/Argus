'use strict';
/*global angular:false */

angular.module('argus.directives')
	.directive('agRequest', ['$http', 'Auth',function($http, Auth) {

		function processJS(js){
			if(!js) return undefined;
			return (new Function('return ' + js.trim()))();
		}

		return {
			restrict: 'E',
			scope: {
				url : "@"
			},
			transclude: true,
			template: '<ng-transclude></ng-transclude>',
			link : function(scope, element) {
				var successJS = element.find('success')[0];
				if(successJS){
					successJS = successJS.innerText;
				}
				var errorJS = element.find('error')[0];
				if(errorJS){
					errorJS = errorJS.innerText;
				}
				element.remove();
				var url = scope.url;

				if(!Auth.isUrlValid(url)){
					console.log("Invalid webservice endpoint");
					return;
				}

				$http.get(url).then(
					processJS(successJS),
					processJS(errorJS)
				);

			}
		};

	}]);
