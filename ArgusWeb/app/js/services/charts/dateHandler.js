/**
 * Created by liuxizi.xu on 11/3/16.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.charts.dateHandler', [])
.service('DateHandlerService', function () {
	this.timeProcessingHelper = function (timeValue) {
		var result;
		if (timeValue[0] === '-') {
			// apply offset to current time
			timeValue = timeValue.toLowerCase().trim();
			var offsetValue = parseInt(timeValue.substring(1, timeValue.length - 1));
			var offsetUnit = timeValue[timeValue.length - 1];
			result = new Date();
			switch (offsetUnit) {
				case 's':
					result = result.setSeconds(result.getSeconds() - offsetValue);
					break;
				case 'm':
					result = result.setMinutes(result.getMinutes() - offsetValue);
					break;
				case 'h':
					result = result.setHours(result.getHours() - offsetValue);
					break;
				case 'd':
					result = result.setDate(result.getDate() - offsetValue);
					break;
			}
			return new Date(result);
		} else {
			// convert timepicker string to Date object
			result = new Date(timeValue);
			return result.toString() === 'Invalid Date' ? new Date() : result;
		}
	};

	this.GMTVerifier = function(timeValue) {
		// true if offset and string with GMT are used for input
		return (timeValue.indexOf('-') !== -1) || (timeValue.indexOf('GMT') !== -1);
	};

	// assuming series' data is sorted already
	this.getStartTimestamp = function(series) {
		var allStartTimestamp = series.map(function(item) {
			// error checking: data field can be empty
			if (item.data && item.data.length > 0) {
				return item.data[0][0];
			}
		});
		return Math.min.apply(null, allStartTimestamp);
	};

	this.getEndTimestamp = function(series) {
		var allStartTimestamp = series.map(function(item) {
			// error checking: data field can be empty
			if (item.data && item.data.length > 0) {
				return item.data[item.data.length - 1][0];
			}
		});
		return Math.max.apply(null, allStartTimestamp);
	};
});
