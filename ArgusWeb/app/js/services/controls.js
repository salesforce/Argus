/**
 * Created by pfu on 8/31/16.
 */
'use strict';
/*global angular:false */

angular.module('argus.services')
	.service('Controls', ['$routeParams', function ($routeParams) {
		this.updateControlValue = function(controlName) {
			for (var prop in $routeParams) {
				if (prop == controlName) {
					return $routeParams[prop];
				}
			}
		};

		this.getUrl = function(controls) {
			var urlStr = '';
			// setup url str from all controls values
			for (var i = 0; i < controls.length; i++) {
				urlStr += controls[i].name + '=' + controls[i].value;
				if (i < controls.length - 1) {
					urlStr += '&';
				}
			}
			return urlStr.replace(/\+/g, "%2B");
		};
	}]);