/**
 * Created by pfu on 9/27/16.
 */
'use strict';

angular.module('argus.directives.charts.d3LineChartTest', [])
    .directive('agD3LineGraphTest', function() {
        return {
            restrict: 'E',
            replace: false,
            template: '<div>' +
            '<button id="reset" class="glyphicon glyphicon-refresh"></button>' +
            '<button id="oneHour">1h</button>' +
            '<button id="oneDay">1d</button>' +
            '</div>',
            link: function(scope, element, attrs) {
                var currSeries = attrs.series;
                // Layout parameters
                var containerHeight = 500;
                var containerWidth = element.parent().width();
                var mainChartRatio = 0.6 //ratio of height
                    , tipBoxRatio = 0.2
                    , brushChartRatio = 0.2;
                var marginTop = 20,
                    marginBottom = 100,
                    marginLeft = 40,
                    marginRight = 20;

                var width = containerWidth - marginLeft - marginRight;
                var height = parseInt((containerHeight - marginTop - marginBottom) * mainChartRatio);
                var height2 = parseInt((containerHeight - marginTop - marginBottom) * brushChartRatio);
                var margin = {top: marginTop,
                              right: marginRight,
                              bottom: containerHeight - marginTop - height,
                              left: marginLeft};

                var margin2 = {top: containerHeight - height2 - marginBottom,
                               right: marginRight,
                               bottom: marginBottom,
                               left: marginLeft};


                var tipPadding = 6;
                var crossLineTipPadding = 2;

                // Local helpers
                var bisectDate = d3.bisector(function(d) { return d[0]; }).left;
                var formatDate = d3.timeFormat('%A, %b %e, %H:%M');
                var formatValue = d3.format(',');
                var tooltipCreator = function() {};

                //graph setup variables
                var x, x2, y, y2, z,
                    nGridX = 10, nGridY = 10,
                    xAxis, xAxis2, yAxis, xGrid, yGrid,
                    line, line2,
                    brush, zoom,
                    svg, xAxisG, xAxisG2, yAxisG, xGridG, yGridG, //g
                    focus, context, clip, brushG, chartRect, //g
                    tip, tipBox, tipItems,
                    crossline
                    ;

// Base graph setup ==========================================================>
                function setGraph() {
                    x = d3.scaleTime().range([0, width]);
                    x2 = d3.scaleTime().range([0, width]); //for brush
                    y = d3.scaleLinear().range([height, 0]);
                    y2 = d3.scaleLinear().range([height2, 0]);
                    z = d3.scaleOrdinal().range(d3.schemeCategory10);
                    nGridX = 10;
                    nGridY = 10;
                    //Axis
                    xAxis = d3.axisBottom()
                        .scale(x)
                        .ticks(nGridX)
                    ;

                    xAxis2 = d3.axisBottom() //for brush
                        .scale(x2)
                        .ticks(nGridX);

                    yAxis = d3.axisLeft()
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

                    //zoom
                    zoom = d3.zoom()
                        .scaleExtent([1, Infinity])
                        .translateExtent([[0, 0], [width, height]])
                        .extent([[0, 0], [width, height]])
                        .on("zoom", zoomed);

                    //Add elements to SVG
                    svg = d3.select(element[0]).append('svg')
                        .attr('width', width + margin.left + margin.right)
                        .attr('height', height + margin.top + margin.bottom)
                        .attr('id', 'svg')
                        .append('g')
                        .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')')

                    ;

                    xAxisG = svg.append('g')
                        .attr('class', 'x axis')
                        .attr('transform', 'translate(0,' + height + ')')
                        .call(xAxis);

                    yAxisG = svg.append('g')
                        .attr('class', 'y axis')
                        .call(yAxis);

                    xGridG = svg.append('g')
                        .attr('class', 'x grid')
                        .attr('transform', 'translate(0,' + height + ')')
                        .call(xGrid);

                    yGridG = svg.append('g')
                        .attr('class', 'y grid')
                        .call(yGrid);


                    // Mouseover/tooltip setup
                    focus = svg.append('g')
                        .attr('class', 'focus')
                        .style('display', 'none');
                    focus.append('circle')
                        .attr('r', 4.5);

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
                        .attr("transform", "translate(0," + margin2.top + ")");

                    //set brush area axis
                    xAxisG2 = context.append("g")
                        .attr("class", "xBrush axis")
                        .attr("transform", "translate(0," + height2 + ")")
                        .call(xAxis2);

                    brushG = context.append("g")
                        .attr("class", "brush")
                        .call(brush)
                        .call(brush.move, x.range()); //change the x axis range when brush area changes

                    //the graph rectangle area
                    chartRect = svg.append('rect')
                        .attr('class', 'chartOverlay')
                        .attr('width', width)
                        .attr('height', height)
                        .on('mouseover', function () {
                            focus.style('display', null);
                        })
                        .on('mouseout', function () {
                            focus.style('display', 'none');
                        })
                        .on('mousemove', mousemove)
                        .call(zoom)
                    ;

                    tip = svg.append('g')
                        .attr('class', 'legend');
                    tipBox = tip.append('rect')
                        .attr('rx', tipPadding)
                        .attr('ry', tipPadding);
                    tipItems = tip.append('g')
                        .attr('class', 'legend-items');

                    //focus tracking
                    crossline = focus.append('g')
                        .attr('id', 'crossline');
                    crossline.append('line')
                        .attr('id', 'crossLineX')
                        .attr('class', 'crossLine');
                    crossline.append('line')
                        .attr('id', 'crossLineY')
                        .attr('class', 'crossLine');
                    crossline.append('text')
                        .attr('id', 'crossLineTip');
                }

                setGraph();
//graph set up done====================================================================>

                //button set up
                d3.select('#reset')
                    .on('click', reset);

                d3.select('#oneHour')
                    .on('click', brushMinute(60));

                d3.select('#oneDay')
                    .on('click', brushMinute(60*24));

                function mousemove() {
                    if (!currSeries || currSeries.length === 0) {
                        return;
                    }
                    var datapoints = [];
                    focus.selectAll('circle').remove();
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
                        var circle = focus.append('circle').attr('r', 4.5).attr('fill', z(metric.id));
                        circle.attr('transform', 'translate(' + x(d[0]) + ',' + y(d[1]) + ')');
                        datapoints.push(d);
                    });
                    tooltipCreator(tipItems, datapoints);
                    generateCrossLine(mouseY, positionX, positionY);
                }

                function newTooltipCreator(names) {
                    return function(group, datapoints) {
                        group.selectAll('text').remove();
                        group.selectAll('circle').remove();
                        for (var i = 0; i < datapoints.length; i++) {
                            var circle = group.append('circle')
                                .attr('r', 4.5)
                                .attr('fill', z(names[i]));
                            var textLine = group.append('text')
                                .attr('dy', (1.2*(i+1)) + 'em')
                                .attr('dx', 8);
                            textLine.append('tspan').attr('class', 'timestamp').text(formatDate(new Date(datapoints[i][0])));
                            textLine.append('tspan').attr('class', 'value').attr('dx', 8).text(formatValue(datapoints[i][1]));
                            textLine.append('tspan').attr('dx', 8).text(names[i]);
                            var textLineBounds = textLine.node().getBBox();
                            circle.attr('transform', 'translate(0,' + (textLineBounds.y + 9) + ')');
                        }
                        var tipBounds = group.node().getBBox();
                        tip.attr('transform', 'translate(' + (width/2 - tipBounds.width/2) + ',' + (height + 50) + ')');
                        tipBox.attr('x', tipBounds.x - tipPadding);
                        tipBox.attr('y', tipBounds.y - tipPadding);
                        tipBox.attr('width', tipBounds.width + 2*tipPadding);
                        tipBox.attr('height', tipBounds.height + 2*tipPadding);
                    };
                }

                //Generate cross lines at the point/cursor
                function generateCrossLine(mouseY, X, Y) {
                    if(!mouseY) return;
                    focus.select('#crossLineX')
                        .attr('x1', X).attr('y1', 0)
                        .attr('x2', X).attr('y2', height);
                    focus.select('#crossLineY')
                        .attr('x1', 0).attr('y1', Y)
                        .attr('x2', width).attr('y2', Y);
                    //add some information around the cross point
                    focus.select('#crossLineTip')
                        .attr('x', X + crossLineTipPadding)
                        .attr('y', Y - crossLineTipPadding)
                        .text(d3.format('.2f')(mouseY));

                }


                //reset the brush area
                function reset() {
                    d3.selectAll(".brush").call(brush.move, null);
                }


                //redraw the lines Axises grids
                function redraw(){
                    //redraw
                    svg.selectAll(".line").attr("d", line);//redraw the line
                    svg.select(".x.axis").call(xAxis);  //redraw xAxis
                    svg.select(".y.axis").call(yAxis);  //redraw yAxis
                    svg.select(".x.grid").call(xGrid);
                    svg.select(".y.grid").call(yGrid);
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
                    svg.select(".chartOverlay").call(zoom.transform, d3.zoomIdentity
                        .scale(width / (s[1] - s[0]))
                        .translate(-s[0], 0));

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

                    //sync the crossline
                    var position = d3.mouse(this);
                    var positionX = position[0];
                    var positionY = position[1];
                    var mouseY = y.invert(positionY);//domain value
                    generateCrossLine(mouseY, positionX, positionY);
                }

                //change brush focus range
                function brushMinute(k){
                    return function(){
                        if(!k) k = (x2.domain()[1] - x2.domain()[0]);
                        //the unit of time value is millisecond
                        //x2.domain is the domain of total
                        var interval = k * 60000; //one minute is 60000 millisecond
                        var scale = (x2.domain()[1].getTime() - x2.domain()[0].getTime()) / interval;
                        //rescale x axis
                        var start = x.domain()[0];
                        var end = new Date(start.getTime() + interval);
                        x.domain([start, end]);
                        // sync the brush
                        var start2 = x2.range()[0];
                        var end2 = start2 + (x2.range()[1] - x2.range()[0]) / scale;
                        context.select(".brush").call
                        (brush.move, [start2, end2]);
                    }
                }

                //rescale YAxis based on XAxis Domain
                function reScaleY(){
                    if(currSeries === "series" || !currSeries) return;
                    var xDomain = x.domain();
                    var start = bisectDate(currSeries[0].data, xDomain[0]);
                    var end = bisectDate(currSeries[0].data, xDomain[1], start);
                    var datapoints = [];
                    currSeries.forEach(function(metric){
                        datapoints = datapoints.concat(metric.data.slice(start, end+1));
                    });
                    y.domain(d3.extent(datapoints, function(d) {return d[1];}));
                }

                //resize
                function resize(){
                    var tempX = x.domain(); //remember that when resize
                    //calculate new size for chart
                    containerWidth = element.parent().width();
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
                    d3.select('svg').remove();
                    setGraph(); //set up the chart
                    updateGraph(currSeries); //refill the data draw the line

                    //restore the zoom&brush
                    context.select(".brush").call
                    (brush.move, [x2(tempX[0]), x2(tempX[1])]);
                }
                d3.select(window).on('resize', resize);


                //updateGraph
                function updateGraph(series){
                    if (!series) return;

                    var allDatapoints = [];
                    var names = series.map(function(metric) { return metric.id; });
                    var svg = d3.select('svg').select('g');

                    currSeries = series;

                    series.forEach(function(metric) {
                        metric.data.sort(function(a, b) {
                            return a[0] - b[0];
                        });
                        allDatapoints = allDatapoints.concat(metric.data);
                    });

                    tooltipCreator = newTooltipCreator(names);
                    x.domain(d3.extent(allDatapoints, function(d) { return d[0]; }));
                    y.domain(d3.extent(allDatapoints, function(d) { return d[1]; }));
                    z.domain(names);
                    x2.domain(x.domain());
                    y2.domain(y.domain());

                    svg.selectAll('.line').remove();
                    svg.selectAll('.brushLine').remove();

                    series.forEach(function(metric) {
                        svg.append('path')
                            .datum(metric.data)
                            .attr('class', 'line')
                            .attr('d', line)
                            .style('stroke', z(metric.id));

                        context.append('path')
                            .datum(metric.data)
                            .attr('class', 'brushLine')
                            .attr('d', line2)
                            .style('stroke', z(metric.id));
                    });

                }

                // Update graph on new metric results
                scope.$watch(attrs.series, function(series) {
                    updateGraph(series);
                });
            }
        };
    });
