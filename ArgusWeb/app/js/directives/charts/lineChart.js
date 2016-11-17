'use strict';

angular.module('argus.directives.charts.lineChart', [])
.directive('lineChart', [function() {

    return {
        restrict: 'E',
        replace: true,
        scope: {
            chartId: '=chartid',
            series: '=series',
            dateConfig: '=dateconfig'
        },
        templateUrl: 'js/templates/charts/topToolbar.html',
        controller: ['$scope', function($scope) {
            $scope.sources = [];
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
                    // .transition().duration(100)
                    // .style('opacity', newOpacity);
                    .style('display', displayProperty);
            }
        }],
        // compile: function (iElement, iAttrs, transclude) {},
        link: function (scope, element, attributes) {
            //TODO: figure what to put in controller, some dom modification should go in link
            // set $scope values
            scope.isWheelOn = false;
            scope.isBrushOn = true;
            scope.isBrushMainOn = false;
            scope.isTooltipOn = true;

            // ---------
            var topToolbar = $(element); //jquery selection
            var container = topToolbar.parent()[0];//real DOM

            var chartId = scope.chartId;
            var series = scope.series;
            var startTime = scope.dateConfig.startTime;
            var endTime = scope.dateConfig.endTime;
            var GMTon = scope.dateConfig.gmt;

            var maxScaleExtent = 100; //zoom in extent
            var currSeries = series;

            // Layout parameters
            var containerHeight = 320;
            var containerWidth = $("#" + chartId).width();
            var brushHeightFactor = 10;
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

            var margin = {top: marginTop,
                right: marginRight,
                bottom: containerHeight - marginTop - height,
                left: marginLeft};

            var margin2 = {top: containerHeight - height2 - marginBottom,
                right: marginRight,
                bottom: marginBottom,
                left: marginLeft};

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
            var numericalDate = '%x';   // output same as %m/%d/%Y

            var bisectDate = d3.bisector(function(d) { return d[0]; }).left;
            var formatDate = d3.timeFormat(shortDate);
            var GMTformatDate = d3.utcFormat(shortDate);

            var formatValue = d3.format(',');

            var isWheelOn = false;
            var isBrushOn = true;
            var isTooltipOn = true;

            //graph setup variables
            var x, x2, y, y2, z,
                nGridX = 7, nGridY = 5,
                xAxis, xAxis2, yAxis, yAxisR, yAxis2, xGrid, yGrid,
                line, line2, area, area2,
                brush, brushMain, zoom,
                svg, mainChart, xAxisG, xAxisG2, yAxisG, yAxisRG, xGridG, yGridG, //g
                focus, context, clip, brushG, brushMainG, chartRect, flags,//g
                tip, tipBox, tipItems,
                crossLine
                ;

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
                z = d3.scaleOrdinal(d3.schemeCategory20);

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
                    .on("start", function(){
                        mainChart.select(".chartOverlay").style("cursor", "move");
                    })
                    .on("end", function(){
                        mainChart.select(".chartOverlay").style("cursor", "crosshair");
                    })
                ;

                //Add elements to SVG
                svg = d3.select(container).append('svg')
                    .attr('width', width + margin.left + margin.right)
                    .attr('height', height + margin.top + margin.bottom)
                    .attr('id', 'svg')
                    .append('g')
                    .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')')
                ;

                mainChart = svg.append("g");

                xAxisG = mainChart.append('g')
                    .attr('class', 'x axis')
                    .attr('transform', 'translate(0,' + height + ')')
                    .call(xAxis);

                yAxisG = mainChart.append('g')
                    .attr('class', 'y axis')
                    .call(yAxis);

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
                clip = svg.append("defs").append("clipPath")
                    .attr("id", "clip")
                    .append("rect")
                    .attr("width", width)
                    .attr("height", height);

                //brush area
                context = svg.append("g")
                    .attr("class", "context")
                    // .attr("transform", "translate(0," + (height + margin.top + 10) + ")");
                    .attr("transform", "translate(0," + margin2.top + ")");

                // flags (annotations)
                flags = svg.append("g").attr("class", "flags");

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
                tip = svg.append('g')
                    .attr('class', 'tooltip')
                    .style('opacity', 1)
                    .style('display', 'none');
                tipBox = tip.append('rect')
                    .attr('rx', tipPadding)
                    .attr('ry', tipPadding);
                tipItems = tip.append('g')
                    .attr('class', 'tooltip-items');

                //focus tracking
                crossLine = focus.append('g')
                    .attr('name', 'crossLine');
                crossLine.append('line')
                    .attr('name', 'crossLineX')
                    .attr('class', 'crossLine');
                crossLine.append('line')
                    .attr('name', 'crossLineY')
                    .attr('class', 'crossLine');

                //tooltip background rect
                crossLine.append('rect')
                    .attr('name', 'crossLineTipRectX')
                    .attr('class', 'crossLineTipRect');
                crossLine.append('rect')
                    .attr('name', 'crossLineTipRectY')
                    .attr('class', 'crossLineTipRect');
                //tooltip text
                crossLine.append('text')
                    .attr('name', 'crossLineTipY')
                    .attr('class', 'crossLineTip');
                crossLine.append('text')
                    .attr('name', 'crossLineTipX')
                    .attr('class', 'crossLineTip');
            }

            // Graph tools that only needs to be created once
            function setGraphTools(series) {
                var names = series.map(function(metric) {
                    return metric.name;
                });
                var colors = series.map(function(metric) {
                    return metric.color;
                });
                var graphClassNames = series.map(function(metric) {
                    return metric.graphClassName;
                });
                z.domain(names);
                legendCreator(names, colors, graphClassNames);
                // create mouse over circle and tooltip
                series.forEach(function(metric) {
                    var tempColor = metric.color === null? z(metric.name): metric.color;
                    focus.append('circle')
                        .attr('r', circleRadius)
                        .attr('fill', tempColor)
                        .attr('class', metric.graphClassName);
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

                currSeries.forEach(function(metric) {
                    if (metric.data.length === 0) {
                        return;
                    }
                    var data = metric.data;
                    var i = bisectDate(data, mouseX, 1);
                    var d0 = data[i - 1];
                    var d1 = data[i];
                    var d;
                    if (!d0) {
                        d = d1;
                    } else if (!d1) {
                        d = d0;
                    } else {
                        d = mouseX - d0[0] > d1[0] - mouseX ? d1 : d0;
                    }
                    // update circle's position on each graph
                    focus.select('.' + metric.graphClassName)
                        .attr('dataX', d[0]).attr('dataY', d[1]) //store the data
                        .attr('transform', 'translate(' + x(d[0]) + ',' + y(d[1]) + ')');
                    //TODO: have a better implementation of this later
                    if (d3.select("." + metric.graphClassName).style("display") != 'none') {
                        datapoints.push({data: d, graphClassName: metric.graphClassName, name: metric.name});
                    }
                });
                toolTipUpdate(tipItems, datapoints, positionX, positionY);
                generateCrossLine(mouseX, mouseY, positionX, positionY);
            }

            function toolTipUpdate(group, datapoints, X, Y) {
                var XOffset = 0;
                var YOffset = 0;
                var OffsetMultiplier = -1;
                var itemsPerCol = 8;
                var circleLen = circleRadius*2;
                for (var i = 0; i < datapoints.length; i++) {
                    // create a new col after every itemsPerCol
                    if (i % itemsPerCol === 0) {
                        OffsetMultiplier++;
                        YOffset = OffsetMultiplier * itemsPerCol
                    }
                    var tempData = formatValue(datapoints[i].data[1]);
                    var tempDate = new Date(datapoints[i].data[0]);
                    tempDate = GMTon? GMTformatDate(tempDate): formatDate(tempDate);
                    var circle = group.select("circle." + datapoints[i].graphClassName);
                    var textLine = group.select("text." + datapoints[i].graphClassName);
                    circle.attr('cy', 20*(0.75 + i - YOffset) + Y)
                        .attr('cx', X + tipOffset + tipPadding + circleRadius + XOffset*OffsetMultiplier);
                    textLine.attr('dy', (20*(1 + i - YOffset)) + Y)
                        .attr('dx', X + tipOffset + tipPadding + circleLen + 2 + XOffset*OffsetMultiplier)
                        .text(tempDate + " - " + tempData);
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

                    // update XOffset if existing offset is smaller than texLine
                    var tempXOffset = textLine.node().getBBox().width + circleLen + 8;
                    if (tempXOffset > XOffset) {
                        XOffset = tempXOffset;
                    }

                }
                var tipBounds = group.node().getBBox();
                tipBox.attr('x', X + tipOffset);
                tipBox.attr('y', Y + tipOffset);
                if (tipBounds.width === 0 || tipBounds.height === 0) {
                    // when there is no graph, make the tipBox 0 size
                    tipBox.attr('width', 0);
                    tipBox.attr('height', 0);
                } else {
                    tipBox.attr('width', tipBounds.width + 4*tipPadding);
                    tipBox.attr('height', tipBounds.height + 2*tipPadding);
                }
                // move tooltip on the right if there is not enough to display it on the right
                var transformAttr;
                if (X + Number(tipBox.attr('width')) > (width + marginRight)
                    && X - Number(tipBox.attr('width')) > 0) {
                    transformAttr = 'translate(-' + (Number(tipBox.attr('width')) + 2*tipOffset) + ')';
                } else {
                    transformAttr = null;
                }
                group.attr('transform', transformAttr);
                tipBox.attr('transform', transformAttr);
            }

            function legendCreator(names, colors, graphClassNames) {
                var tmpSources = [];
                for (var i = 0; i < names.length; i++) {
                    var tempColor = colors[i] === null? z(names[i]): colors[i];
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

            //Generate cross lines at the point/cursor
            function generateCrossLine(mouseX, mouseY, X, Y) {
                if(!mouseY) return;

                focus.select('[name=crossLineX]')
                    .attr('x1', X).attr('y1', 0)
                    .attr('x2', X).attr('y2', height);
                focus.select('[name=crossLineY]')
                    .attr('x1', 0).attr('y1', Y)
                    .attr('x2', width).attr('y2', Y);
                //add some information around the axis

                focus.select('[name=crossLineTipY')
                    .attr('x', 0)
                    .attr('y', Y)
                    .attr('dx', -crossLineTipWidth)
                    .text(d3.format('.2s')(mouseY));

                //add a background to it
                var boxY = focus.select('[name=crossLineTipY]').node().getBBox();
                focus.select('[name=crossLineTipRectY]')
                    .attr('x', boxY.x - crossLineTipPadding)
                    .attr('y', boxY.y - crossLineTipPadding)
                    .attr('width', boxY.width + 2 * crossLineTipPadding)
                    .attr('height', boxY.height + 2 * crossLineTipPadding);

                var date = GMTon? GMTformatDate(mouseX): formatDate(mouseX);
                focus.select('[name=crossLineTipX]')
                    .attr('x', X)
                    .attr('y', height )
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
                svg.selectAll(".brush").call(brush.move, null);
                svg.selectAll(".brushMain").call(brush.move, null);
            }

            //redraw the lines Axises grids
            function redraw(){
                //redraw
                svg.selectAll(".line").attr("d", line);//redraw the line
                svg.select(".x.axis").call(xAxis);  //redraw xAxis
                svg.select(".y.axis").call(yAxis);  //redraw yAxis
                svg.select(".y.axis:nth-child(3)").call(yAxisR); //redraw yAxis right
                svg.select(".x.grid").call(xGrid);
                svg.select(".y.grid").call(yGrid);
                if(!isBrushOn){
                    svg.select(".context").attr("display", "none");
                }
                updateDateRange();
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

                if(brushMainG){
                    brushMainG.call(zoom.transform, d3.zoomIdentity
                        .scale(width / (s[1] - s[0]))
                        .translate(-s[0], 0));
                }
            }

            function brushedMain(){
                var selection = d3.event.selection; //the brushMain selection
                if(selection) {
                    var start = x.invert(selection[0]);
                    var end = x.invert(selection[1]);
                    var range = end - start;
                    brushMainG.call(brushMain.move, null);
                        if(range * maxScaleExtent < x2.domain()[1] - x2.domain()[0]) return;
                    x.domain([start, end]);
                    brushG.call(brush.move, [x2(start), x2(end)]);
                }
            }

            //zoomed
            function zoomed() {
                // ignore the case when it is called by the brushed function
                if (d3.event.sourceEvent && (d3.event.sourceEvent.type === "brush" || d3.event.sourceEvent.type === "end") )return;
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
                var mouseY = y.invert(positionY);//domain value
                focus.selectAll('circle').each(function(d, i){
                    var circle = d3.select(this);
                    var dataX = circle.attr('dataX');
                    var dataY = circle.attr('dataY');
                    circle.attr('transform','translate(' + x(dataX)  + ',' + y(dataY) + ')');
                });
                generateCrossLine(mouseX, mouseY, positionX, positionY);
            }

            //change brush focus range
            function brushMinute(k){
                return function(){
                    if(!k) k = (x2.domain()[1] - x2.domain()[0]);
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
            function reScaleY(){
                if(currSeries === "series" || !currSeries) return;
                var xDomain = x.domain();
                var datapoints = [];

                currSeries.forEach(function(metric){
                    if (metric !== null && metric.data.length > 0) {
                      var len = metric.data.length
                      // TODO: this generates bug when there is no data
                      if(metric.data[0][0] > xDomain[1].getTime() || metric.data[len-1][0] < xDomain[0].getTime()) return;
                      //if this metric time range is within the xDomain
                      var start = bisectDate(metric.data, xDomain[0]);
                      var end = bisectDate(metric.data, xDomain[1], start);
                      datapoints = datapoints.concat(metric.data.slice(start, end+1));
                    }
                });

                var extent = d3.extent(datapoints, function(d) {return d[1];});
                var diff = extent[1] - extent[0];
                var buffer = diff * bufferRatio;
                var yMin = extent[0] - buffer;
                if(yMin < 0) yMin = 0;
                var yMax = extent[1] + buffer;
                y.domain([yMin, yMax]);

            }

            //resize
            function resize(){
                var tempX = x.domain(); //remember that when resize
                //calculate new size for chart
                containerWidth = $(container).width();
                width = containerWidth - marginLeft - marginRight;
                margin = {top: marginTop,
                    right: marginRight,
                    bottom: containerHeight - marginTop - height,
                    left: marginLeft};
                margin2 = {top: containerHeight - height2 - marginBottom,
                    right: marginRight,
                    bottom: marginBottom,
                    left: marginLeft};

                //clear every chart
                // BUG: resize causes charts to re-attach in wrong DOM position
                d3.select(container).select('svg').remove();

                setGraph(); //set up the chart
                updateGraph(currSeries); //refill the data draw the line
                addOverlay();
                if(tempX[0].getTime() == x2.domain()[0].getTime() &&
                    tempX[1].getTime() == x2.domain()[1].getTime()) {
                    reset();
                }else{
                    //restore the zoom&brush
                    context.select(".brush").call
                    (brush.move, [x2(tempX[0]), x2(tempX[1])]);
                }
            }

            //updateGraph, update the graph with new data
            function updateGraph(series){
                if (!series) return;

                var allDatapoints = [];
                currSeries = series;

                series.forEach(function(metric) {
                    allDatapoints = allDatapoints.concat(metric.data);
                });

                x.domain(d3.extent(allDatapoints, function(d) { return d[0]; }));
                y.domain(d3.extent(allDatapoints, function(d) { return d[1]; }));
                x2.domain(x.domain());
                y2.domain(y.domain());

                svg.selectAll('.line').remove();
                svg.selectAll('.brushLine').remove();

                series.forEach(function(metric) {
                    if (metric.data.length > 0) {
                        var tempColor = metric.color === null? z(metric.name): metric.color;
                        mainChart.append('path')
                            .datum(metric.data)
                            .attr('class', metric.graphClassName + ' line')
                            .attr('d', line)
                            .style('stroke', tempColor)
                        ;
                        // graphs in the brush
                        context.append('path')
                            .datum(metric.data)
                            .attr('class', 'brushLine')
                            .attr('d', line2)
                            .style('stroke', tempColor)
                        ;
                    }

                });
                //draw the brush xAxis
                xAxisG2.call(xAxis2);
                setZoomExtent(3);

                // draw flag(s) to denote annotation mark
                updateAnnotations();
            }

            function updateAnnotations() {
                if (!scope || scope.series.length > 1 ) return;
                // TODO: empty data issue
                var flagSeries = scope.series[0].flagSeries.data;
                var flagsG = d3.select('#' + chartId).select('svg').select('.flags');
                var label = flagsG.selectAll("flagItem")
                    .data(flagSeries)
                    .enter().append("g")
                    .attr("class", "flagItem")
                    .attr("transform", function(d) {
                        // x, xAxis, xAxisG
                        var x_Val = 200;   // x(d.x); // d.x is timestamp of X axis
                        var y_Val = height - 35;
                        return "translate("+ x_Val + ", "+ y_Val +")";
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
            function addOverlay(){
                //the graph rectangle area
                chartRect = mainChart.append('rect')
                    .attr('class', 'chartOverlay')
                    .attr('width', width)
                    .attr('height', height)
                    .on('mouseover', function () {
                        focus.style('display', null);
                        if (isTooltipOn) tip.style('display', null);
                    })
                    .on('mouseout', function () {
                        focus.style('display', 'none');
                        tip.style('display', 'none');
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
                        focus.style('display', null);
                        if (isTooltipOn) tip.style('display', null);
                    })
                    .on('mouseout', function () {
                        focus.style('display', 'none');
                        if (isTooltipOn) tip.style('display', 'none');
                    })
                    .on('mousemove', mouseMove);

                if (scope.isBrushMainOn){
                    brushMainG.attr('display', null);
                } else {
                    brushMainG.attr('display', 'none');
                }
                // no wheel zoom on page load
                if (!isWheelOn){
                    chartRect.on("wheel.zoom", null);   // does not disable 'double-click' to zoom
                    brushMainG.on("wheel.zoom", null);
                }
            }

            //toggle time brush
            function toggleBrush(){
                if(isBrushOn){
                    //disable the brush
                    svg.select('.context').attr('display', 'none');
                    isBrushOn = false;
                } else {
                    //enable the brush
                    svg.select('.context').attr('display', null);
                    isBrushOn = true;
                }
            }

            //toggle time brush
            function toggleBrushMain(){
                //enable main chart brush
                if(scope.isBrushMainOn){
                    svg.select('.brushMain').attr('display', null);
                    //isBrushMainOn = false;   ---------ng-model is auto changed.
                } else {
                    //disable main chart brush
                    svg.select('.brushMain').attr('display', 'none');
                    //isBrushMainOn = true;
                }
            }

            //toggle the mouse wheel for zoom
            function toggleWheel(){
                if(isWheelOn){
                    chartRect.on("wheel.zoom", null);
                    brushMainG.on("wheel.zoom", null);
                    isWheelOn = false;
                } else {
                    chartRect.call(zoom);
                    brushMainG.call(zoom)
                        .on("mousedown.zoom", null);
                    isWheelOn = true;
                }
            }

            //toggle tooltip
            function toggleTooltip() {
                if(isTooltipOn) {
                    svg.select(".tooltip").attr("display", 'none');
                    isTooltipOn = false;
                } else {
                    svg.select(".tooltip").attr("display", null);
                    isTooltipOn = true;
                }
            }

            //date range
            function updateDateRange(){
                var start, end, str;
                if (GMTon) {
                    start = GMTformatDate(x.domain()[0]);
                    end = GMTformatDate(x.domain()[1]);
                    str = start + ' - ' + end + " (GMT/UTC)";
                } else {
                    start = formatDate(x.domain()[0]);
                    end = formatDate(x.domain()[1]);
                    // TODO: detect local time zone, display as 'PST', etc.
                    str = start + ' - ' + end;  // + " in local time zone";
                }

                // update $scope
                scope.dateRange = str;

                // update view
                d3.select('#topTb-' + chartId + ' .dateRange').text(str);
            }

            //extent, k is the least number of points in one line you want to see on the main chart view
            function setZoomExtent(k){
              //TODO: deal with empty data
                var numOfPoints= currSeries[0].data.length;
                //choose the max among all the series
                for(var i = 1; i < currSeries.length; i++){
                    if(numOfPoints < currSeries[i].data.length){
                        numOfPoints = currSeries[i].data.length;
                    }
                }
                if(!k || k > numOfPoints) k = 3;
                zoom.scaleExtent([1, numOfPoints/k]);
                maxScaleExtent = parseInt(numOfPoints/k);
            }

            //dynamically enable button for brush time period(1h/1d/1w/1m/1y)
            function enableBrushTime(){
                var range = x2.domain()[1] - x2.domain()[0];
                if(range > 3600000){
                    //enable 1h button
                    $('[name=oneHour]', topToolbar).prop('disabled', false);
                }
                if(range > 3600000 * 24){
                    //enable 1d button
                    $('[name=oneDay]', topToolbar).prop('disabled', false);
                }
                if(range > 3600000 * 24 * 7){
                    //enable 1w button
                    $('[name=oneWeek]', topToolbar).prop('disabled', false);
                }
                if(range > 3600000 * 24 * 30){
                    //enable 1month button
                    $('[name=oneMonth]', topToolbar).prop('disabled', false);
                }
                if(range > 3600000 * 24 * 365){
                    //enable 1y button
                    $('[name=oneYear]', topToolbar).prop('disabled', false);
                }
            }

            // call resize when browser size changes
            var parent = scope.$parent.$parent.$parent;
            //It is weird that the parent scope directive descending from is scope.$parent.$parent.$parent
            if(!parent.resize){
                parent.resizeJobs = [];
                parent.resize = function(){
                    parent.resizeJobs.forEach(function(resize){
                        resize();
                    });
                };
                d3.select(window).on('resize', parent.resize);
            }
            parent.resizeJobs.push(resize);

            // Update graph on new metric results
            setGraph();
            setGraphTools(series);
            updateGraph(series);

            // initialize starting point for graph settings & info
            addOverlay();
            updateDateRange();
            enableBrushTime();
            reset();    //to remove the brush cover first for user the drag

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
        }
    };
}]);
