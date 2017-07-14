/**
 * Created by pfu on 4/10/17.
 */
'use strict';
/*global angular:false, $:false */

angular.module('argus.directives.modals.confirmClick', [])
.directive('confirmClick', ['ConfirmClick', function(ConfirmClick){
	return {
		restrict: 'A',
		replace: false,
		scope: {
			confirmTitle: '@',
			confirmMessage: '@',
			confirmCall: '&',
			confirmDisabled: '='
		},
		controller: ['$scope', '$element', '$uibModal', function ($scope, $element, $uibModal) {
			$element.on('click', function (e) {
				e.stopPropagation();

				// check 'disabled' before opening modal
				if ($scope.confirmDisabled) return;
				ConfirmClick.openConfirmModal(
					$scope.confirmTitle,
					$scope.confirmMessage,
					$scope.confirmCall
				);
			});
		}]
	};
}]);
