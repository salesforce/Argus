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
			//There are two display modes based on the results
            if(scope.results.length === 1){
				//Only one timestamp, convert tData and let each row header be the tags, column header be scope+metric
                scope.oneRow = true;
                scope.results = self.setTDataWithFirstColumnAsTags(scope.results[0], scope);
                scope.colNames = scope.colNamesScopeMetric;
                scope.sort(scope.colNames);
                scope.topLeftString = self.TopLeftStringTags;
            }else{
				//normal mode as show tData
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
			//this data is a row in the tData, because the result contains only one row.
			var temp = {};
			var scopeMetricSet = new Set();
			/** scope.colNamesSources is original colNames:
			 *  {
			 * 	  firstCol: DateTime
			 *    value0: name combined of scope+metric+tags
			 *    valule1: ...
			 *    ...	
			 *  } 
			 *  the colNamesSources now consists of scope, metric, tags, tags will be taken out as row name
			**/
			for(var i in scope.colNamesSources){
				if(i === "firstCol") continue;
				var metric = scope.seriesNameToMetricMap[scope.colNamesSources[i]];
				scopeMetricSet.add(metric.scopeMetric);
				if(!temp[metric.tags]) {
                    temp[metric.tags] = {};
                }
                temp[metric.tags][metric.scopeMetric] = data[i];
			}
			/**
			 *  temp is an obj like:
			 *  {
			 *    tag0:{
			 * 			 scopeMetric0: value
			 *           scopeMetric1: value  
			 *    	   }
			 *    tag1:...,
			 *    ...
			 *  }
			 */

			var result = [];
			scope.colNamesScopeMetric = {firstCol: 'tags'};

			var index = 0;
			for(i of scopeMetricSet){
				scope.colNamesScopeMetric['value' + index++] = i;
			}
			/**
			 *  scope.colNamesScopeMetric is an obj like:
			 *  {
			 *   firstCol: 'tags'
			 *   value0: scopeMetric0,
			 *   value1: scopeMetric1,
			 *   ...
			 *  }
			 * 
			 *  result is an arraylike
			 *  [
			 *    {
			 *      fisrtCol: tag 0
			 * 	    value0: value00
			 * 	    value1: value01
			 *      ...
			 *    },
			 *    ...
			 *  ]
			 */

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
				/**
				*	allTimestamps is an object: 
				*	{
				*		t0: [series0's datapoint value at t0, series1's datapoint value at t1, ...]
				*		t1: [...]	
				*	}
				**/
				var columns = {'firstCol': 'Datetime'};
				scope.seriesNameToMetricMap = {};
				
				/** The series (with indentical scope+metric+tag combination) in data could be (s0,s1,s2,s0,s3)
				 *  Therefore, we need a map to store the unique series and its index
				 *  For example, the 3th element s0 should has index 0 instead of 3,
				 *  and the 4th element s3 should has index 3 instead of 5
				 **/ 
				var seriesNameToIndexMap = {};
				var offset = 0; 
				for (i in data) {
					dps = data[i].datapoints;
					if (dps) {
						//sometimes two or more series from data can have the same scope+metric+tag combination
						var seriesName = ChartDataProcessingService.createSeriesName(data[i]);
						var seriesIndex = seriesNameToIndexMap[seriesName]
						var adjustedIndex = i - offset;
						if(seriesIndex === undefined){
							//this scope+metric+tag combination is new
							columns['value' + adjustedIndex] = ChartDataProcessingService.createSeriesName(data[i]);
							scope.seriesNameToMetricMap[seriesName] = ChartDataProcessingService.createMetricWithScopeMetricAndTags(data[i]);
							seriesNameToIndexMap[seriesName] = adjustedIndex;
							//fill in the allTimestamps
							for (timestamp in allTimestamps) {
								var values = allTimestamps[timestamp];
								if (dps[timestamp]) {
									values.push(parseFloat(dps[timestamp]));
								} else {
									//use undefined if a value if missed at the timestamp
									values.push(undefined);
								}
								allTimestamps[timestamp] = values;
							}
						}else{
							offset++;
							//the scope+metric+tag combination of this series is more than one
							//fill in the allTimestamps, insert to right index in each array
							for (timestamp in allTimestamps) {
								var values = allTimestamps[timestamp];
								if (dps[timestamp]) {
									//values[seriesIndex] should be recored as undefined before
									values[seriesIndex] = parseFloat(dps[timestamp]);
								}
								allTimestamps[timestamp] = values;
							}
						}
					}
				}

                scope.colNames = columns;
				scope.colNamesSources = columns;
				scope.sortSourceIndices(columns);//first, sort base on column headers

				/**
				 *  Transfer the allTimestamp obj to tData:
				 *  [
				 * 	  {
				 * 	  	dateTime: Date0
				 *      firstCol: timestamp0 (interger)
				 *      value0: value from series0 at timestamp0
				 *      value1: ...
				 *    },
				 *    {
				 * 	  	dateTime: Date1
				 *      firstCol: timestamp1 (interger)
				 *      value0: value from series0 at timestamp1
				 *      value1: ...
				 *    },
				 *  ]
				 */
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
				//sort based on timestamp	
				tData.sort(function(a,b){
					return a.firstCol - b.firstCol;
				});
			}
			scope.tData = tData;
		};
	}]);