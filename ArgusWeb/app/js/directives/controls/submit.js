'use strict';
/*global angular:false */

angular.module('argus.directives.controls.submit', [])
.directive('agSubmit', ['$http', function($http) {
	return {
		restrict: 'E',
		scope: {
			controlName: '@name',
			elemId: '@id',
			cssName: '@class',
			style: '@style',
			size: '@size'
		},
		require: '^agDashboard',
		template: '',
		link: function(scope, element, attributes, dashboardCtrl) {
			var buttonName = 'Submit';
			if (element.text() && element.text().length > 0) {
				buttonName = element.text();
			}

			element.html('<button id='+ scope.elemId +' class="btn btn-primary btn-md '+ scope.cssName +'" size='+ scope.size +' style='+ scope.style +'>' + buttonName + '</button>');

			element.on('click', function() {
				$http.pendingRequests = []; //This line should be deleted.
				dashboardCtrl.broadcastEvent(dashboardCtrl.getSubmitBtnEventName(), dashboardCtrl.getAllControls());
			});
		}
	};
}]);