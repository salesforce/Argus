/**
 * Created by liuxizi.xu on 12/12/16.
 */

// source: http://stackoverflow.com/questions/19098797/fastest-way-to-flatten-un-flatten-nested-json-objects
'use strict';
/*global angular:false */

angular.module('argus.services.jsonFlatten', [])
.service('JsonFlattenService', function() {
	this.unflatten = function(data) {
		if (Object(data) !== data || Array.isArray(data))
			return data;
		var regex = /\.?([^.\[\]]+)|\[(\d+)\]/g,
			resultholder = {};
		for (var p in data) {
			var cur = resultholder,
				prop = '',
				m;
			while ((m = regex.exec(p))) {
				cur = cur[prop] || (cur[prop] = (m[2] ? [] : {}));
				prop = m[2] || m[1];
			}
			cur[prop] = data[p];
		}
		return resultholder[''] || resultholder;
	};

	this.flatten = function(data) {
		var result = {};
		function recurse (cur, prop) {
			if (Object(cur) !== cur) {
				result[prop] = cur;
			} else if (Array.isArray(cur)) {
				for(var i=0, l=cur.length; i<l; i++)
					recurse(cur[i], prop + '[' + i + ']');
				if (l == 0)
					result[prop] = [];
			} else {
				var isEmpty = true;
				for (var p in cur) {
					isEmpty = false;
					recurse(cur[p], prop ? prop + '.' + p : p);
				}
				if (isEmpty && prop)
					result[prop] = {};
			}
		}
		recurse(data, '');
		return result;
	};
});