/**
 * Created by pfu on 3/2/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.agTableService', [])
.service('AgTableService', ['$filter', 'ChartDataProcessingService',
	function ($filter, ChartDataProcessingService) {
        this.numOfBaseLine = 2;
        this.baseHeight = 60;
        this.numOfLettersPerline = 22;
        this.heightPerLine = 19;
        this.TopLeftStringDatetime = "Datetime / <br> Metric Sources";
        this.TopLeftStringTags = "Tags / <br> Metric & Scopes";
        var self = this;

        this.getDateTime = function(timestamp, GMTOn){
            return  GMTOn? $filter('date')(timestamp, 'M/d/yyyy HH:mm:ss', 'UTC'): $filter('date')(timestamp, 'M/d/yyyy HH:mm:ss')
        };


        this.processResults = function(scope){
            if(scope.results.length === 1){
                scope.oneRow = true;
                scope.results = self.setTDataWithFirstColumnAsTags(scope.results[0], scope);
                scope.colNames = scope.colNamesScopeMetric;
                scope.sort(scope.colNames);
                scope.topLeftString = self.TopLeftStringTags;
            }else{
                if(scope.oneRow){
                    scope.oneRow = false;
                    scope.colNames = scope.colNamesSources;
                    scope.results = scope.tData;
                    scope.sort(scope.colNames);
                    scope.topLeftString = self.TopLeftStringDatetime;
                }
            }
        };

        function findMaxLength(item){
            var maxLength= 0;
            for(var i in item){
                if(item[i] && item[i].length > maxLength) {
                    maxLength = item[i].length;
                }
            }
            return maxLength;
        }

        this.processRowHeight = function(item){
            var maxLength = findMaxLength(item);
            var numOfLine =  Math.ceil(maxLength/self.numOfLettersPerline);
            if(numOfLine <= self.numOfBaseLine){
                return self.baseHeight;
            }else{
                return self.baseHeight + (numOfLine - self.numOfBaseLine) * self.heightPerLine;
            }
        };

        this.setTDataWithFirstColumnAsTags = function(data, scope) {
			//this data is a row in the tData
			var temp = {};
			var scopeMetricSet = new Set();
			for(var i in scope.colNamesSources){
				if(i === "firstCol") continue;
				var metric = scope.seriesNameToMetricMap[scope.colNamesSources[i]];
				scopeMetricSet.add(metric.scopeMetric);
				if(!temp[metric.tags]) {
                    temp[metric.tags] = {};
                }
                temp[metric.tags][metric.scopeMetric] = data[i];
			}

			var result = [];
			scope.colNamesScopeMetric = {firstCol: 'tags'};

			var index = 0;
			for(i of scopeMetricSet){
				scope.colNamesScopeMetric['value' + index++] = i;
			}

			for(i in temp) {
            	var temp2 = {firstCol: i};
            	index = 0;
            	for(var j in scope.colNamesScopeMetric){
            		if(j === 'firstCol') continue;
					temp2['value' + index++] = temp[i][scope.colNamesScopeMetric[j]];
				}
            	result.push(temp2);
			}
			return result;
		};

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

				var columns = {'firstCol': 'Datetime'};
                scope.seriesNameToMetricMap = {};
				for (i in data) {
					dps = data[i].datapoints;
					if (dps) {
						columns['value' + i] = ChartDataProcessingService.createSeriesName(data[i]);
						scope.seriesNameToMetricMap[columns['value' + i]] = ChartDataProcessingService.createMetricWithScopeMetricAndTags(data[i]);
						}
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
				scope.colNamesSources = columns;
				scope.sortSourceIndices(columns);
				for (timestamp in allTimestamps) {
					var obj = {
						dateTime: this.getDateTime(timestamp, GMTon),
						firstCol: timestamp
					};

					var index = 0;
					for (i in columns) {
						if (i !== 'firstCol')
							obj[i] = allTimestamps[timestamp][index++];
					}
					tData.push(obj);
				}

				tData.sort(function(a,b){
					return a.firstCol - b.firstCol;
				});

			scope.tData = tData;
		};
	}]);