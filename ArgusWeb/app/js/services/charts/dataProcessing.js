'use strict';
/*global angular:false */

angular.module('argus.services.charts.dataProcessing', [])
.service('ChartDataProcessingService', ['ChartOptionService', 'Annotations', 'JsonFlattenService', function(ChartOptionService, Annotations, JsonFlattenService) {
	// Private methods
	function copySeries(data) {
		var result = [];
		if (data) {
			for (var i = 0; i < data.length; i++) {
				var series = [];
				for (var key in data[i].datapoints) {
					var timestamp = parseInt(key);
					if (data[i].datapoints[key] !== null) {
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

	function createMetricWithScopeMetricAndTags(metric){
		return {
			scopeMetric: metric.scope + ':' + metric.metric,
			tags: createTagString(metric.tags)
		};
	}

	function createSeriesName(metric) {
		if (metric.displayName !== null && metric.displayName !== undefined) {
			return metric.displayName;
		}

		var scope = metric.scope ? metric.scope: '';
		var name = metric.metric ? metric.metric : '';
		var tags = createTagString(metric.tags);
		if (scope !== '' && name !== '') scope = scope + ':';
		return scope + name + tags;
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
				result += '{' + tagString.substring(0, tagString.length - 1) + '}';
			}
		}
		return result;
	}

	function copyFlagSeries(data) {
		var result;
		if (data) {
			result = {type: 'flags'/*, shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2*/};
			result.data = [];
			var tempID = 0;
			for (var i = 0; i < data.length; i++) {
				var flagData = data[i];
				result.data.push({
					x: flagData.timestamp,
					title: 'A',
					text: formatFlagText(flagData.fields),
					flagID: '_Flag'+tempID,
					fields: flagData.fields,
					source: flagData.source
				});
				tempID++;
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

		augmentExpressionWithControlsData: function(expression, controls) {
			var result = expression;
			for (var controlIndex in controls) {
				var controlName = controls[controlIndex].name;
				var controlValue = controls[controlIndex].value;
				var controlType = controls[controlIndex].type;

				if ( controlType === 'agDate' ) {
					controlValue = isNaN(Date.parse(controlValue)) ? controlValue : Date.parse(controlValue);
					// remove GMT from offset input from
					if( typeof (controlValue) === 'string' && controlValue.indexOf('GMT') >= 0){
						controlValue = controlValue.replace('GMT','').trim();
					}

					if(result.match(new RegExp('\\$' + controlName + '\\$', 'g')) !== null) {
						result = result.replace(new RegExp('\\$' + controlName + '\\$', 'g'), controlValue);
					}


					var match = null;
					//Check if it either matches something like $start-7h$ or $start-$diff$$
					if((match = result.match(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', 'g'))) !== null) {
						controlValue = this.modifyControlValue(controlValue, match[0]);
						result = result.replace(new RegExp('\\$' + controlName + '\\-\\d+[smhd]\\$', 'g'), controlValue);
					} else if((match = result.match(new RegExp('\\$' + controlName + '\\-\\$[^\\$]*\\$\\$', 'g'))) !== null) {
						match = match[0].substring(1, match[0].length - 1);
						var subtractControlName = match.match(/\$.*\$/)[0];
						subtractControlName = subtractControlName.substring(1, subtractControlName.length - 1);
						var value = this.getControlValueFromName(controls, subtractControlName);

						controlValue = this.modifyControlValue(controlValue, '-' + value);
						result = result.replace(new RegExp('\\$' + controlName + '\\-\\$[^\\$]*\\$\\$', 'g'), controlValue);
					}

				} else {
					controlValue = controlValue === undefined ? '' : controlValue;
					result = result.replace(new RegExp('\\$' + controlName + '\\$', 'g'), controlValue);
				}
			}

			result = result.replace(/(\r\n|\n|\r|\s+)/gm, '');
			return result;
		},

		getControlValueFromName: function(controls, controlName) {
			for(var index in controls) {
				if(controlName === controls[index].name) {
					return controls[index].value;
				}
			}

			return null;
		},

		modifyControlValue: function(controlValue, controlName) {
			var match = controlName.match(/\-\d+[smdh]/)[0];
			var subtract = this.getValue(match);

			if(isNaN(controlValue)) {
				controlValue = this.getValue(controlValue);
				return '-' + (controlValue + subtract) + 's';
			}

			return controlValue - (subtract * 1000);
		},

		getValue: function (timeStr) {
			timeStr = timeStr.substring(1);
			var digits = timeStr.substring(0, timeStr.length - 1);
			var unit = timeStr.substring(timeStr.length - 1);

			var secs = 'invalid';
			switch(unit) {
				case 's':
					secs = parseFloat(digits);
					break;
				case 'm':
					secs = parseFloat(digits) * 60;
					break;
				case 'h':
					secs = parseFloat(digits) * 3600;
					break;
				case 'd':
					secs = parseFloat(digits) * 24 * 3600;
					break;
				default:
					secs = 'Invalid time unit used.';
			}

			return secs;
		},

		processMetricData: function(data, controls) {
			if (!data) return;

			var processedData = [];
			var updatedMetricList = [];
			var updatedAnnotationList = [];
			var updatedOptionList = JsonFlattenService.unflatten(data.options);
			var processedExpression;

			for (var key in data.metrics) {
				if (data.metrics.hasOwnProperty(key)) {

					// get metricExpression, and name & color attributes from data
					var metrics = data.metrics[key];
					var metricExpression = metrics.expression;
					var metricSpecificOptions = metrics.metricSpecificOptions;
					processedExpression = this.augmentExpressionWithControlsData(metricExpression, controls);

					if (processedExpression.length > 0 /* && (/\$/.test(processedExpression)==false) */) {
						var processedMetric = {};
						processedMetric['expression'] = processedExpression;
						processedMetric['name'] = metrics.name;
						processedMetric['color'] = metrics.color;
						processedMetric['extraYAxis'] = metrics.extraYAxis;
						processedMetric['hideTags'] = metrics.hideTags;
						processedMetric['hideScope'] = metrics.hideScope;
						processedMetric['hideMetric'] = metrics.hideMetric;
						processedMetric['metricSpecificOptions'] = this.getMetricSpecificOptionsInArray(metricSpecificOptions);

						// update metric list with new processed metric object
						updatedMetricList.push(processedMetric);
					}
				}
			}

			for (var key in data.annotations) {
				if (data.annotations.hasOwnProperty(key)) {
					processedExpression = this.augmentExpressionWithControlsData(data.annotations[key], controls);
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
			if (data && data.length !== 0) {
				for (var i = 0; i < data.length; i++) {
					var series;
					// converts json to 2D array
					series = [];
					for (var key in data[i].datapoints) {
						var timestamp = parseInt(key);
						if (data[i].datapoints[key] !== null) {
							var value = parseFloat(data[i].datapoints[key]);
							series.push([timestamp, value]);
						}
					}
					if (metricItem.hideTags !== undefined) {
						if (metricItem.hideTags === 'true') {
							// delete all tags
							delete data[i].tags;
						} else {
							// delete provided tags
							var droppedTags = metricItem.hideTags.split(',').map(function(tag) {
								return tag.trim();
							});
							droppedTags.forEach(function(tag) {
								delete data[i].tags[tag];
							});
						}
					}

					if (metricItem.hideScope) delete data[i].scope;
					if (metricItem.hideMetric) delete data[i].metric;

					var metricName = (metricItem.name) ? metricItem.name : createSeriesName(data[i]);
					var metricColor = (metricItem.color) ? metricItem.color : null;
					var metricExtraYAxis = (metricItem.extraYAxis) ? metricItem.extraYAxis : null;
					var objSeries = {
						name: metricName,
						color: metricColor,
						extraYAxis: metricExtraYAxis,
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

		getDatapointRange: function (datapoints) {
			var result = {start: Number.MAX_VALUE, end: Number.MIN_VALUE};
			for (var key in datapoints) {
				if (datapoints.hasOwnProperty(key)) {
					if (key < result.start) {
						result.start = key;
					}
					if (key > result.end) {
						result.end = key;
					}
				}
			}
			return result;
		},

		getAlertFlagExpression: function (metric) {
			if (metric && metric.datapoints) {
				var range = this.getDatapointRange(metric.datapoints);
				var scopeName = metric.scope;
				var metricName = metric.metric;
				var tagData = metric.tags;
				var result = range.start + ':' + range.end + ':' + scopeName + ':' + metricName;
				result += createTagString(tagData);
				result += ':ALERT';
				return result;
			} else {
				return null;
			}
		},

		copySeries: copySeries,

		createMetricWithScopeMetricAndTags: createMetricWithScopeMetricAndTags,

		createSeriesName: createSeriesName,

		copyFlagSeries: copyFlagSeries
	};

	return service;
}]);
