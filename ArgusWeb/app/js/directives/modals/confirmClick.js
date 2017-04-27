/**
 * Created by pfu on 4/10/17.
 */
'use strict';
/*global angular:false, $:false */

angular.module('argus.directives.modals.confirmClick', [])
.directive('confirmClick', [function(){
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

			$scope.openConfirmModal = function (confirmTitle, confirmMessage, confirmCall) {
				var confirmModal = $uibModal.open({
					templateUrl: 'js/templates/modals/confirmClick.html',
					windowClass: 'confirmClick',
					controller: ['$scope', function ($scope) {
						// Attach details to scope so they can be rendered in the modal
						$scope.confirmTitle = confirmTitle;
						$scope.confirmMessage = confirmMessage;
						$scope.confirmCall = confirmCall;
						$scope.confirm = function () {
							$scope.confirmCall();
							$scope.close();
						};
						$scope.close = function () {
							confirmModal.close();
						};
					}]
				});
				// add lightMask class when modal is opened
				confirmModal.opened.then(function () {
					$('body').addClass('lightMask');
				});

				// remove lightMask class when modal is closed
				confirmModal.result.then(function () {
					angular.noop();
				}, function () {
					$('body').removeClass('lightMask');
				});
			};

			$element.on('click', function (e) {
				e.stopPropagation();

				// check 'disabled' before opening modal
				if ($scope.confirmDisabled) return;
				$scope.openConfirmModal(
					$scope.confirmTitle,
					$scope.confirmMessage,
					$scope.confirmCall
				);
			});
		}]
	};
}]);
