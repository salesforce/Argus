angular.module('argus.services.utils', [])
.service('UtilService', [function() {
	'use strict';

	var options = {
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
                result[name] = getParsedValue(value);
                return result;
            } else {
                var property = name.substring(0, index);
                result[property] = constructObjectTree(name.substring(index + 1), value);
                return result;
            }
        }
	};
}]);
