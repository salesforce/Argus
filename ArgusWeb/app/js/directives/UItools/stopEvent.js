'use strict';
/*global angular:false */

angular.module('argus.directives')
.directive('stopEvent', function () {
	return {
		restrict: 'A',
		link: function (scope, element) {
			element.bind('click', function (e) {
				e.stopPropagation();
			});
		}
	};
});
