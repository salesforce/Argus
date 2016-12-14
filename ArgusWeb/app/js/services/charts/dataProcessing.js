'use strict';

angular.module('argus.services.charts.dataProcessing', [])
.service('ChartDataProcessingService', ['ChartOptionService', 'Annotations', 'JsonFlattenService', function(ChartOptionService, Annotations, JsonFlattenService) {
    'use strict';

    // Private methods
    function copySeries(data) {
        var result = [];
        if (data) {
            for (var i = 0; i < data.length; i++) {
                var series = [];
                for (var key in data[i].datapoints) {
                    var timestamp = parseInt(key);
                    if (data[i].datapoints[key] != null) {
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
    }

    function createSeriesName(metric) {
        var scope = metric.scope;
        var name = metric.metric;
        var tags = createTagString(metric.tags);
        return scope + ':' + name + tags;
    }

    function createTagString(tags) {
        var result = '';
        if (tags) {
            var tagString ='';
            for (var key in tags) {
                if (tags.hasOwnProperty(key)) {
                    tagString += (key + '=' + tags[key] + ',');
                }
            }
            if (tagString.length) {
                result += '{' + tagString.substring(0, tagString.length - 1) + '}';
            }
        }
        return result;
    }

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
    }

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
    }

    // Public Service methods
    var service = {
        getLastDataPoint: function(datapoints) {
            if (!datapoints) return;
            return datapoints[Object.keys(datapoints).sort().reverse()[0]];
        },

        getMetricSpecificOptionsInArray: function(metricSpecificOptions) {
            var options = [];
            for (var key in metricSpecificOptions) {
                if (metricSpecificOptions.hasOwnProperty(key)) {
                    options.push({'name': key, 'value': metricSpecificOptions[key]});
                }
            }
            return options;
        },

        augmentExpressionWithControlsData: function(event, expression, controls) {
			var result = expression;

            for (var controlIndex in controls) {
                var controlName = '\\$' + controls[controlIndex].name + '\\$';
                var controlValue = controls[controlIndex].value;
                var controlType = controls[controlIndex].type;
                if ( controlType === "agDate" ) {
                    controlValue = isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
                    // remove GMT from offset input from
                    if( typeof (controlValue) === "string" && controlValue.indexOf('GMT') >= 0){
                        controlValue = controlValue.replace('GMT','');
                    }
                }
                controlValue = controlValue === undefined ? "" : controlValue;
                result = result.replace(new RegExp(controlName, "g"), controlValue);
            }

            result = result.replace(/(\r\n|\n|\r|\s+)/gm, "");
            return result;
        },

        processMetricData: function(data, event, controls) {
			if (!data) return;

			var processedData = [];
            var updatedMetricList = [];
            var updatedAnnotationList = [];
            var updatedOptionList = JsonFlattenService.unflatten(data.options);
            for (var key in data.metrics) {
                if (data.metrics.hasOwnProperty(key)) {

                    // get metricExpression, and name & color attributes from data
                    var metrics = data.metrics[key];
                    var metricExpression = metrics.expression;
                    var metricSpecificOptions = metrics.metricSpecificOptions;
                    var processedExpression = this.augmentExpressionWithControlsData(event, metricExpression, controls);

                    if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
                        var processedMetric = {};
                        processedMetric['expression'] = processedExpression;
                        processedMetric['name'] = metrics.name;
                        processedMetric['color'] = metrics.color;
                        processedMetric['metricSpecificOptions'] = this.getMetricSpecificOptionsInArray(metricSpecificOptions);

                        // update metric list with new processed metric object
                        updatedMetricList.push(processedMetric);
                    }
                }
            }

            for (var key in data.annotations) {
                if (data.annotations.hasOwnProperty(key)) {
                    var processedExpression = this.augmentExpressionWithControlsData(event, data.annotations[key],controls);
                    if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
                        updatedAnnotationList.push(processedExpression);
                    }
                }
            }

            if (updatedMetricList.length > 0) {
        		processedData = {
            		updatedMetricList: updatedMetricList,
            		updatedAnnotationList: updatedAnnotationList,
            		updatedOptionList: updatedOptionList
        		};

				return processedData;
            }
        },

        copySeriesDataNSetOptions: function(data, metricItem) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                    var series = [];

                    for (var key in data[i].datapoints) {
                        var timestamp = parseInt(key);
                        if (data[i].datapoints[key] != null) {
                            var value = parseFloat(data[i].datapoints[key]);
                            series.push([timestamp, value]);
                        }
                    }

                    var metricName = (metricItem.name) ? metricItem.name : createSeriesName(data[i]);
                    var metricColor = (metricItem.color) ? metricItem.color : null;
                    var objSeries = {
                        name: metricName,
                        color: metricColor,
                        data: series
                    };
                    var objSeriesWithOptions = ChartOptionService.setCustomOptions(objSeries, metricItem.metricSpecificOptions);

                    result.push(objSeriesWithOptions);
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        },

        populateAnnotations: function(annotationsList, chart) {
            if (annotationsList && annotationsList.length > 0 && chart) {
                for (var i=0; i < annotationsList.length; i++) {
					this.addAlertFlag(annotationsList[i], chart);
                }
            }
        },

        addAlertFlag: function(annotationExpression, chart) {
			Annotations.query({expression: annotationExpression}, function (data) {
                if (data && data.length > 0) {
                    var forName = createSeriesName(data[0]);
                    var series = copyFlagSeries(data);
                    series.linkedTo = forName;

                    for (var i=0; i < chart.series.length; i++) {
                        if (chart.series[i].name == forName) {
                            series.color = chart.series[i].color;
                            break;
                        }
                    }

                    chart.addSeries(series);
                }
            }, function (error) {
                console.log( 'no data found', error.data.message );
            });
        },

        createSeriesName: createSeriesName,

        copyFlagSeries: copyFlagSeries
    };

    return service;
}]);
