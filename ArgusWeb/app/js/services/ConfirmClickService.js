/**
 * Created by pfu on 6/8/17.
 */
'use strict';
/*global angular:false, jwt_decode:false, $:false */

angular.module('argus.services.confirmClick', [])
	.service('ConfirmClick', ['$uibModal', function($uibModal){
		this.openConfirmModal = function (confirmTitle, confirmMessage, confirmCall, cancelCall) {
			var confirmModal = $uibModal.open({
				templateUrl: 'js/templates/modals/confirmClick.html',
				windowClass: 'confirmClick',
				controller: ['$scope', '$sce', function ($scope, $sce) {
					$scope.toTrustedHTML = function( html ){
						return $sce.trustAsHtml( html );
					};
					// Attach details to scope so they can be rendered in the modal
					$scope.confirmTitle = confirmTitle;
					$scope.confirmMessage = confirmMessage;
					$scope.confirmCall = confirmCall;
					$scope.cancelCall = cancelCall;
					$scope.confirm = function () {
						if($scope.confirmCall){
							$scope.confirmCall();
						}
						$scope.close();
					};
					$scope.close = function () {
						if($scope.cancelCall){
							$scope.cancelCall();
						}
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
	}]);
