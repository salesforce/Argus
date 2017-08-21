'use strict';
/*global angular:false, $:false */

angular.module('argus.directives.controls.dashboard', [])
.directive('agDashboard', [function() {
	var chartOptionsModal = angular.module('argus.directives')
		.directive('ChartOptionsModal', function () {
			return {
				scope: {
					options: '='
				},
				controller: ['$scope', '$element', '$modal', function ($scope, $element, $modal) {
					$scope.openChartOptionsModal = function () {

						var chartOptions = $modal.open({
							templateUrl: 'js/templates/modals/chartOptions.html',
							windowClass: 'argusModal chartOptions',
							controller: ['$scope', function ($scope) {
								// Attach details to scope so they can be rendered in the modal
								// close modal window
								$scope.close = function () {
									chartOptions.close();
								};
							}]
						});

						// add lightMask class when modal is opened
						chartOptions.opened.then(function () {
							$('body').addClass('lightMask');
						});

						// remove lightMask class when modal is closed
						chartOptions.result.then(function () {
							angular.noop();
						}, function () {
							$('body').removeClass('lightMask');
						});
					};

					$element.on('click', function () {
						$scope.openChartOptionsModal($scope.options);
					});
				}]
			};
		});

	chartOptionsModal.$inject = ['$modal'];
}]);
