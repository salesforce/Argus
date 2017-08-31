/**
 * Created by pfu on 3/2/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.agTableService', [])
.service('AgTableService', ['$filter',
	function ($filter) {
		function createSeriesName(metric) {
			var scope = metric.scope;
			var name = metric.metric;
			var tags = createTagString(metric.tags);
			return scope + ':' + name + tags;
		}

		function createTagString(tags) {
			var result = '';
			if (tags) {
				var tagString = '';
				for (var key in tags) {
					if (tags.hasOwnProperty(key)) {
						tagString += (key + '=' + tags[key] + ',');
					}
				}
				if (tagString.length) {
					result += '{';
					result += tagString.substring(0, tagString.length - 1);
					result += '}';
				}
			}
			return result;
		}

		this.setTData = function(data, scope, GMTon) {
			var tData = [];
			if (data && data.length > 0) {
				var allTimestamps = {};
				var i, timestamp;
				for (i in data) {
					var dps = data[i].datapoints;
					for (timestamp in dps) {
						if (!allTimestamps[timestamp]) {
							allTimestamps[timestamp] = [];
						}
					}
				}

				var columns = {'datetime': 'Datetime'};
				for (i in data) {
					dps = data[i].datapoints;
					if (dps) {
						columns['value' + i] = createSeriesName(data[i]);
						for (timestamp in allTimestamps) {
							var values = allTimestamps[timestamp];
							if (dps[timestamp]) {
								values.push(parseFloat(dps[timestamp]));
							} else {
								values.push(undefined);
							}
							allTimestamps[timestamp] = values;
						}
					}
				}
				scope.colNames = columns;
				scope.sortSourceIndices(columns);
				for (timestamp in allTimestamps) {
					var obj = {
						datetime: GMTon? $filter('date')(timestamp, 'M/d/yyyy HH:mm:ss', 'UTC'): $filter('date')(timestamp, 'M/d/yyyy HH:mm:ss'),
						timestamp: timestamp
					};

					var index = 0;
					for (i in columns) {
						if (i !== 'datetime')
							obj[i] = allTimestamps[timestamp][index++];
					}
					tData.push(obj);
				}

				tData.sort(function(a,b){
					return a.timestamp - b.timestamp;
				});
			}
			scope.tData = tData;
		};
	}]);