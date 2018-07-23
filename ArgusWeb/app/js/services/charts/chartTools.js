'use strict';
/*global angular:false, d3:false, fc:false, window:false, screen:false, console:false */

angular.module('argus.services.charts.tools', [])
.service('ChartToolService', ['UtilService', function(UtilService) {

	this.getTimeAxis = function(timeSpan) {
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
	};

	// layout dimension
	var marginTopSmall= 5,
		marginRightSmall= 5;
	var marginTop = 15,
		marginBottom = 35,
		marginLeft = 50,
		marginRight= 60;
	var mainChartRatio = 0.8, //ratio of height
		brushChartRatio = 0.15;



	var bufferRatio = 0.2; // the ratio of buffer above/below max/min on yAxis for better showing experience

	this.extraYAxisPadding = 35;
	this.defaultHeatmapIntervalInMinutes = 30;
	this.defaultHeatmapNumOfBucket = 5;
	this.defaultAggregateType = 'avg';
	this.syncChartJobs = {};
	this.defaultTileColor = 'steelblue';

	this.getOrCreateSyncChartJobs = function(dashboardId){
		if(!this.syncChartJobs[dashboardId]){
			this.syncChartJobs[dashboardId] = {};
		}
		return this.syncChartJobs[dashboardId];
	};

	this.destroySyncChartJobs = function(dashboardId){
		delete this.syncChartJobs[dashboardId];
	};

	this.addSyncChartJob = function(dashboarId, chartId, syncChartJob){
		if(syncChartJob[dashboarId]){
			syncChartJob[dashboarId][chartId] = syncChartJob;
		}
	};

	this.calculateDimensions = function (newContainerWidth, newContainerHeight, isSmallChart, isBrushOn, extraYAxisNum) {
		var currentMarginTop = isSmallChart? marginTopSmall: marginTop;
		var currentMarginRight = isSmallChart? marginRightSmall: marginRight;
		var newWidth = newContainerWidth - marginLeft - currentMarginRight;
		var newHeight, newHeight2;

		// 'smallChart' setting: add to marginBottom, so xAxis has more room for rotated text labels
		var tmpMarginBottom = (isSmallChart)? marginBottom + 30 : marginBottom;

		if (isBrushOn) {
			newHeight = parseInt((newContainerHeight - currentMarginTop - tmpMarginBottom) * mainChartRatio);
			newHeight2 = parseInt((newContainerHeight - currentMarginTop - tmpMarginBottom) * brushChartRatio);
		} else {
			newHeight = parseInt(newContainerHeight - currentMarginTop - tmpMarginBottom);
			newHeight2 = 0;
		}

		var newMargin = {
			top: currentMarginTop,
			right: currentMarginRight,
			bottom: newContainerHeight - currentMarginTop - newHeight,
			left: marginLeft
		};
		var newMargin2 = {
			top: newContainerHeight - newHeight2 - tmpMarginBottom,
			right: currentMarginRight,
			bottom: tmpMarginBottom,
			left: marginLeft
		};
		return {
			width: newWidth - extraYAxisNum * this.extraYAxisPadding,
			widthFull: newWidth,
			height: newHeight,
			height2: newHeight2,
			margin: newMargin,
			margin2: newMargin2
		};
	};

	// date and formatting https://github.com/d3/d3-time-format/blob/master/README.md#timeFormat
	var numericalDate = '%-m/%-d/%y %H:%M:%S';
	var smallChartDate = '%x';  // %x = %m/%d/%Y  11/5/2016

	this.generateDateFormatter = function (isGMT, customizedFormat, isSmallChart) {
		var tmpDate;
		if (customizedFormat === undefined) {
			tmpDate = isSmallChart ? smallChartDate : numericalDate;
		} else {
			tmpDate = customizedFormat;
		}
		return isGMT? d3.utcFormat(tmpDate): d3.timeFormat(tmpDate);
	};

	this.bisectDate = d3.bisector(function (d) {
		return d[0];
	}).left;

	this.bisectDateStackedData = d3.bisector(function (d) {
		return d.data.timestamp;
	}).left;

	// menu option
	var sampleCustomFormat = '0,.8';     // scientific notation
	var defaultYaxis = '.3s';
	var defaultTicksYaxis = '5';

	this.rawDataFormat = ',';
	this.defaultMenuOption = {
		dateFormat: numericalDate,
		colorPalette: 'schemeCategory20',
		downSampleMethod: '',
		isSyncChart: false,
		isBrushMainOn: false,
		isWheelOn: false,
		isBrushOn: true,
		isTooltipOn: true,
		tooltipConfig: {
			rawTooltip: true,
			customTooltipFormat: sampleCustomFormat,
			leadingNum: null,
			trailingNum: null,
			isTooltipSortOn: true
		},
		yAxisConfig: {
			formatYaxis: defaultYaxis,
			numTicksYaxis: defaultTicksYaxis
		},
		isSnapCrosslineOn: true,
		localTimezone: false,
		showEmptyRange: false
	};
	this.defaultMenuOptionSmallChart = {
		dateFormat: numericalDate,
		colorPalette: 'schemeCategory20',
		downSampleMethod: '',
		isSyncChart: false,
		isBrushMainOn: false,
		isWheelOn: true,
		isBrushOn: false,
		isTooltipOn: true,
		tooltipConfig: {
			rawTooltip: true,
			customTooltipFormat: sampleCustomFormat,
			leadingNum: null,
			trailingNum: null,
			isTooltipSortOn: true
		},
		yAxisConfig: {
			formatYaxis: defaultYaxis,
			numTicksYaxis: defaultTicksYaxis
		},
		isSnapCrosslineOn: true,
		localTimezone: false,
		showEmptyRange: false
	};

	// color
	this.setColorScheme = function (colorPalette) {
		var result;
		switch (colorPalette) {
			case 'schemeCategory10':
				result = d3.scaleOrdinal(d3.schemeCategory10);
				break;
			case 'schemeCategory20b':
				result = d3.scaleOrdinal(d3.schemeCategory20b);
				break;
			case 'schemeCategory20c':
				result = d3.scaleOrdinal(d3.schemeCategory20c);
				break;
			default:
				result = d3.scaleOrdinal(d3.schemeCategory20);
		}
		return result;
	};

	this.bindDefaultColorsWithSources = function (colorZ, sourceNames) {
		colorZ.domain(sourceNames);
	};

	// other constants
	this.yAxisPadding = 1;

	// other things
	this.defaultEmptyGraphMessage = 'No graph available';

	this.getXandY = function (timeInfo, isGMT, sizeInfo, yScaleType, yScaleConfigValue) {
		var xScale = isGMT? d3.scaleUtc(): d3.scaleTime();
		var y = this.getY(sizeInfo, yScaleType, yScaleConfigValue);
		return {
			x: xScale.domain([timeInfo.startTime, timeInfo.endTime]).range([0, sizeInfo.width]),
			y: y.y,
			yScalePlain: y.yScalePlain
		};
	};

	this.getColorScale = function(sizeInfo){
		return  d3.scaleLiner().range([0, sizeInfo.width]);
	};

	this.getY = function (sizeInfo, yScaleType, yScaleConfigValue){
		var yScale, yScalePlain;
		if (yScaleConfigValue === undefined || isNaN(yScaleConfigValue)) yScaleConfigValue = 10;
		switch (yScaleType) {
			case 'log':
				yScale = d3.scaleLog().base(yScaleConfigValue);
				yScalePlain = d3.scaleLog().base(yScaleConfigValue);
				break;
			case 'power':
				yScale = d3.scalePow().exponent(yScaleConfigValue);
				yScalePlain = d3.scalePow().exponent(yScaleConfigValue);
				break;
			// case 'linear':
			default:
				yScale = d3.scaleLinear();
				yScalePlain = d3.scaleLinear();
		}

		return {
			y: yScale.range([sizeInfo.height, 0]),
			yScalePlain: yScalePlain
		};
	};

	this.generateTimestampSelector = function (isDataStacked) {
		return isDataStacked ? function (d) {return d.data.timestamp;} : function (d) {return d[0];};
	};

	this.getXandYDomainsOfSeries = function (series, isChartDiscrete, isDataStacked, timestampSelector, extraYAxisSet) {
		var datapoints = [];
		var extraDatapoints = {};
		var allDatapoints = [];
		for(var iSet of extraYAxisSet){
			extraDatapoints[iSet] = [];
		}

		series.forEach(function (metric) {
			if (metric.extraYAxis) {
				extraDatapoints[metric.extraYAxis] = extraDatapoints[metric.extraYAxis].concat(metric.data);
			} else {
				datapoints = datapoints.concat(metric.data);
			}
			allDatapoints = allDatapoints.concat(metric.data);
		});

		var result = {
			xDomain: this.getXDomainOfSeries(allDatapoints, timestampSelector),
			yDomain: this.getYDomainOfSeries(datapoints, isDataStacked),
			extraYDomain: this.getExtraYDomainOfSeries(extraDatapoints, extraYAxisSet)
		};

		if (isChartDiscrete) result.discreteXDomain = this.getDiscreteXDomainOfSeries(allDatapoints, timestampSelector);

		return result;
	};

	this.getXDomainOfSeries = function (dataPoints, timestampSelector) {
		var extent;
		extent = d3.extent(dataPoints, function (d) {
			return timestampSelector(d);
		});
		return extent;
	};

	this.getDiscreteXDomainOfSeries = function (datapoints, timestampSelector) {
		var result = [];
		datapoints.map(function (d) {
			var newTimestamp = timestampSelector(d);
			if (!result.includes(newTimestamp)) result.push(newTimestamp);
		});
		return result.sort();
	};

	this.getYDomainOfSeries = function (dataPoints, isDataStacked) {
		var extent;
		if (isDataStacked) {
			if (dataPoints === undefined || dataPoints.length === 0) {
				extent = [0, 0];
			} else {
				var yMin = Number.MAX_VALUE, yMax = Number.MAX_VALUE * (-1);
				dataPoints.map(function (d) {
					if (d[0] < yMin) yMin = d[0];
					if (d[1] > yMax) yMax = d[1];
				});
				extent = [yMin, yMax];
			}
		} else {
			extent = d3.extent(dataPoints, function (d) { return d[1]; });
		}
		return extent;
	};

	this.getExtraYDomainOfSeries = function (extraDatapoints, extraYAxisSet){
		var extraYDomain = {};

		for(var iSet of extraYAxisSet){
			extraYDomain[iSet] = d3.extent(extraDatapoints[iSet], function (d) {
				return d[1];
			});
		}

		return extraYDomain;
	};

	this.updateXandYRange = function (sizeInfo, x, y, needToAdjustHeight) {
		if (needToAdjustHeight) {
			y.range([sizeInfo.height, 0]);
		}
		x.range([0, sizeInfo.width]);
	};

	this.updateExtraYRange = function (sizeInfo, extraY, extraYAxisSet){
		if (extraYAxisSet){
			for(var iSet of extraYAxisSet){
				extraY[iSet].range([sizeInfo.height, 0]);
			}
		}
	};

	this.getSubDiscreteXDomain = function (discreteXDomain, newExtent) {
		var startTime = typeof newExtent[0] === 'number' ? newExtent[0] : newExtent[0].getTime();
		var endTime = typeof newExtent[1] === 'number' ? newExtent[1] : newExtent[1].getTime();
		var startIndex = discreteXDomain.indexOf(startTime);
		var endIndex = discreteXDomain.indexOf(endTime);
		if (startIndex === -1) startIndex = d3.bisectLeft(discreteXDomain, startTime) - 1;
		if (endIndex === -1) endIndex = d3.bisectRight(discreteXDomain, endTime);
		return discreteXDomain.slice(startIndex, endIndex + 1);
	};

	this.createSourceListForLegend = function (names, graphClassNames, colors, colorZ) {
		var tmpSources = [];
		for (var i = 0; i < names.length; i++) {
			var tempColor = colors[i] === null ? colorZ(names[i]) : colors[i];
			tmpSources.push({
				name: names[i],
				displaying: true,
				color: tempColor,
				graphClassName: graphClassNames[i]
			});
		}
		return tmpSources;
	};

	var everyNthPoint = function () {
		var bucketSize = 1;
		var everyNthPoint = function(data){
			var temp = [];
			for(var i = 0; i < data.length; i+=bucketSize){
				temp.push(data[i]);
			}
			return temp;
		};
		everyNthPoint.bucketSize = function(size){
			bucketSize = size;
		};
		return everyNthPoint;
	};

	// var downsampleThreshold = 1/2; // datapoints per pixel
	this.downSample = function (series, containerWidth, downSampleMethod, downSampleThreshold) {
		if (downSampleThreshold === undefined) downSampleThreshold = 0.5;
		if (!series) return series;
		if (downSampleMethod === '' || downSampleMethod === undefined) return series;
		// var temp = angular.copy(series);
		var temp = series.map(function(metric) {
			return UtilService.objectWithoutProperties(metric, ['data']);
		});

		// Create the sampler
		var sampler;
		switch (downSampleMethod){
			case 'largest-triangle-one-bucket':
				sampler = fc.largestTriangleOneBucket();
				// Configure the x / y value accessors
				sampler.x(function(d) {return d[0];})
						.y(function(d){return d[1];});
				break;
			case 'largest-triangle-three-bucket':
				sampler = fc.largestTriangleThreeBucket();
				// Configure the x / y value accessors
				sampler.x(function(d) {return d[0];})
						.y(function(d){return d[1];});
				break;
			case 'mode-median':
				sampler = fc.modeMedian();
				sampler.value(function(d){return d[1];});
				break;
			case 'every-nth-point':
				sampler = everyNthPoint();
				break;
		}

		// Run the sampler
		if (sampler) {
			series.forEach(function(metric, index){
				//determine whether to downsample or not
				//downsample if there are too many datapoints per pixel
				if (metric.data.length / containerWidth > downSampleThreshold) {
					//determine bucket size
					var bucketSize = Math.ceil(metric.data.length / (downSampleThreshold * containerWidth));
					// Configure the size of the buckets used to downsample the data.
					sampler.bucketSize(bucketSize);
					temp[index].data  = sampler(metric.data);
				} else {
					// no need to downsample; just copy over the data
					temp[index].data  = angular.copy(metric.data);
				}
			});
		}

		return temp;
	};

	this.downSampleASingleMetricsDataEveryTenPoints = function (metric, containerWidth) {
		if (metric.data.length / containerWidth > 0.1) {
			// var result = angular.copy(metric);
			var result = UtilService.objectWithoutProperties(metric, ['data']);
			var sampler = everyNthPoint();
			var bucketSize = Math.ceil(metric.data.length / (0.1 * containerWidth));
			sampler.bucketSize(bucketSize);
			result.data = sampler(metric.data);
			return result;
		} else {
			return metric;
		}
	};

	this.setZoomExtent = function (series, zoom, k) {
		//extent, k is the least number of points in one line you want to see on the main chart view
		var numOfPoints = series[0].data.length;
		for (var i = 1; i < series.length; i++) {
			if (numOfPoints < series[i].data.length) {
				numOfPoints = series[i].data.length;
			}
		}
		if (!k || k > numOfPoints) k = 3;
		zoom.scaleExtent([1, numOfPoints / k]);
		return parseInt(numOfPoints / k);
	};

	this.isBrushInNonEmptyRange = function (xDomain, dateExtent) {
		return xDomain[0] <= dateExtent[1] && xDomain[xDomain.length - 1] >= dateExtent[0];
	};

	this.isNotInTheDomain = function (value, domainArray) {
		return value < domainArray[0] || value > domainArray[domainArray.length - 1];
	};

	this.isMetricNotInTheDomain = function (metric, xDomain, timestampSelector) {
		var len = metric.data.length;
		if (len < 1) return false;
		var startPoint = timestampSelector(metric.data[0]);
		var endPoint = timestampSelector(metric.data[len - 1]);
		return startPoint > xDomain[xDomain.length - 1] || endPoint < xDomain[0];
	};
	var isMetricNotInTheDomain = this.isMetricNotInTheDomain;

	this.updateContainerSize = function (container, defaultContainerHeight, defaultContainerWidth, isSmallChart, isBrushOn, changeToFullscreen, extraYAxisNum) {
		var containerWidth, containerHeight;
		if (changeToFullscreen && (window.innerHeight === screen.height || container.offsetHeight === window.innerHeight)) {
			// set the graph size to be the same as the screen
			containerWidth = screen.width;
			containerHeight = screen.height * 0.95;
		} else {
			// default containerHeight will be used
			containerHeight = defaultContainerHeight;
			// no width defined via chart option: window width will be used
			containerWidth = defaultContainerWidth < 0 ? container.offsetWidth : defaultContainerWidth;
		}
		var newSize = this.calculateDimensions(containerWidth, containerHeight, isSmallChart, isBrushOn, extraYAxisNum);
		return {
			newSize: newSize,
			containerWidth: containerWidth,
			containerHeight: containerHeight
		};
	};

	this.calculateGradientOpacity = function (num, tot) {
		return 0.9 - 0.7 * (num / tot);
	};

	var findValueAtAGivenTimestamp = function (metric, timestamp, startingIndex) {
		for (var i = startingIndex; i < metric.data.length; i++) {
			if (metric.data[i][0] === timestamp) {
				return {
					index: i,
					value: metric.data[i][1]
				};
			}
		}
	};

	this.convertSeriesToTimeBasedFormat = function (series, metricsToIgnore) {
		var result = [];
		var allTimestamps = [];
		var valuesAtPreviousTimestampWithIndex = {};

		var needToIgnoreSomeMetrics = metricsToIgnore !== undefined && metricsToIgnore.length !== 0;
		series.map(function(metric) {
			if (needToIgnoreSomeMetrics && metricsToIgnore.includes(metric.name)) return;
			valuesAtPreviousTimestampWithIndex[metric.name] = {value: 0, index: 0};
			metric.data.map(function(d) {
				var timestamp = d[0];
				if (!allTimestamps.includes(timestamp)) allTimestamps.push(timestamp);
			});
		});

		// sort the timestamps and add values from each source
		allTimestamps.sort(function(a, b) { return a - b; });
		allTimestamps.map(function(timestamp) {
			var valuesAtThisTimestamp = {timestamp: timestamp};
			series.map(function(metric) {
				if (needToIgnoreSomeMetrics && metricsToIgnore.includes(metric.name)) return;
				var tempValueWithIndex = findValueAtAGivenTimestamp(metric, timestamp, valuesAtPreviousTimestampWithIndex[metric.name].index);
				// use previous value when there is no data for this timestamp
				if (tempValueWithIndex === undefined) {
					if (valuesAtPreviousTimestampWithIndex[metric.name] !== undefined) {
						tempValueWithIndex = valuesAtPreviousTimestampWithIndex[metric.name];
					} else {
						tempValueWithIndex = { value: 0, index: 0 };
						valuesAtPreviousTimestampWithIndex[metric.name] = tempValueWithIndex;
					}
				} else {
					valuesAtPreviousTimestampWithIndex[metric.name] = tempValueWithIndex;
				}
				valuesAtThisTimestamp[metric.name] = tempValueWithIndex.value;
			});
			result.push(valuesAtThisTimestamp);
		});
		return result;
	};

	this.getAggregatedSeriesAndXYZDomain = function(series, names, aggr, intervalInMinutes){
		var timeBasedSeries = this.convertSeriesToTimeBasedFormatKeepUndefined(series);
		return this.getAggregatedTimeBasedSeriesAndXYZDomain(timeBasedSeries, names, aggr, intervalInMinutes);
	};

	this.convertSeriesToTimeBasedFormatKeepUndefined = function (series) {
		var result = [];
		var allTimestamps = [];
		var valuesAtPreviousTimestampWithIndex = {};

		series.map(function(metric) {
			valuesAtPreviousTimestampWithIndex[metric.name] = {value: 0, index: 0};
			metric.data.map(function(d) {
				var timestamp = d[0];
				if (!allTimestamps.includes(timestamp)) allTimestamps.push(timestamp);
			});
		});

		// sort the timestamps and add values from each source
		allTimestamps.sort(function(a, b) { return a - b; });
		allTimestamps.map(function(timestamp) {
			var valuesAtThisTimestamp = {timestamp: timestamp};

			series.map(function(metric) {
				var tempValueWithIndex = findValueAtAGivenTimestamp(metric, timestamp, valuesAtPreviousTimestampWithIndex[metric.name].index);
				if(tempValueWithIndex !== undefined){
					valuesAtPreviousTimestampWithIndex[metric.name] = tempValueWithIndex;
					valuesAtThisTimestamp[metric.name] = tempValueWithIndex.value;
				}
			});
			result.push(valuesAtThisTimestamp);
		});
		return result;
	};

	this.getAggregatedTimeBasedSeriesAndXYZDomain = function(timeBasedSeries, names, aggr, intervalInMinutes) {
		intervalInMinutes = intervalInMinutes || this.defaultHeatmapIntervalInMinutes;
		var startOfInterval = timeBasedSeries[0].timestamp;
		var endOfInterval = startOfInterval + intervalInMinutes * 60000;
		var aggregatedSeries = [];
		var tempSum = {};
		var tempCount = {};
		var yDomain = [Number.MAX_VALUE, Number.MIN_VALUE];

		function aggregate() {
			if (aggr === 'sum') {
				var sum = {timestamp: startOfInterval};
				names.forEach(function (name) {
					if (tempSum[name] !== undefined) {
						var val = tempSum[name];
						if (val < yDomain[0]) {
							yDomain[0] = val;
						}
						if (val > yDomain[1]) {
							yDomain[1] = val;
						}
						sum[name] = val;
					}
				});
				aggregatedSeries.push(sum);
			} else if (aggr === 'avg') {
				var avg = {timestamp: startOfInterval};
				names.forEach(function (name) {
					if (tempSum[name] !== undefined) {
						var val = tempSum[name] / tempCount[name];
						if (val < yDomain[0]) {
							yDomain[0] = val;
						}
						if (val > yDomain[1]) {
							yDomain[1] = val;
						}
						avg[name] = val;
					}
				});
				aggregatedSeries.push(avg);
			}
		}

		timeBasedSeries.forEach(function (d) {
			if (d.timestamp < endOfInterval) {
				//keep adding
				names.forEach(function (name) {
					var val = d[name];
					if (val !== undefined) {
						if (tempSum[name]) {
							tempSum[name] += val;
							tempCount[name] += 1;
						} else {
							tempSum[name] = val;
							tempCount[name] = 1;
						}
					}
				});
			} else {
				//sum/avg this interval
				aggregate();
				startOfInterval = endOfInterval;
				endOfInterval = startOfInterval + intervalInMinutes * 60000;

				//start adding
				names.forEach(function (name) {
					var val = d[name];
					if (val !== undefined) {
						tempSum[name] = val;
						tempCount[name] = 1;
					}
				});
			}
		});


		//last interval
		if(timeBasedSeries[timeBasedSeries.length - 1].timestamp < endOfInterval){
			//sum/avg this interval
			aggregate();
		}
		return {
			aggregatedSeries: aggregatedSeries,
			xDomain: [timeBasedSeries[0].timestamp, timeBasedSeries[timeBasedSeries.length-1].timestamp],
			yDomain: yDomain,
			zDomain: [0, names.length]
		};
	};


	this.getHeatmapDataAndBucketInfo = function(aggregatedSeriesAndYDomain, bucketMin, step, numOfBucket){
		var heatmapData = [];
		var aggregatedSeries = aggregatedSeriesAndYDomain.aggregatedSeries;
		var yDomain = aggregatedSeriesAndYDomain.yDomain;
		numOfBucket = numOfBucket || this.defaultHeatmapNumOfBucket;
		bucketMin = this.getTheNumberValueFromTwo(bucketMin, yDomain[0]);
		step = step || (yDomain[1] - bucketMin) / numOfBucket;
		var bucketMax = bucketMin + step * numOfBucket;

		//transfer to time, bucket, frequency
		aggregatedSeries.forEach(function(d){
			var temp = [];
			for(var i = 0; i < numOfBucket; i++){
				temp[i] = {
					bucket: bucketMin + i * step,
					count: 0,
					names: []
				};
			}
			for(var k in d){
				if(d.hasOwnProperty(k)){
					if (k !== 'timestamp'){
						for(i = 0; i < numOfBucket; i++){
							if(d[k] < bucketMin + step * (i + 1)){
								temp[i].count += 1;
								temp[i].names.push(k);
								break;
							}
						}
						if(d[k] === bucketMax){
							temp[numOfBucket - 1].count += 1;
							temp[numOfBucket - 1].names.push(k);
						}
					}
				}
			}
			temp.forEach(function(e){
				heatmapData.push({
					timestamp: d.timestamp,
					bucket: e.bucket,
					frequency: e.count,
					names: e.names
				});
			});

		});

		return{
			newYDomain: [bucketMin, bucketMin + step * numOfBucket],
			heatmapData: heatmapData,
			numOfBucket: numOfBucket,
			bucketMin: bucketMin,
			step: step
		};
	};

	this.addStackedDataToSeries = function (series, stack, metricsToIgnore) {
		var stackedData = stack(this.convertSeriesToTimeBasedFormat(series, metricsToIgnore));
		var newSeries = series.map(function (metric, index) {
			var newMetric = UtilService.objectWithoutProperties(metric, ['data']);
			newMetric.data = stackedData[index];
			return newMetric;
		});
		return newSeries;
	};

	this.adjustSeriesBeingDisplayed = function (series, x, timestampSelector, dateBisector) {
		var xDomain = x.domain();
		var newDisplayingSeries = series.map(function(metric) {
			return UtilService.objectWithoutProperties(metric, ['data']);
		});
		series.forEach(function (metric, index) {
			if (isMetricNotInTheDomain(metric, xDomain, timestampSelector)) {
				newDisplayingSeries[index].data = [];
				return;
			}
			var start, end;
			start = dateBisector(metric.data, xDomain[0]);
			if (start > 0) start -= 1; //to avoid cut off issue on the edge
			end = dateBisector(metric.data, xDomain[1], start) + 1; //to avoid cut off issue on the edge
			newDisplayingSeries[index].data = metric.data.slice(start, end + 1);
		});
		return newDisplayingSeries;
	};

	this.findMatchingMetricInSources = function (metric, sources) {
		return sources.filter(function(source) {
			return source.name === metric.name;
		})[0];
	};

	this.processYDomain = function (currentExtent, yScalePlain, yScaleType, agYMin, agYMax, isDataStacked, isChartDiscrete) {
		var yMin, yMax, buffer, finalYMin, finalYMax;
		yMin = UtilService.validNumberChecker(yScalePlain(currentExtent[0]));
		yMax = UtilService.validNumberChecker(yScalePlain(currentExtent[1]));
		buffer = (yMax - yMin) * bufferRatio;
		if (buffer === 0) buffer = this.yAxisPadding;

		finalYMin = (agYMin === undefined) ? UtilService.validNumberChecker(yScalePlain.invert(yMin - buffer)): agYMin;
		finalYMax = (agYMax === undefined) ? UtilService.validNumberChecker(yScalePlain.invert(yMax + 1.2 * buffer)): agYMax;
		// TODO: need to test with negative values for area and bar charts
		if (isDataStacked && finalYMin < 0 && yMin !== yMax) finalYMin = 0;
		// if (isChartDiscrete && finalYMin < 0 && yMin < yMax) finalYMin = 0;
		if (isChartDiscrete && !isDataStacked && finalYMin > 0 && yMin < yMax) {
			finalYMin = 0;
			finalYMax *= 1.2;
		}
		// TODO: still need to handle log(0) better
		if (yScaleType === 'log') {
			if (finalYMin === 0) finalYMin = 1;
			if (finalYMax === 0) finalYMax = 1;
		}

		return [finalYMin, finalYMax];
	};

	this.getTileData = function(heatmapData, graph, mouseData, bucketInfo){
		var xIndex = Math.floor((mouseData.mouseX - graph.x.domain()[0])/bucketInfo.xStep);
		var yIndex = Math.floor((mouseData.mouseY - graph.y.domain()[0])/bucketInfo.yStep);
		var index = bucketInfo.numOfYStep * xIndex + yIndex;
		var tileData = heatmapData[index];
		return {
			data: tileData,
			xIndex: xIndex,
			yIndex: yIndex
		};
	};

	this.getGraphClassNamesMap = function(series){
		var map = {};
		series.forEach(function(metric){
			map[metric.name] = metric.graphClassName;
		});
		return map;
	};

	this.Number = function(a){
		if(typeof(a) === "string" && a.trim() === "") return NaN;
		return Number(a);
	};

	this.isNaN = function (a) {
		if(typeof(a) === "string" && a.trim() === "") return true;
		return isNaN(a);
	};

	this.getTheNumberValueFromTwo = function(a, b){
		return (this.isNaN(a) ? Number(b) : Number(a));
	};

	this.getTheNumberValueFromThree = function(a, b, c){
		var temp = (this.isNaN(a) ? Number(b) : Number(a));
		return this.isNaN(temp) ? Number(c) : temp;
	};

	this.equalTimeDomain = function(domain1, domain2) {
		return (new Date(domain1[0]).getTime() === new Date(domain2[0]).getTime()) &&
				(new Date(domain1[1]).getTime() === new Date(domain2[1]).getTime());
	};
}]);
