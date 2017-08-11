/*global angular:false, copyProperties:false */
'use strict';
angular.module('argus.services.utils', [])
.service('UtilService', ['$filter', function($filter) {
	var options = {
		assignController: function(controllers) {
			if (!controllers) return;
			for (var i=0; i < controllers.length; i++) {
				if (controllers[i])
					return controllers[i];
			}
		},

		copyProperties: function(from, to) {
			for (var key in from) {
				if (from.hasOwnProperty(key)) {
					//if from[key] is not an object and is last property then just copy so that it will overwrite the existing value
					if (!to[key] || typeof from[key] == 'string' || from[key] instanceof String ) {
						to[key] = from[key];
					} else {
						copyProperties(from[key],to[key]);
					}
				}
			}
		},

		constructObjectTree: function(name, value) {
			var result = {};
			var index = name.indexOf('.');

			if (index == -1) {
				result[name] = this.getParsedValue(value);
				return result;
			} else {
				var property = name.substring(0, index);
				result[property] = this.constructObjectTree(name.substring(index + 1), value);
				return result;
			}
		},

		getParsedValue: function(value) {
			if (value instanceof Object || value.length === 0) {
				return value;
			}
			if (value == 'true') {
				return true;
			} else if (value == 'false') {
				return false;
			} else if (!isNaN(value)) {
				return parseInt(value);
			}
			return value;
		},

		cssNotationCharactersConverter: function (name) {
			return name.replace( /(:|\.|\[|\]|,|=|@)/g, '\\$1' );
		},

		trimMetricName: function (metricName, menuOption) {
			if (!metricName) return;

			return $filter('truncateMetricName')(metricName, menuOption);
		},

		validNumberChecker: function (num) {
			return isFinite(num)? num: 0;
		},

		capitalizeString: function (string) {
			return string.charAt(0).toUpperCase() + string.slice(1);
		},

		epochTimeMillisecondConverter: function (timestampNum) {
			// sometimes epoch time is in second instead of milisecond
			// http://stackoverflow.com/questions/23929145/how-to-test-if-a-given-time-stamp-is-in-seconds-or-milliseconds
			if (timestampNum.toString().length < 12) {
				return timestampNum * 1000;
			} else {
				return timestampNum;
			}
		},

		alphabeticalSort: function(a, b) {
			var textA = a.name.toUpperCase();
			var textB = b.name.toUpperCase();
			return (textA < textB) ? -1 : (textA > textB) ? 1 : 0;
		},

		objectWithoutProperties: function (obj, keys) {
			var target = {};
			for (var i in obj) {
				if (keys.indexOf(i) >= 0) continue;
				if (!Object.prototype.hasOwnProperty.call(obj, i)) continue;
				target[i] = obj[i];
			}
			return target;
		}
	};
	return options;
}]);
