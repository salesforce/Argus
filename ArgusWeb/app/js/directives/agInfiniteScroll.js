'use strict';
/*global angular:false */

/**
 * Created by pfu on 7/3/17.
 */
angular.module('argus.directives')
	.directive('agInfiniteScroll', ['$timeout', function($timeout) {
		return {
			restrict: 'A',
			link : function(scope, element, attributes) {
				var timer;
				var scrollTimeout = 250;
				scope.infiniteScrollDistance = 5;

				element.on('scroll', function(){
					var infiniteScroll = attributes.agInfiniteScroll;
					if(this.scrollTop +  scope.infiniteScrollDistance> this.scrollHeight - this.offsetHeight ){
						$timeout.cancel(timer);
						timer = $timeout(function(){
							eval(infiniteScroll);
						}, scrollTimeout);
					}
				});
			}
		};
	}]);