/*! Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *  
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *   
 *      Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 *      Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 *      Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
var dashboardServiceModule = angular.module('argusDashboardService', []);

dashboardServiceModule.service('DashboardService', ['$filter', '$compile', '$resource', 'CONFIG', 'VIEWELEMENT', 'Metrics','$sce', '$http','Annotations','growl',
    function ($filter, $compile, $resource, CONFIG, VIEWELEMENT, Metrics, $sce, $http,Annotations,growl) {

        this.getDashboardById = function(dashboardId){
            return $http.get(CONFIG.wsUrl + 'dashboards/' + dashboardId);
        };
        
        this.populateView = function(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope){

            if(metricList && metricList.length>0 && divId) {

                if (metricList && metricList.length > 0) {
                	if(elementType === VIEWELEMENT.chart){
                		populateChart(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope);
                	}else{
                		var metricExpressionList = getMetricExpressionList(metricList);
                    $http({
                        method: 'GET',
                        url: CONFIG.wsUrl + 'metrics',
                        params: {'expression': metricExpressionList}


                    }).
                        success(function(data, status, headers, config){
                        if(data && data.length>0) {
                            $('#' + divId).show();
                            if(elementType === VIEWELEMENT.heatmap)
                                updateHeatmap({}, data, divId, optionList, attributes);
                            else if(elementType === VIEWELEMENT.table)
                            	updateTable(data, scope, divId, optionList);
                        } else {
                            updateChart({}, data, divId, annotationExpressionList, optionList, attributes);
                            growl.info('No data found for the metric expressions: ' + JSON.stringify(metricExpressionList));

                        }
                        }).
                        error(function(data, status, headers, config) {
                        growl.error(data.message);
                        $('#' + divId).hide();
                    });
                }
                } else {
                    growl.error('Valid metric expressions are required to display the chart/table.');
                    $('#' + divId).hide();
                }
            }
        };
        
        function populateChart(metricList, annotationExpressionList, optionList, divId, attributes, elementType, scope){
	         
        	var objMetricCount={};
        	objMetricCount.totalCount=metricList.length;
        	
        	 $('#' + divId).empty();
	         $('#' + divId).show();
	         var series=[];
	       	 var chartType = attributes.type ? attributes.type : 'LINE';
	         var highChartOptions = getOptionsByChartType(config,chartType);
	         setCustomOptions(highChartOptions,optionList);
	         
	         $('#' + divId).highcharts('StockChart', highChartOptions);
	         
	         var chart = $('#' + divId).highcharts('StockChart');
	       	 
	       	 for(var i=0;i<metricList.length;i++){
		       	 var metricExpression = metricList[i].expression;
		       	 var metricOptions=metricList[i].metricSpecicOptions;
		       	populateSeries(metricExpression,metricOptions,highChartOptions,series,divId,annotationExpressionList,objMetricCount);
	       	 }
	       	 //populateAnnotations(annotationExpressionList, chart);
       }
        
        function populateSeries(metricExpression,metricOptions,highChartOptions,series,divId,annotationExpressionList,objMetricCount){
        	 $http({
                 method: 'GET',
                 url: CONFIG.wsUrl + 'metrics',
                 params: {'expression': metricExpression}
             }).
                 success(function(data, status, headers, config){
                 if(data && data.length>0) {
                	 var seriesWithOptions = copySeriesDataNSetOptions(data,metricOptions);
                	 Array.prototype.push.apply(series,seriesWithOptions)
                 } else{
                	 growl.info('No data found for the metric expression: ' + JSON.stringify(metricExpression));
                 }
                 objMetricCount.totalCount=objMetricCount.totalCount-1;
                 
                 if(objMetricCount.totalCount==0){
            		 bindDataToChart(divId,highChartOptions,series,annotationExpressionList);
            	 }
                 }).
                 error(function(data, status, headers, config) {
                	 growl.error(data.message);
                	 objMetricCount.totalCount=objMetricCount.totalCount-1;
                	 if(objMetricCount.totalCount==0){
                		 bindDataToChart(divId,highChartOptions,series,annotationExpressionList);
                	 }
             });
        	
        	
        }
        
        function bindDataToChart(divId,highChartOptions,series,annotationExpressionList){
        		// $("#" + divId).empty();
        		 highChartOptions.series=series;
        		 $('#' + divId).highcharts('StockChart', highChartOptions);
        		 var chart = $('#' + divId).highcharts('StockChart');
        		 populateAnnotations(annotationExpressionList, chart);
        }
       
       function getMetricExpressionList(metrics){
	       	var result = [];
	       	for(var i=0;i<metrics.length; i++){
	       		result.push(metrics[i].expression);
	       	}
	       	return result;
       }
        
        function updateTable(data, scope, divId, options) {
        	if(data && data.length > 0) {
        		
        		var allTimestamps = {};
        		for(var i in data) {
        			var dps = data[i].datapoints;
        			for(var timestamp in dps) {
        				if(!allTimestamps[timestamp]) {
        					allTimestamps[timestamp] = [];
        				}
        			}
        		}
        		
        		var columns = [{title: "timestamp", value: "Timestamp"}];
        		for(var i in data) {
        			var dps = data[i].datapoints;
        			if(dps) {
        				columns.push({
        					title: "value" + i,
        					value: createSeriesName(data[i])
        				});
        				
        				for(var timestamp in allTimestamps) {
            				var values = allTimestamps[timestamp];
            				if(dps[timestamp]) {
            					values.push(parseFloat(dps[timestamp]));
            				} else {
            					values.push(undefined);
            				}
            				allTimestamps[timestamp] = values;
            			}
        			}
        		}
        		
        		var tData = [];
        		for(var timestamp in allTimestamps) {
        			var obj = {
        					timestamp: parseInt(timestamp),
        					date: $filter('date')(timestamp, "medium")
        			};
        			
        			for(var i in columns) {
        				if(columns[i].title !== "timestamp")
        					obj[columns[i].title] = allTimestamps[timestamp][i-1];
        			}
        			tData.push(obj);
        		}
        		
        		var tableConfig = {
        				itemsPerPage: 10,
        			    fillLastPage: true
        		};
        		
        		for(var i in options) {
        			var option = options[i];
        			if(option.name && option.value)
        				tableConfig[option.name] = option.value;
        		}
        		
        		
        		scope.tData = tData;
        		scope.config = tableConfig;
        		
        		var html = '<div style="overflow-x: scroll"><table class="table table-striped table-header-rotated" at-table at-paginated at-list="tData" at-config="config">';
        		
        		html += '<thead>';
        		html += '<tr>';
        		for(var i in columns) {
        			html += '<th class="rotate-45" at-attribute="' + columns[i].title + '"><div><span>' + columns[i].value + '</span></div></th>';
        		}
        		html += '</tr>';
        		html += '</thead>';
        		
        		html += '<tbody>';
        		html += '<tr>';
        		
        		for(var i in columns) {
        			if(columns[i].title === 'timestamp')
        				html += '<td at-sortable at-attribute="' + columns[i].title + '">{{ item.date }}</td>';
        			else
        				html += '<td at-sortable at-attribute="' + columns[i].title + '">{{ item.' + columns[i].title + '}}</td>';
        		}
        		
        		html += '</tr>';
        		html += '</tbody>';
        		
        		html += '</table></div>';
        		
        		html += '<at-pagination at-list="tData" at-config="config"></at-pagination>';
        		
        		$("#" + divId).empty();
        		$compile($("#" + divId).prepend(html))(scope);
        		
        	}
        };

        function updateChart(config, data, divId, annotationExpressionList, optionList, attributes) {

            var chartType = attributes.type ? attributes.type : 'LINE';
            if(data && data.length>0) {
                var options = getOptionsByChartType(config,chartType);
                options.series = copySeries(data);
                //options.chart={renderTo: 'container',defaultSeriesType: 'line'};
                setCustomOptions(options,optionList);
                $('#' + divId).highcharts('StockChart', options);
            } else {
                $('#' + divId).highcharts('StockChart', getOptionsByChartType(config, chartType));
            }
            var chart = $('#' + divId).highcharts('StockChart');
            //chart.chart={renderTo: 'container',defaultSeriesType: 'line'};
            //chart.renderTo='container';
            //chart.defaultSeriesType='line';
            
            populateAnnotations(annotationExpressionList, chart);
        };
        function resetChart(chart){
            chart.zoomOut();
        };

        
        function getOptionsByChartType(config, chartType){
            var options = config ? angular.copy(config) : {};
            options.legend = {
                enabled: true,
                maxHeight: 62,
                itemStyle: {
                    fontWeight: 'normal',
                    fontSize: '10px'
                },
                navigation : {
                    style : {
                        fontWeight: 'normal',
                        fontSize: '10px'
                    }
                }
            };
            options.credits = {enabled: false};
            options.rangeSelector = {selected: 1, inputEnabled: false};
            options.xAxis = {
            	type: 'datetime',
            	ordinal: false
            };
            options.lang = {noData: 'No Data to Display'};
            if(chartType && chartType.toUpperCase() === 'AREA'){
                options.plotOptions = {series: {animation: false}};
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
            }else  if(chartType && chartType.toUpperCase() === 'STACKAREA'){
                options.plotOptions = {
                    area: {
                        stacking: 'normal',
                       // lineWidth: 1.5,
                        dataGrouping: {
                            enabled: true//,
                          //  groupPixelWidth: 2
                        },
                        animation: false,
                        marker: {
                            enabled: false
                        }
                    }
                };
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
            }
            else {
                options.plotOptions = {series: {animation: false}};
                options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
            }
            return options;
        };

        function updateHeatmap(config, data, divId, optionList, attributes) {
            if(data && data.length>0) {
                var top = attributes.top? parseInt(attributes.top) : data.length;
                var options = getOptionsByHeatmapType(config, top);
                data.sort(compareAverage);
                data = data.slice(0, Math.min(top, data.length));
                var orgAxis = data.map(createSeriesName);
                var timeSpan = getTimeSpan(data);
                var timeAxis = getTimeAxis(timeSpan); 
                var dataSeries = copyHeatmapSeries(data, timeSpan); 
                options.series[0].data = dataSeries;
                options.xAxis.categories = timeAxis;
                options.yAxis.categories = orgAxis.reverse();
                setCustomOptions(options,optionList);
                $('#' + divId).highcharts(options);
            }else {
                $('#' + divId).highcharts('StockChart', getOptionsByChartType(config, 'LINE'));
            }
        }
        
        function resetHeatmap(heatmap){
            
        }
        
        function getOptionsByHeatmapType(config, top){
            var options = config ? angular.copy(config) : {};
            options.credits = {enabled: false};
            options.chart = {
                type: 'heatmap',
                marginTop: 0,
                marginBottom: 60,
                height: 40 * top
            };
            options.title = {text: ''};
            options.xAxis = {
                categories: null
            };
            options.yAxis = {
                categories: null,
                title: null,
                labels: {
                }
            };
            options.colorAxis = {
                dataClasses: [{
                    from: 0,
                    to: 300,
                    color: '#00FF00'
                },{
                    from:300,
                    to:400,
                    color:'#FF8000'
                },{
                    from:400,
                    color:'#FF0040'
                }]
            };
            options.legend = {enabled: true};
            options.tooltip = {enabled: false};
            options.series = [{
                name: '',
                borderWidth: 1,
                data: null,
                dataLabels: {
                    enabled: true,
                    color: 'black',
                    style: {
                        textShadow: 'none',
                        HcTextStroke: null
                    }
                }
            }];
            return options;
        }
        
        function compareAverage(a,b) {
            if (getAverage(a) < getAverage(b)) return 1;
            if (getAverage(a) > getAverage(b)) return -1;
            return 0;
        }
        
        function getTimeSpan(data) {
            var begin = 9999999999999;
            var end = 0;
            for (var i = 0; i < data.length; i++) {
                for (var time in data[i].datapoints) {
                    begin = Math.min(begin, parseInt(time));
                    end = Math.max(end, parseInt(time));
                }
            }
            var span = Math.floor(end/1000/60/60) - Math.floor(begin/1000/60/60) + 1;
            return {begin: begin, end: end, span: span};
        }
        
        function getTimeAxis(timeSpan) {
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
        
        function getAverage(data) {
            var total = 0;
            var count = 0;
            for (var time in data.datapoints) {
                total += parseInt(data.datapoints[time]);
                count += 1;
            }
            if (count > 0)
                return total / count;
            else
                return 0;
        }
        
        function getHourlyAverage(timeSpan, data) {
            var sums = Array.apply(null, Array(timeSpan.span)).map(Number.prototype.valueOf,0);
            var counts = Array.apply(null, Array(timeSpan.span)).map(Number.prototype.valueOf,0);
            var pivotHour = Math.floor(timeSpan.begin / 1000 / 60 / 60);
            for (var time in data.datapoints) {        
                var hour = Math.floor(parseInt(time) / 1000 / 60 / 60);
                sums[hour - pivotHour] += parseInt(data.datapoints[time]);
                counts[hour - pivotHour] += 1;
            }
            var avgs = [];
            for (var i = 0; i < timeSpan.span; i++) {
                if (counts[i] > 0) avgs.push(sums[i] / counts[i]);
                else avgs.push(null);
            }
            return avgs;
        }
        
        function copyHeatmapSeries(data, timeSpan) {
            var table = data.map(getHourlyAverage.bind(null, timeSpan));
            for (var i = 0; i < data.length; i++) {
                table[i].push(getAverage(data[i]));
            }
            var dataSeries = [];
            for (var i = 0; i < data.length; i++) {
                for (var j = 0; j < table[0].length; j++) {
                    var intValue = table[data.length - 1 - i][j] ? Math.floor(table[data.length - 1 - i][j]) : null;
                    dataSeries.push([j, i, intValue]);
                }
            }
            return dataSeries;
        }

        function copySeries(data) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                	var series = [];
                	for(var key in data[i].datapoints) {
                		var timestamp = parseInt(key);
                		if(data[i].datapoints[key] !=null){
                			var value = parseFloat(data[i].datapoints[key]);
                			series.push([timestamp, value]);
                		}
                	}
                    result.push({name: createSeriesName(data[i]), data: series});
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        };
        
        function copySeriesDataNSetOptions(data, metricOptions) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                	var series = [];
                	for(var key in data[i].datapoints) {
                		var timestamp = parseInt(key);
                		if(data[i].datapoints[key] !=null){
                			var value = parseFloat(data[i].datapoints[key]);
                			series.push([timestamp, value]);
                		}
                	}
                	var objSeries = {name: createSeriesName(data[i]), data: series};
                	var objSeriesWithOptions=setCustomOptions(objSeries, metricOptions);
                    result.push(objSeriesWithOptions);
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        };
        
        function createSeriesName(metric) {
            var scope = metric.scope;
            var name = metric.metric;
            var tags = createTagString(metric.tags);
            return scope + ':' + name + tags;
        };

        function createTagString(tags) {
            var result = '';
            if (tags) {
                var tagString ='';
                for (var key in tags) {
                    if (tags.hasOwnProperty(key)) {
                        tagString += (key + '=' + tags[key] + ',');
                    }
                }
                if(tagString.length) {
                    result += '{';
                    result += tagString.substring(0, tagString.length - 1);
                    result += '}';
                }
            }
            return result;
        };

        function populateAnnotations(annotationsList, chart){
            if (annotationsList && annotationsList.length>0 && chart) {
                for (var i = 0; i < annotationsList.length; i++) {
                    addAlertFlag(annotationsList[i],chart);
                }
            }
        };

        function addAlertFlag(annotationExpression,chart) {
            Annotations.query({expression: annotationExpression}, function (data) {
                if(data && data.length>0) {
                    var forName = createSeriesName(data[0]);
                    var series = copyFlagSeries(data);
                    series.linkedTo = forName;

                    for(var i=0;i<chart.series.length;i++){
                        if(chart.series[i].name == forName){
                            series.color = chart.series[i].color;
                            break;
                        }
                    }

                    chart.addSeries(series);
                }
            });
        };

        function copyFlagSeries(data) {
            var result;
            if (data) {
                result = {type: 'flags', shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2};
                result.data = [];
                for (var i = 0; i < data.length; i++) {
                    var flagData = data[i];
                    result.data.push({x: flagData.timestamp, title: 'A', text: formatFlagText(flagData.fields)});
                }
            } else {
                result = null;
            }
            return result;
        };

        function formatFlagText(fields) {
            var result = '';
            if (fields) {
                for (var field in fields) {
                    if (fields.hasOwnProperty(field)) {
                        result += (field + ': ' + fields[field] + '<br/>');
                    }
                }
            }
            return result;
        };



        function setCustomOptions(options,optionList){
          for(var idx in optionList) {
                var propertyName = optionList[idx].name;
                var propertyValue = optionList[idx].value;
                var result = constructObjectTree(propertyName, propertyValue);
                copyProperties(result,options);
            }
            return options;
        }

        function copyProperties(from,to){
            for (var key in from) {
                if (from.hasOwnProperty(key)) {
                    if(!to[key] || typeof from[key] == 'string' || from[key] instanceof String ){//if from[key] is not an object and is last property then just copy so that it will overwrite the existing value
                        to[key]=from[key];
                    }else{
                        copyProperties(from[key],to[key]);
                    }
                }
            }
        }

        //It constructs the object tree.
        function constructObjectTree(name, value) {
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
        };
        
        function getParsedValue(value){
        	
        	if(value instanceof Object || value.length==0){
        		return value;
        	}

        	if(value=='true'){
        		return true;
       		}else if(value=='false'){
       			return false;
       		}else if(!isNaN(value)){
       			return parseInt(value);
       		}
        	return value;
        }

    }]);

dashboardServiceModule.factory('Metrics', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'metrics', {}, {
            query: {method: 'GET', isArray: true}
        });
    }]);

dashboardServiceModule.factory('Annotations', ['$resource', 'CONFIG',
    function ($resource, CONFIG) {
        return $resource(CONFIG.wsUrl + 'annotations', {}, {
            query: {method: 'GET', isArray: true}
        });
    }]);

dashboardServiceModule.service('Tags', ['CONFIG', '$http', '$q', function(CONFIG, $http, $q) {
	
	this.getDropdownOptions = function(key) {
		var request = $http({
			method: 'GET',
			url: CONFIG.wsUrl + 'schema/tags',
			params: {
				tagk: key
			}
		});
		
		return request;
	}
	
}]);
