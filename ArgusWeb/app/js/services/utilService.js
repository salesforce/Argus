/*global angular:false, copyProperties:false */

angular.module('argus.services.utils', [])
.service('UtilService', [function() {
	'use strict';

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
			if (value instanceof Object || value.length == 0) {
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
		}
	};

	return options;
}]);
