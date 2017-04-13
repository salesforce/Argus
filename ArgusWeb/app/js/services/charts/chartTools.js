/*global angular:false */

angular.module('argus.services.charts.tools', [])
.service('ChartToolService', [function() {
	'use strict';

	var tools = {
		getTimeAxis: function(timeSpan) {
			var hours = [
				'12AM', '1AM', '2AM', '3AM', '4AM', '5AM',
				'6AM', '7AM', '8AM', '9AM', '10AM', '11AM',
				'12PM', '1PM', '2PM', '3PM', '4PM', '5PM',
				'6PM', '7PM', '8PM', '9PM', '10PM', '11PM'
			];
			var axis = [];
			var firstHour = (new Date(timeSpan.begin)).getHours();
			for (var i = 0; i < timeSpan.span; i++) {
				axis.push(hours[(firstHour + i) % 24]);
			}
			axis.push('<b><i>Average</i></b>');
			return axis;
		}
	};

	return tools;
}]);