'use strict';

angular.module('argus.directives.charts.lineChart', [])
.directive('lineChart', ['$timeout', 'Storage', '$routeParams', function($timeout, Storage, $routeParams) {
    var resizeTimeout = 250; //the time for resize function to fire
    var resizeJobs = [];
    var timer;
    var lineChartIdIndex = 0;
    var lineChartIdName = 'linechart_'; // in case there are other kind of ag-chart on the page

    function resizeHelper(){
        $timeout.cancel(timer); //clear to improve performance
        timer = $timeout(function () {
            resizeJobs.forEach(function (resizeJob) { //resize all the charts
                resizeJob();
            });
        }, resizeTimeout); //only execute resize after a timeout
    }

    d3.select(window).on('resize', resizeHelper);

    return {
        restrict: 'E',
        replace: true,
        scope: {
            chartConfig: '=chartconfig',
            series: '=series',
            dateConfig: '=dateconfig'
        },
        templateUrl: 'js/templates/charts/topToolbar.html',
        controller: ['$scope', function($scope) {
            $scope.sources = [];
            // can be used for future modal window
            $scope.noDataSeries = [];
            $scope.invalidSeries = [];

            $scope.toggleSource = function(source) {
                toggleGraphOnOff(source);
            };

            // show ONLY this 1 source, hide all others
            $scope.hideOtherSources = function(sourceToShow) {
                var sources = $scope.sources;
                for (var i = 0; i < sources.length; i++) {
                    if (sourceToShow.name !== sources[i].name) {
                        toggleGraphOnOff(sources[i]);
                    }
                }
            };

            $scope.labelTextColor = function(source) {
                return source.displaying? source.color: 'white';
            };

            function toggleGraphOnOff(source) {
                // d3 select with dot in ID name: http://stackoverflow.com/questions/33502614/d3-how-to-select-element-by-id-when-there-is-a-dot-in-id
                // var graphID = source.name.replace(/\s+/g, '');
                var displayProperty = source.displaying? 'none' : null;
                source.displaying = !source.displaying;
                d3.selectAll("." + source.graphClassName)
                    .style('display', displayProperty);
            }
        }],
        // compile: function (iElement, iAttrs, transclude) {},
        link: function (scope, element, attributes) {
            scope.lineChartId = ++lineChartIdIndex;
            /**
             * not using chartId because when reload the chart by 'sumbit' button
             * or other single page app navigate button the chartId is not reset
             * to 1, only by refreshing the page would the chartId be reset to 0
             */

            var chartId = scope.chartConfig.chartId;
            var series = scope.series;
            var startTime = scope.dateConfig.startTime;
            var endTime = scope.dateConfig.endTime;
            var GMTon = scope.dateConfig.gmt;
            var chartOptions = scope.chartConfig;

            var agYMin, agYMax;
            //provide support for yaxis lower case situation.
            if(chartOptions.yAxis){
                agYMin = chartOptions.yAxis.min;
                agYMax = chartOptions.yAxis.max;
            }
            if(chartOptions.yaxis){
                agYMin = agYMin || chartOptions.yaxis.min;
                agYMax = agYMax || chartOptions.yaxis.max;
            }

            if (isNaN(agYMin)) agYMin = undefined;
            if (isNaN(agYMax)) agYMax = undefined;



            // set $scope values, get them from the local storage
            scope.menuOption = {
                isWheelOn : false,
                isBrushOn : true,
                isBrushMainOn : false,
                isTooltipOn : true,
                isTooltipSortOn: false,
                isTooltipDetailOn: false
            };

            scope.dashboardId = $routeParams.dashboardId;

            var menuOption = Storage.get('menuOption_' + scope.dashboardId +'_' + lineChartIdName + scope.lineChartId);
            if (menuOption){
                scope.menuOption = menuOption;
            }


            var dateExtent; //extent of non empty data date range
            // ---------
            var topToolbar = $(element); //jquery selection
            var container = topToolbar.parent()[0];//real DOM

            var maxScaleExtent = 100; //zoom in extent
            var currSeries = series;

            // Layout parameters
            var containerHeight = 330;
            var containerWidth = $("#" + chartId).width();

            if (chartOptions.chart !== undefined) {
                containerHeight = chartOptions.chart.height === undefined ? containerHeight: chartOptions.chart.height;
                containerWidth = chartOptions.chart.width === undefined ? containerWidth: chartOptions.chart.width;
            }
            var xAxisLabelHeightFactor = 15;
            var brushHeightFactor = 20;
            var mainChartRatio = 0.8, //ratio of height
                tipBoxRatio = 0.2,
                brushChartRatio = 0.2
                ;
            var marginTop = 15,
                marginBottom = 35,
                marginLeft = 50,
                marginRight = 60;

            var width = containerWidth - marginLeft - marginRight;
            var height = parseInt((containerHeight - marginTop - marginBottom) * mainChartRatio);
            var height2 = parseInt((containerHeight - marginTop - marginBottom) * brushChartRatio) - brushHeightFactor;

            var margin = {
                top: marginTop,
                right: marginRight,
                bottom: containerHeight - marginTop - height,
                left: marginLeft
            };

            var margin2 = {
                top: containerHeight - height2 - marginBottom,
                right: marginRight,
                bottom: marginBottom,
                left: marginLeft
            };

            var tipPadding = 3;
            var tipOffset = 8;
            var circleRadius = 4.5;

            var crossLineTipWidth = 35;
            var crossLineTipHeight = 15;
            var crossLineTipPadding = 3;

            var bufferRatio = 0.2; //the ratio of buffer above/below max/min on yAxis for better showing experience

            // Local helpers
            // date formats
            // https://github.com/d3/d3-time-format/blob/master/README.md#timeFormat
            var longDate = '%A, %b %e, %H:%M';      // Saturday, Nov 5, 11:58
            var shortDate = '%b %e, %H:%M';
            var numericalDate = '%-m/%-d/%y %H:%M:%S';   // %x = %m/%d/%Y  11/5/2016

            var bisectDate = d3.bisector(function (d) {
                return d[0];
            }).left;
            var formatDate = d3.timeFormat(shortDate);
            var GMTformatDate = d3.utcFormat(numericalDate);

            var formatValue = d3.format(',');

            //graph setup variables
            var x, x2, y, y2,
                nGridX = 7, nGridY = 5,
                xAxis, xAxis2, yAxis, yAxisR, yAxis2, xGrid, yGrid,
                line, line2, area, area2,
                brush, brushMain, zoom,
                svg, svg_g, mainChart, xAxisG, xAxisG2, yAxisG, yAxisRG, xGridG, yGridG, //g
                focus, context, clip, brushG, brushMainG, chartRect, flags,//g
                tip, tipBox, tipItems,
                crossLine,
                names, colors, graphClassNames;

            var messageToDisplay = ['No graph available'];

            // color scheme
            var z = d3.scaleOrdinal(d3.schemeCategory20);

            // Base graph setup, initialize all the graph variables
            function setGraph() {
                // use different x axis scale based on timezone
                if (GMTon) {
                    x = d3.scaleUtc().domain([startTime, endTime]).range([0, width]);
                    x2 = d3.scaleUtc().domain([startTime, endTime]).range([0, width]); //for brush
                } else {
                    x = d3.scaleTime().domain([startTime, endTime]).range([0, width]);
                    x2 = d3.scaleTime().domain([startTime, endTime]).range([0, width]); //for brush
                }

                y = d3.scaleLinear().range([height, 0]);
                y2 = d3.scaleLinear().range([height2, 0]);

                //Axis
                xAxis = d3.axisBottom()
                    .scale(x)
                    .ticks(nGridX)
                ;

                xAxis2 = d3.axisBottom() //for brush
                    .scale(x2)
                    .ticks(nGridX)
                ;

                yAxis = d3.axisLeft()
                    .scale(y)
                    .ticks(nGridY)
                    .tickFormat(d3.format('.2s'))
                ;

                yAxisR = d3.axisRight()
                    .scale(y)
                    .ticks(nGridY)
                    .tickFormat(d3.format('.2s'))
                ;

                //grid
                xGrid = d3.axisBottom()
                    .scale(x)
                    .ticks(nGridX)
                    .tickSizeInner(-height)
                ;

                yGrid = d3.axisLeft()
                    .scale(y)
                    .ticks(nGridY)
                    .tickSizeInner(-width)
                ;

                //line
                line = d3.line()
                    .x(function (d) {
                        return x(d[0]);
                    })
                    .y(function (d) {
                        return y(d[1]);
                    });

                //line2 (for brush area)
                line2 = d3.line()
                    .x(function (d) {
                        return x2(d[0]);
                    })
                    .y(function (d) {
                        return y2(d[1]);
                    });

                //brush
                brush = d3.brushX()
                    .extent([[0, 0], [width, height2]])
                    .on("brush end", brushed);

                brushMain = d3.brushX()
                    .extent([[0, 0], [width, height]])
                    .on("end", brushedMain);

                //zoom
                zoom = d3.zoom()
                    .scaleExtent([1, Infinity])
                    .translateExtent([[0, 0], [width, height]])
                    .extent([[0, 0], [width, height]])
                    .on("zoom", zoomed)
                    .on("start", function () {
                        mainChart.select(".chartOverlay").style("cursor", "move");
                    })
                    .on("end", function () {
                        mainChart.select(".chartOverlay").style("cursor", "crosshair");
                    })
                ;

                //Add elements to SVG
                svg = d3.select(container).append('svg')
                    .attr('width', width + margin.left + margin.right)
                    .attr('height', height + margin.top + margin.bottom);

                svg_g = svg
                    .append('g')
                    .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')')
                ;

                mainChart = svg_g.append("g");

                xAxisG = mainChart.append('g')
                    .attr('class', 'x axis')
                    .attr('transform', 'translate(0,' + height + ')')
                    .call(xAxis);

                yAxisG = mainChart.append('g')
                    .attr('class', 'y axis')
                    .call(yAxis);

                // add axis label if they are in ag options
                if (chartOptions.xAxis!== undefined && chartOptions.xAxis.title !== undefined) {
                    mainChart.append("text")
                              .attr("class", "xAxisLabel")
                              .attr("transform", "translate(" + (width / 2) + " ," + (height + margin.top + xAxisLabelHeightFactor) + ")")
                              .style("text-anchor", "middle")
                              .style("font-size", 12)
                              .text(chartOptions.xAxis.title.text);
                }
                if (chartOptions.yAxis!== undefined && chartOptions.yAxis.title !== undefined) {
                    mainChart.append("text")
                              .attr("class", "yAxisLabel")
                              .attr("transform", "rotate(-90)")
                              .attr("y", 0 - margin.left)
                              .attr("x",0 - (height / 2))
                              .attr("dy", "1em")
                              .style("text-anchor", "middle")
                              .style("font-size", 12)
                              .text(chartOptions.yAxis.title.text);
                }

                yAxisRG = mainChart.append('g')
                    .attr('class', 'y axis')
                    .attr('transform', 'translate(' + width + ')')
                    .call(yAxisR);

                xGridG = mainChart.append('g')
                    .attr('class', 'x grid')
                    .attr('transform', 'translate(0,' + height + ')')
                    .call(xGrid);

                yGridG = mainChart.append('g')
                    .attr('class', 'y grid')
                    .call(yGrid);

                //Brush, zoom, pan
                //clip path
                clip = svg_g.append("defs").append("clipPath")
                    .attr('name','clip')
                    .attr("id", "clip_" + chartId)
                    .append("rect")
                    .attr("width", width)
                    .attr("height", height);

                //brush area
                context = svg_g.append("g")
                    .attr("class", "context")
                    // .attr("transform", "translate(0," + (height + margin.top + 10) + ")");
                    .attr("transform", "translate(0," + margin2.top + ")");

                // flags (annotations)
                flags = svg_g.append("g").attr("class", "flags");

                //set brush area axis
                xAxisG2 = context.append("g")
                    .attr("class", "xBrush axis")
                    .attr("transform", "translate(0," + height2 + ")")
                    .call(xAxis2)
                ;

                // Mouseover/tooltip setup
                focus = mainChart.append('g')
                    .attr('class', 'focus')
                    .style('display', 'none');
                tip = svg_g.append('g')
                    .attr('class', 'tooltip')
                    .style('opacity', 1)
                    .style('display', 'none');
                tipBox = tip.append('rect')
                    .attr('rx', tipPadding)
                    .attr('ry', tipPadding);
                tipItems = tip.append('g')
                    .attr('class', 'tooltip-items');

                //focus tracking/crossLine
                crossLine = focus.append('g')
                    .attr('name', 'crossLine');
                crossLine.append('line')
                    .attr('name', 'crossLineX')
                    .attr('class', 'crossLine');
                crossLine.append('line')
                    .attr('name', 'crossLineY')
                    .attr('class', 'crossLine');

                //tooltip label on axis background rect
                crossLine.append('rect')
                    .attr('name', 'crossLineTipRectX')
                    .attr('class', 'crossLineTipRect');
                crossLine.append('rect')
                    .attr('name', 'crossLineTipRectY')
                    .attr('class', 'crossLineTipRect');
                //tooltip label on axis text
                crossLine.append('text')
                    .attr('name', 'crossLineTipY')
                    .attr('class', 'crossLineTip');
                crossLine.append('text')
                    .attr('name', 'crossLineTipX')
                    .attr('class', 'crossLineTip');
            }

            // Graph tools that only needs to be created once in theory; all of these are data independent
            function setGraphTools(series) {
                // set z to metric names and set legend content
                z.domain(names);
                // create mouse over circle, tooltip items, lines and brush lines
                series.forEach(function (metric) {
                    if (metric.data.length === 0) return;
                    var tempColor = metric.color === null ? z(metric.name) : metric.color;
                    // main graphs
                    mainChart.append('path')
                      .attr('class', 'line ' + metric.graphClassName)
                      .style('stroke', tempColor)
                      .style('clip-path', "url('#clip_" + chartId + "')");
                    // graphs in the brush
                    context.append('path')
                        .attr('class', 'brushLine ' + metric.graphClassName + '_brushline')
                        .style('stroke', tempColor);
                    // circle on graph during mouse over
                    focus.append('circle')
                        .attr('r', circleRadius)
                        .attr('fill', tempColor)
                        .attr('class', metric.graphClassName);
                    // tooltip items
                    tipItems.append('circle')
                        .attr('r', circleRadius)
                        .attr('fill', tempColor)
                        .attr('class', metric.graphClassName);
                    tipItems.append('text')
                        .attr('class', metric.graphClassName);
                });
            }

            function mouseMove() {
                if (!currSeries || currSeries.length === 0) return;
                var datapoints = [];
                var position = d3.mouse(this);
                var positionX = position[0];
                var positionY = position[1];
                var mouseX = x.invert(positionX);
                var mouseY = y.invert(positionY);

                if(isBrushInNonEmptyRange()) {
                    currSeries.forEach(function (metric) {
                        if (metric.data.length === 0) {
                            return;
                        }
                        var data = metric.data;
                        var i = bisectDate(data, mouseX, 1);
                        var d0 = data[i - 1];
                        var d1 = data[i];
                        var d;
                        // snap the datapoint that lives in the x domain
                        if (!d0) {
                            //There is a case when d0 is outside domain but d1 is undefined, we cannot render d1
                            //we could still render d0 but make it invisible.
                            d = d1;
                        } else if (!d1) {
                            d = d0;
                            // if both data points lives in the domain, choose the closer one to the mouse position
                        } else {
                            d = mouseX - d0[0] > d1[0] - mouseX ? d1 : d0;
                        }

                        var circle = focus.select('.' + metric.graphClassName);

                        if(d[0] < x.domain()[0] || d[0] > x.domain()[1].getTime() ||d[1] < y.domain()[0] || d[1] > y.domain()[1]){
                            //outside domain
                            circle.attr('display', 'none');
                        }else{
                            circle.attr('display', null);
                        }

                        // update circle's position on each graph
                        circle
                            .attr('dataX', d[0]).attr('dataY', d[1]) //store the data
                            .attr('transform', 'translate(' + x(d[0]) + ',' + y(d[1]) + ')');

                        // check if the source is displaying based on the legend
                        var sourceInLegend = scope.sources.find(function (source) {
                            return source.graphClassName === metric.graphClassName;
                        });
                        if (sourceInLegend.displaying) {
                            datapoints.push({
                                data: d,
                                graphClassName: metric.graphClassName,
                                name: metric.name
                            });
                        }
                    });
                    // sort items in tooltip if needed
                    if (scope.menuOption.isTooltipSortOn) {
                        datapoints = datapoints.sort(function (a, b) {
                            return b.data[1] - a.data[1]
                        });
                    }

                    toolTipUpdate(tipItems, datapoints, positionX, positionY);
                }
                updateCrossLine(mouseX, mouseY, positionX, positionY);
            }

            function toolTipUpdate(group, datapoints, X, Y) {
                var XOffset = 0;
                var YOffset = 0;
                var newXOffset = 0;
                var OffsetMultiplier = -1;
                var itemsPerCol = 8;
                var circleLen = circleRadius * 2;
                if (scope.menuOption.isTooltipDetailOn) {
                    itemsPerCol = 14;
                } else if (datapoints.length < 2*itemsPerCol) {
                    itemsPerCol = Math.ceil(datapoints.length / 2);
                }

                for (var i = 0; i < datapoints.length; i++) {
                    // create a new col after every itemsPerCol
                    if (i % itemsPerCol === 0) {
                        OffsetMultiplier++;
                        YOffset = OffsetMultiplier * itemsPerCol;
                        XOffset += newXOffset;
                        newXOffset = 0;
                    }
                    // Y data point - metric specific
                    var tempData = datapoints[i].data[1];

                    // X data point - time
                    // var tempDate = new Date(datapoints[i].data[0]);
                    // tempDate = GMTon ? GMTformatDate(tempDate) : formatDate(tempDate);

                    var circle = group.select("circle." + datapoints[i].graphClassName)
                                        .attr('cy', 20 * (0.75 + i - YOffset) + Y)
                                        .attr('cx', X + tipOffset + tipPadding + circleRadius + XOffset);
                    var textLine = group.select("text." + datapoints[i].graphClassName)
                                        .attr('dy', 20 * (1 + i - YOffset) + Y)
                                        .attr('dx', X + tipOffset + tipPadding + circleLen + 2 + XOffset);

                    if (scope.menuOption.isTooltipDetailOn) {
                        textLine.text(datapoints[i].name + "   " + d3.format('0,.7')(tempData));
                    } else {
                        textLine.text(d3.format('.2s')(tempData));
                    }

                    // update XOffset if existing offset is smaller than texLine
                    var tempXOffset = textLine.node().getBBox().width + circleLen + tipOffset;
                    if (tempXOffset > newXOffset) {
                        newXOffset = tempXOffset;
                    }

                    /*
                     // keep this just in case different styles are needed for time and value
                     textLine.append('tspan')
                     .attr('class', 'timestamp')
                     .text(formatDate(new Date(datapoints[i][0])));
                     textLine.append('tspan').attr('class', 'value')
                     .attr('dx', 8)
                     .text(formatValue(datapoints[i][1]));
                     textLine.append('tspan').attr('dx', 8).text(names[i]);
                     */


                }

                var tipBounds = group.node().getBBox();
                tipBox.attr('x', X + tipOffset);
                tipBox.attr('y', Y + tipOffset);

                if (tipBounds.width === 0 || tipBounds.height === 0) {
                    // when there is no graph, make the tipBox 0 size
                    tipBox.attr('width', 0);
                    tipBox.attr('height', 0);
                } else {
                    tipBox.attr('width', tipBounds.width + 4 * tipPadding);
                    tipBox.attr('height', tipBounds.height + 2 * tipPadding);
                }

                // move tooltip on the right if there is not enough to display it on the right
                var transformAttr;
                if (X + Number(tipBox.attr('width')) > (width + marginRight) &&
                    X - Number(tipBox.attr('width')) > 0) {
                    transformAttr = 'translate(-' + (Number(tipBox.attr('width')) + 2 * tipOffset) + ')';
                } else {
                    transformAttr = null;
                }
                group.attr('transform', transformAttr);
                tipBox.attr('transform', transformAttr);
            }

            function legendCreator(names, colors, graphClassNames) {
                var tmpSources = [];
                for (var i = 0; i < names.length; i++) {
                    var tempColor = colors[i] === null ? z(names[i]) : colors[i];
                    tmpSources.push({
                        name: names[i],
                        displaying: true,
                        color: tempColor,
                        graphClassName: graphClassNames[i]
                    });
                }
                // set names into $scope for legend
                scope.sources = tmpSources;
            }

            /*  Generate cross lines at the point/cursor
             mouseX,mouseY are actual values
             X,Y are coordinates value
             */
            function updateCrossLine(mouseX, mouseY, X, Y) {
                //if (!mouseY) return; comment this to avoid some awkwardness when there is no data in selected range

                focus.select('[name=crossLineX]')
                    .attr('x1', X).attr('y1', 0)
                    .attr('x2', X).attr('y2', height);
                focus.select('[name=crossLineY]')
                    .attr('x1', 0).attr('y1', Y)
                    .attr('x2', width).attr('y2', Y);
                //add some information around the axis

                var textY;
                if(isNaN(mouseY)){ //mouseY can be 0
                    textY = "No Data";
                }else{
                    textY = d3.format('.2s')(mouseY);
                }

                focus.select('[name=crossLineTipY')
                    .attr('x', 0)
                    .attr('y', Y)
                    .attr('dx', -crossLineTipWidth)
                    .text(textY);

                //add a background to it
                var boxY = focus.select('[name=crossLineTipY]').node().getBBox();
                focus.select('[name=crossLineTipRectY]')
                    .attr('x', boxY.x - crossLineTipPadding)
                    .attr('y', boxY.y - crossLineTipPadding)
                    .attr('width', boxY.width + 2 * crossLineTipPadding)
                    .attr('height', boxY.height + 2 * crossLineTipPadding);

                var date = GMTon ? GMTformatDate(mouseX) : formatDate(mouseX);
                focus.select('[name=crossLineTipX]')
                    .attr('x', X)
                    .attr('y', 0)
                    .attr('dy', crossLineTipHeight)
                    .text(date);

                //add a background to it
                var boxX = focus.select('[name=crossLineTipX]').node().getBBox();
                focus.select('[name=crossLineTipRectX]')
                    .attr('x', boxX.x - crossLineTipPadding)
                    .attr('y', boxX.y - crossLineTipPadding)
                    .attr('width', boxX.width + 2 * crossLineTipPadding)
                    .attr('height', boxX.height + 2 * crossLineTipPadding);

            }

            //reset the brush area
            function reset() {
                svg_g.selectAll(".brush").call(brush.move, null);
                svg_g.selectAll(".brushMain").call(brush.move, null);
            }

            // //redraw the lines Axises grids
            // function redraw() {
            //     //redraw
            //     if(x.domain()[0].getTime() <= dateExtent[1] &&  x.domain()[1].getTime()>= dateExtent[0]) {
            //         svg_g.selectAll(".line").attr("d", line);//redraw the line
            //     }
            //     xAxisG.call(xAxis);  //redraw xAxis
            //     yAxisG.call(yAxis);  //redraw yAxis
            //     yAxisRG.call(yAxisR); //redraw yAxis right
            //     xGridG.call(xGrid);
            //     yGridG.call(yGrid);
            //     if (!scope.menuOption.isBrushOn) {
            //         context.attr("display", "none");
            //     }
            //     updateDateRange();
            // }

            //redraw the line with restrict
            function redraw(){
                var domainStart = x.domain()[0].getTime();
                var domainEnd = x.domain()[1].getTime();
                //redraw
                if(isBrushInNonEmptyRange()) {
                    mainChart.selectAll('path.line').attr('display', null);
                    //update the dataum and redraw the line
                    currSeries.forEach(function (metric) {
                        if (metric === null || metric.data.length === 0) return;
                        var len = metric.data.length;
                        if (metric.data[0][0] > domainEnd || metric.data[len - 1][0] < domainStart){
                            mainChart.select('path.line.' + metric.graphClassName)
                                .datum([])
                                .attr('d', line);
                            return;
                        }
                        //if this metric time range is within the x domain
                        var start = bisectDate(metric.data, x.domain()[0]);
                        if(start > 0) start-=1; //to avoid cut off issue on the edge
                        var end = bisectDate(metric.data, x.domain()[1], start) + 1; //to avoid cut off issue on the edge
                        var data = metric.data.slice(start, end + 1);

                        //only render the data within the domain
                        mainChart.select('path.line.' + metric.graphClassName)
                            .datum(data)
                            .attr('d', line); //change the datum will call d3 to redraw
                    });
                    //svg_g.selectAll(".line").attr("d", line);//redraw the line
                }else{
                    mainChart.selectAll('path.line').attr('display', 'none');
                }
                xAxisG.call(xAxis);  //redraw xAxis
                yAxisG.call(yAxis);  //redraw yAxis
                yAxisRG.call(yAxisR); //redraw yAxis right
                xGridG.call(xGrid);
                yGridG.call(yGrid);
                if (!scope.menuOption.isBrushOn) {
                    context.attr("display", "none");
                }
                updateDateRange();
                updateAnnotations();
            }

            //brushed
            function brushed() {
                // ignore the case when it is called by the zoomed function
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "zoom" )) return;
                var s = d3.event.selection || x2.range();
                x.domain(s.map(x2.invert, x2));     //rescale the domain of x axis
                                                    //invert the x value in brush axis range to the
                                                    //value in domain

                reScaleY(); //rescale domain of y axis
                //redraw
                redraw();
                //sync with zoom
                chartRect.call(zoom.transform, d3.zoomIdentity
                    .scale(width / (s[1] - s[0]))
                    .translate(-s[0], 0));

                if (brushMainG) {
                    brushMainG.call(zoom.transform, d3.zoomIdentity
                        .scale(width / (s[1] - s[0]))
                        .translate(-s[0], 0));
                }
            }

            function brushedMain() {
                var selection = d3.event.selection; //the brushMain selection
                if (selection) {
                    var start = x.invert(selection[0]);
                    var end = x.invert(selection[1]);
                    var range = end - start;
                    brushMainG.call(brushMain.move, null);
                    if (range * maxScaleExtent < x2.domain()[1] - x2.domain()[0]) return;
                    x.domain([start, end]);
                    brushG.call(brush.move, [x2(start), x2(end)]);
                }
            }

            //zoomed
            function zoomed() {
                // ignore the case when it is called by the brushed function
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "brush" || d3.event.sourceEvent.type === "end"))return;
                var t = d3.event.transform;
                x.domain(t.rescaleX(x2).domain());  //rescale the domain of x axis
                                                    //invert the x value in brush axis range to the
                                                    //value in domain

                reScaleY(); //rescale domain of y axis
                //redraw
                redraw();
                // sync the brush
                context.select(".brush").call
                (brush.move, x.range().map(t.invertX, t));

                //sync the crossLine
                var position = d3.mouse(this);
                var positionX = position[0];
                var positionY = position[1];
                var mouseX = x.invert(positionX);
                var mouseY = y.invert(positionY); //domain value
                if(isBrushInNonEmptyRange()) {
                    focus.selectAll('circle').attr('display', null)
                        .each(function (d, i) {
                        var circle = d3.select(this);
                        var dataX = circle.attr('dataX');
                        var dataY = circle.attr('dataY');
                        circle.attr('transform', 'translate(' + x(dataX) + ',' + y(dataY) + ')');

                        if(dataX < x.domain()[0] || dataX > x.domain()[1]){
                            circle.attr('display', 'none');
                        }
                    });
                }else{
                    focus.selectAll('circle').attr('display', 'none');
                }
                updateCrossLine(mouseX, mouseY, positionX, positionY);
            }

            //change brush focus range, k is the number of minutes
            function brushMinute(k) {
                return function () {
                    if (!k) k = (x2.domain()[1] - x2.domain()[0]);
                    //the unit of time value is millisecond
                    //x2.domain is the domain of total
                    var interval = k * 60000; //one minute is 60000 millisecond

                    //take current x domain value and extend it
                    var start = x.domain()[0].getTime();
                    var end = x.domain()[1].getTime();
                    var middle = (start + end) / 2;
                    start = middle - interval / 2;
                    var min = x2.domain()[0].getTime();
                    var max = x2.domain()[1].getTime();
                    if (start < min) start = min;
                    end = start + interval;
                    if (end > max) end = max;
                    context.select(".brush").call
                    (brush.move, [x2(new Date(start)), x2(new Date(end))]);
                };
            }

            //rescale YAxis based on XAxis Domain
            function reScaleY() {
                if (currSeries === "series" || !currSeries) return;
                if(agYMin !== undefined && agYMax !== undefined) return; //hard coded ymin & ymax

                var xDomain = x.domain();
                var datapoints = [];

                currSeries.forEach(function (metric) {
                    if (metric === null || metric.data.length === 0) return;
                    var len = metric.data.length;
                    if (metric.data[0][0] > xDomain[1].getTime() || metric.data[len - 1][0] < xDomain[0].getTime()) return;
                    //if this metric time range is within the xDomain
                    var start = bisectDate(metric.data, xDomain[0]);
                    var end = bisectDate(metric.data, xDomain[1], start);
                    datapoints = datapoints.concat(metric.data.slice(start, end + 1));
                });

                var extent = d3.extent(datapoints, function (d) {
                    return d[1];
                });
                var diff = extent[1] - extent[0];
                var buffer = diff * bufferRatio;
                var yMin = (agYMin === undefined) ? extent[0] - buffer : agYMin;
                var yMax = (agYMax === undefined) ? extent[1] + buffer : agYMax;

                y.domain([yMin, yMax]);

            }

            // //resize
            // function resize_() {
            //     //calculate new size for chart
            //     containerWidth = $(container).width();
            //     width = containerWidth - marginLeft - marginRight;
            //     margin = {
            //         top: marginTop,
            //         right: marginRight,
            //         bottom: containerHeight - marginTop - height,
            //         left: marginLeft
            //     };
            //     margin2 = {
            //         top: containerHeight - height2 - marginBottom,
            //         right: marginRight,
            //         bottom: marginBottom,
            //         left: marginLeft
            //     };
            //
            //     //clear every chart
            //     d3.select(container).select('svg').remove();
            //
            //     if (typeof x === "undefined") {
            //         // when resizing a graph with empty series
            //         displayEmptyGraph(container, width, height, margin, series[0])
            //     } else {
            //         var tempX = x.domain(); //remember that when resize
            //         setGraph(); //set up the chart
            //         //TODO: should not need to call this function
            //         setGraphTools(currSeries);
            //         updateGraph(currSeries); //refill the data draw the line
            //         addOverlay();
            //
            //         if (tempX[0].getTime() == x2.domain()[0].getTime() &&
            //             tempX[1].getTime() == x2.domain()[1].getTime()) {
            //             reset();
            //         } else {
            //             //restore the zoom&brush
            //             context.select(".brush").call
            //             (brush.move, [x2(tempX[0]), x2(tempX[1])]);
            //         }
            //     }
            // }

            //precise resize without removing and recreating everything
            function resize(){
                if (series === "series" || !series) {
                    return;
                }

                containerWidth = $(container).width();
                width = containerWidth - marginLeft - marginRight;

                if(width < 0) return; //it happens when click other tabs (like 'edit'/'history', the charts are not destroyed

                margin = {
                    top: marginTop,
                    right: marginRight,
                    bottom: containerHeight - marginTop - height,
                    left: marginLeft
                };
                margin2 = {
                    top: containerHeight - height2 - marginBottom,
                    right: marginRight,
                    bottom: marginBottom,
                    left: marginLeft
                };

                if (series.length > 0) {
                    var tempX = x.domain(); //remember that when resize

                    clip.attr('width', width)
                        .attr('height', height);
                    chartRect.attr('width', width);
                    //update range
                    x.range([0, width]);
                    x2.range([0, width]);

                    //update brush & zoom
                    brush.extent([
                        [0, 0],
                        [width, height2]
                    ]);
                    brushMain.extent([
                        [0, 0],
                        [width, height]
                    ]);
                    zoom.translateExtent([
                            [0, 0],
                            [width, height]
                        ])
                        .extent([
                            [0, 0],
                            [width, height]
                        ]);
                    brushG.call(brush);
                    brushMainG.call(brushMain);

                    //width related svg element
                    svg.attr('width', width + margin.left + margin.right);
                    svg_g.attr('width', width)
                        .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

                    yGrid.tickSizeInner(-width);
                    yGridG.call(yGrid);

                    yAxisRG.attr('transform', 'translate(' + width + ')')
                        .call(yAxisR);

                    svg_g.selectAll(".line").attr("d", line); //redraw the line
                    svg_g.selectAll(".brushLine").attr("d", line2); //redraw brush line

                    xAxisG.call(xAxis); //redraw xAxis
                    yAxisG.call(yAxis); //redraw yAxis
                    xGridG.call(xGrid);
                    xAxisG2.call(xAxis2);

                    // update x axis label if it's in ag options
                    if (chartOptions.xAxis!== undefined && chartOptions.xAxis.title !== undefined) {
                        mainChart.select(".xAxisLabel")
                                  .attr("transform", "translate(" + (width / 2) + " ," + (height + margin.top + xAxisLabelHeightFactor) + ")");
                    }

                    if (tempX[0].getTime() == x2.domain()[0].getTime() &&
                        tempX[1].getTime() == x2.domain()[1].getTime()) {
                        reset();
                    } else {
                        //restore the zoom&brush
                        context.select(".brush").call(brush.move, [x2(tempX[0]), x2(tempX[1])]);
                    }
                } else {
                    displayEmptyGraph(container, width, height, margin, messageToDisplay);
                }
            }


            function updateGraph(series) {
                var allDatapoints = [];
                currSeries = series;

                series.forEach(function (metric) {
                    allDatapoints = allDatapoints.concat(metric.data);
                });

                //x domain was set according to dateConfig previously
                //this shows exactly the date range defined by user instead of actual data

                dateExtent = d3.extent(allDatapoints, function (d) {
                        return d[0];
                });

                if(!startTime) startTime = dateExtent[0]; //startTime/endTime will not be 0
                if(!endTime) endTime = dateExtent[1];

                x.domain([startTime, endTime]);

                var yDomain = d3.extent(allDatapoints, function (d) {
                    return d[1];
                });

                if(agYMin !== undefined && agYMax !== undefined){
                    y.domain([agYMin, agYMax]);
                }else{
                    y.domain(yDomain);
                }

                x2.domain(x.domain());
                y2.domain(yDomain);

                series.forEach(function (metric) {
                    if (metric.data.length === 0) return;
                    mainChart.select('path.line.' + metric.graphClassName)
                        .datum(metric.data)
                        .attr('d', line);
                    context.select('path.brushLine.' + metric.graphClassName + '_brushline')
                        .datum(metric.data)
                        .attr('d', line2);
                });
                //draw the brush xAxis
                xAxisG2.call(xAxis2);
                setZoomExtent(3);

                // draw flag(s) to denote annotation mark
                updateAnnotations();
            }

            // when there is no data for series, display a message
            function displayEmptyGraph(containerName, width, height, margin, messageToDisplay) {
                if (svg) svg.remove();
                svg = d3.select(containerName).append('svg')
                    .attr('width', width + margin.left + margin.right)
                    .attr('height', height + margin.top + margin.bottom);
                svg.selectAll('text')
                    .data(messageToDisplay)
                    .enter()
                    .append("text")
                    .attr("x", margin.left + width/2)
                    .attr("y", function (d, i) {
                        return 20*i + margin.top + height/2;
                    })
                    .style("text-anchor", "middle")
                    .text(function(d){return d;});
            }

            //TODO: this doesnt work
            function updateAnnotations() {
                if (!scope || !scope.series) return;

                var flagSeries;
                if (scope.series.length === 1 && scope.series[0].flagSeries) {
                    flagSeries = scope.series[0].flagSeries.data;
                } else {
                    // TODO: do any dashboards have flag data for multiple series?
                    return;
                }

                var flagsG = d3.select('#' + chartId).select('svg').select('.flags');
                //clear previous graph element
                flagsG.selectAll(".flagItem").remove();
                var label = flagsG.selectAll(".flagItem")
                    .data(flagSeries)
                    .enter().append("g")
                    .attr("class", "flagItem")
                    .attr("transform", function (d) {
                        // x, xAxis, xAxisG
                        var x_Val = x(d.x); // d.x is timestamp of X axis
                        var y_Val = height - 35;
                        return "translate(" + x_Val + ", " + y_Val + ")";
                    });

                label.append("line")
                    .attr("y2", 35)
                    .attr("stroke-width", 2)
                    .attr("stroke", "steelblue");

                label.append("circle")
                    .attr("r", 5)
                    .attr("class", "flag");

                // TODO: add mouseover for short text description when it comes available
                // label.append("text")
                //     .attr("x", 10)
                // text is currently too large and unreadable.
                // TODO: need separate panel to satisfy use case for user to select text
                // .text(function(d) { return d.text; });
            }

            //this function add the overlay element to the graph when mouse interaction takes place
            //need to call this after drawing the lines in order to put mouse interaction overlay on top
            function addOverlay() {
                //the graph rectangle area
                chartRect = mainChart.append('rect')
                    .attr('class', 'chartOverlay')
                    .attr('width', width)
                    .attr('height', height)
                    .on('mouseover', function () {
                       mouseOverChart();
                    })
                    .on('mouseout', function () {
                        focus.style('display', 'none');
                        if (scope.menuOption.isTooltipOn) tip.style('display', 'none');
                    })
                    .on('mousemove', mouseMove)
                    .call(zoom)
                ;

                //the brush overlay
                brushG = context.append("g")
                    .attr("class", "brush")
                    .call(brush)
                    .call(brush.move, x.range()); //change the x axis range when brush area changes

                brushMainG = mainChart.append("g")//have to do this seperately, because rect svg cannot register brush
                    .attr("class", "brushMain")
                    .call(zoom)
                    .on("mousedown.zoom", null)
                    .call(brushMain)
                    .on('mouseover', function () {
                       mouseOverChart();
                    })
                    .on('mouseout', function () {
                        focus.style('display', 'none');
                        if (scope.menuOption.isTooltipOn) tip.style('display', 'none');
                    })
                    .on('mousemove', mouseMove);

                if (scope.menuOption.isBrushMainOn) {
                    brushMainG.attr('display', null);
                } else {
                    brushMainG.attr('display', 'none');
                }
                // no wheel zoom on page load
                if (!scope.menuOption.isWheelOn) {
                    chartRect.on("wheel.zoom", null);   // does not disable 'double-click' to zoom
                    brushMainG.on("wheel.zoom", null);
                }
            }

            //toggle time brush
            function toggleBrush() {
                if (scope.menuOption.isBrushOn) {
                    //disable the brush
                    svg_g.select('.context').attr('display', null);
                } else {
                    //enable the brush
                    svg_g.select('.context').attr('display', 'none');
                }
                updateStorage();
            }

            //toggle time brush
            function toggleBrushMain() {
                //enable main chart brush
                if (scope.menuOption.isBrushMainOn) {
                    brushMainG.attr('display', null);
                } else {
                    //disable main chart brush
                    brushMainG.attr('display', 'none');
                }
                updateStorage();
            }

            //toggle the mouse wheel for zoom
            function toggleWheel() {
                if (scope.menuOption.isWheelOn) {
                    chartRect.call(zoom);
                    brushMainG.call(zoom)
                        .on("mousedown.zoom", null);
                } else {
                    chartRect.on("wheel.zoom", null);
                    brushMainG.on("wheel.zoom", null);
                }
                updateStorage();
            }

            //toggle tooltip
            function toggleTooltip() {
                if (scope.menuOption.isTooltipOn) {
                    svg_g.select(".tooltip").attr("display", 'none');
                } else {
                    svg_g.select(".tooltip").attr("display", null);
                }
                updateStorage();
            }

            //date range
            function updateDateRange() {
                var start, end, str;
                if (GMTon) {
                    start = GMTformatDate(x.domain()[0]);
                    end = GMTformatDate(x.domain()[1]);
                    str = start + ' - ' + end + " (GMT/UTC)";
                } else {
                    start = formatDate(x.domain()[0]);
                    end = formatDate(x.domain()[1]);
                    var temp = (new Date()).toString();
                    var currentTimeZone = temp.substring(temp.length - 6, temp.length);
                    str = start + ' - ' + end + currentTimeZone;
                }

                // update $scope
                scope.dateRange = str;

                // update view
                d3.select('#topTb-' + chartId + ' .dateRange').text(str);
            }

            //extent, k is the least number of points in one line you want to see on the main chart view
            function setZoomExtent(k) {
                var numOfPoints = currSeries[0].data.length;
                //choose the max among all the series
                for (var i = 1; i < currSeries.length; i++) {
                    if (numOfPoints < currSeries[i].data.length) {
                        numOfPoints = currSeries[i].data.length;
                    }
                }
                if (!k || k > numOfPoints) k = 3;
                zoom.scaleExtent([1, numOfPoints / k]);
                maxScaleExtent = parseInt(numOfPoints / k);
            }

            //dynamically enable button for brush time period(1h/1d/1w/1m/1y)
            function enableBrushTime() {
                var range = x2.domain()[1] - x2.domain()[0];
                if (range > 3600000) {
                    //enable 1h button
                    $('[name=oneHour]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24) {
                    //enable 1d button
                    $('[name=oneDay]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24 * 7) {
                    //enable 1w button
                    $('[name=oneWeek]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24 * 30) {
                    //enable 1month button
                    $('[name=oneMonth]', topToolbar).prop('disabled', false);
                }
                if (range > 3600000 * 24 * 365) {
                    //enable 1y button
                    $('[name=oneYear]', topToolbar).prop('disabled', false);
                }
            }

            function isBrushInNonEmptyRange(){
                return x.domain()[0].getTime() <= dateExtent[1] &&  x.domain()[1].getTime()>= dateExtent[0];
            }

            function mouseOverChart(){
                focus.style('display', null);
                if(isBrushInNonEmptyRange()) {
                    if (scope.menuOption.isTooltipOn) tip.style('display', null);
                }else{
                    //no need to show the circle to tip
                    focus.selectAll('circle').attr('display', 'none');
                    tip.attr('display', 'none');
                }
            }

            // create graph only when there is data
            if (!series || series.length === 0) {
                //this should never happen
                console.log("Empty data from chart data processing");
            } else {
                // set up legend
                names = series.map(function(metric) { return metric.name; });
                colors = series.map(function(metric) { return metric.color; });
                graphClassNames = series.map(function(metric) { return metric.graphClassName; });
                legendCreator(names, colors, graphClassNames);
                // check if there is anything to graph
                var hasNoData, emptyReturn, invalidExpression;
                var tempSeries = [];
                for (var i = 0; i < series.length; i++) {
                    if (series[i].invalidMetric) {
                        scope.invalidSeries.push(series[i]);
                        invalidExpression = true;
                    } else if (series[i].noData) {
                        scope.noDataSeries.push(series[i]);
                        emptyReturn = true;
                    } else if (series[i].data.length === 0) {
                        hasNoData = true;
                    } else {
                        // only keep the metric that's graphable
                        tempSeries.push(series[i]);
                    }
                }
                series = tempSeries;

                if (series.length > 0) {
                    // Update graph on new metric results
                    setGraph();
                    setGraphTools(series);
                    updateGraph(series);
                    // initialize starting point for graph settings & info
                    addOverlay();
                    updateDateRange();
                    enableBrushTime();
                    reset();    //to remove the brush cover first for user the drag
                } else {
                    // generate content for no graph message
                    if (invalidExpression) {
                        messageToDisplay.push('Metric expressions do not exist in TSDB');
                        for (var i = 0; i < scope.invalidSeries.length; i ++) {
                            messageToDisplay.push(scope.invalidSeries[i].errorMessage);
                        }
                        messageToDisplay.push('(Failed metrics are labeled black in the legend)');
                    }
                    if (emptyReturn) {
                        messageToDisplay.push('Metric expressions have no return value from TSDB');
                        messageToDisplay.push('(Empty returned metrics are labeled maroon in the legend)');
                    }
                    if (hasNoData) {
                        messageToDisplay.push('No data found for metric expressions');
                        messageToDisplay.push('(Series names are shown with normal colors in the legend)');
                    }
                    displayEmptyGraph(container, width, height, margin, messageToDisplay);
                }
            }

            function updateStorage(){
                Storage.set('menuOption_' + scope.dashboardId + '_' + lineChartIdName + scope.lineChartId, scope.menuOption);
            }
            //button set up
            $('[name=reset]', topToolbar).click(reset);
            $('[name=oneHour]', topToolbar).click(brushMinute(60));
            $('[name=oneDay]', topToolbar).click(brushMinute(60*24));
            $('[name=oneWeek]', topToolbar).click(brushMinute(60*24*7));
            $('[name=oneMonth]', topToolbar).click(brushMinute(60*24*30));
            $('[name=oneYear]', topToolbar).click(brushMinute(60*24*365));

            //toggle
            $('[name=toggle-brush]', topToolbar).change(toggleBrush);
            $('[name=toggle-brush-main]', topToolbar).change(toggleBrushMain);
            $('[name=toggle-wheel]', topToolbar).change(toggleWheel);
            $('[name=toggle-tooltip]', topToolbar).change(toggleTooltip);

            //TODO improve the resize efficiency if performance becomes an issue
            element.on('$destroy', function(){
                if(lineChartIdIndex){
                    resizeJobs = [];
                    lineChartIdIndex = 0;
                }
            });
            resizeJobs.push(resize);
        }
    };
}]);
