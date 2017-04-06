'use strict';

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

    this.calculateDimensions = function (newContainerWidth, newContainerHeight, isSmallChart) {
        var currentMarginTop = isSmallChart? marginTopSmall: marginTop;
        var currentMarginRight = isSmallChart? marginRightSmall: marginRight;
        var newWidth = newContainerWidth - marginLeft - currentMarginRight;
        var newHeight = parseInt((newContainerHeight - currentMarginTop - marginBottom) * mainChartRatio);
        var newHeight2 = parseInt((newContainerHeight - currentMarginTop - marginBottom) * brushChartRatio);
        var newMargin = {
            top: currentMarginTop,
            right: currentMarginRight,
            bottom: newContainerHeight - currentMarginTop - newHeight,
            left: marginLeft
        };
        var newMargin2 = {
            top: newContainerHeight - newHeight2 - marginBottom,
            right: currentMarginRight,
            bottom: marginBottom,
            left: marginLeft
        };
        return {
            width: newWidth,
            height: newHeight,
            height2: newHeight2,
            margin: newMargin,
            margin2: newMargin2
        };
    };

    // date and formatting https://github.com/d3/d3-time-format/blob/master/README.md#timeFormat
    var longDate = '%A, %b %e, %H:%M';      // Saturday, Nov 5, 11:58
    var shortDate = '%b %e, %H:%M';
    var numericalDate = '%-m/%-d/%y %H:%M:%S';
    var smallChartDate = '%x';  // %x = %m/%d/%Y  11/5/2016
    this.generateDateFormatter = function (isGMT, customizedFormat, isSmallChart) {
        var result, tmpDate;
        if (isSmallChart) {
            result = isGMT? d3.utcFormat(smallChartDate): d3.timeFormat(smallChartDate);
        } else {
            tmpDate = customizedFormat === undefined? numericalDate: customizedFormat;
            result = isGMT? d3.utcFormat(tmpDate): d3.timeFormat(tmpDate);
        }
        return result;
    };

    this.bisectDate = d3.bisector(function (d) {
        return d[0];
    }).left;
    this.rawDataFormat = ',';

    // menu option
    var rawDataFormat = ',';
    var sampleCustomFormat = '0,.8';     // scientific notation
    var defaultYaxis = '.3s';
    var defaultTicksYaxis = '5';
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
        }
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
    this.xAxisLabelHeightFactor = 15;
    this.yAxisPadding = 1;

    var bufferRatio = 0.2; //the ratio of buffer above/below max/min on yAxis for better showing experience

    // other things
    this.defaultEmptyGraphMessage = 'No graph available';

    this.getXandY = function (timeInfo, sizeInfo, yScaleType) {
        var xScale = timeInfo.GMTon? d3.scaleUtc(): d3.scaleTime();
        var yScale;
        switch (yScaleType) {
            case "log":
                yScale = d3.scaleLog();
                break;
            case "power":
                yScale = d3.scalePow();
                break;
            case "linear":
            default:
                yScale = d3.scaleLinear();
        }

        return {
            x: xScale.domain([timeInfo.startTime, timeInfo.endTime]).range([0, sizeInfo.width]),
            y: yScale.range([sizeInfo.height, 0])
        }
    };

    this.getXandYDomainsOfSeries = function (series) {
        var allDatapoints = [];
        series.forEach(function (metric) {
            allDatapoints = allDatapoints.concat(metric.data);
        });
        var xDomain = d3.extent(allDatapoints, function (d) {
            return d[0];
        });
        var yDomain = d3.extent(allDatapoints, function (d) {
            return d[1];
        });
        return {
            xDomain: xDomain,
            yDomain: yDomain
        }
    };

    this.createSourceListForLegend = function (names, colors, graphClassNames, colorZ) {
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

    var downsampleThreshold = 1/2; // datapoints per pixel
    this.downSample = function (series, downSampleMethod, containerWidth) {
        if (!series) return;

        // Create the sampler
        var temp = JSON.parse(JSON.stringify(series));
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

        function everyNthPoint(){
            var bucketSize = 1;
            var everyNthPoint = function(data){
                var temp = [];
                for(var i = 0; i < data.length; i+=bucketSize){
                    temp.push(data[i])
                }
                return temp;
            };
            everyNthPoint.bucketSize = function(size){
                bucketSize = size;
            };
            return everyNthPoint;
        }

        // Run the sampler
        if (sampler) {
            series.forEach(function(metric, index){
                //determine whether to downsample or not
                //downsample if there are too many datapoints per pixel
                if(metric.data.length / containerWidth > downsampleThreshold){
                    //determine bucket size
                    var bucketSize = Math.ceil(metric.data.length / (downsampleThreshold * containerWidth));
                    // Configure the size of the buckets used to downsample the data.
                    sampler.bucketSize(bucketSize);
                    temp[index].data  = sampler(metric.data);
                }
            });
        }

        return temp;
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
        return xDomain[0].getTime() <= dateExtent[1] &&  xDomain[1].getTime()>= dateExtent[0];
    };

    this.isNotInTheDomain = function (value, domainArray) {
        return value < domainArray[0] || value > domainArray[1];
    };
}]);