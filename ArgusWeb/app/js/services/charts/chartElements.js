/**
 * Created by liuxizi.xu on 3/28/17.
 */

'use strict';
/*global angular:false, d3:false, $:false  */

angular.module('argus.services.charts.elements', [])
.service('ChartElementService', ['ChartToolService', 'UtilService', function(ChartToolService, UtilService) {
	var nGridXSmall = 3;
	var nGridYSmall = 3;
	var nGridX = 7;
	var xAxisLabelHeightFactor = 15;
	var tipOffset = 8;
	var tipPadding = 3;
	var circleRadius = 4.5;
	var circleLen = circleRadius * 2;
	var itemsPerCol = 12; // for tooltip
	var crossLineTipWidth = 35;
	var crossLineTipHeight = 15;
	var crossLineTipPadding = 3;
	var bufferRatio = 0.2; // the ratio of buffer above/below max/min on yAxis for better showing experience
	var snappingFactor = 0.1;

	var setGraphColorStyle = function (graph, color, chartType, opacity) {
		switch (chartType) {
			case 'area':
				graph.style('fill', color).style('opacity', opacity);
				break;
			// case 'line':
			default:
				graph.style('stroke', color);
		}
	};

	// pre populate the elements
	this.createAxisElements = function (x, y, isSmallChart, yAxisConfig) {
		var xAxis, yAxis, yAxisR,
			currentnGridX, currentnGridY;

		if (isSmallChart) {
			currentnGridX = nGridX;
			currentnGridY = yAxisConfig.numTicksYaxis;
		} else {
			currentnGridX = nGridXSmall;
			currentnGridY = nGridYSmall;
		}

		xAxis = d3.axisBottom()
			.scale(x)
			.ticks(currentnGridX);

		yAxis = d3.axisLeft()
			.scale(y)
			.ticks(currentnGridY)
			.tickFormat(d3.format(yAxisConfig.formatYaxis));

		yAxisR = d3.axisRight()
			.scale(y)
			.ticks(currentnGridY)
			.tickFormat(d3.format(yAxisConfig.formatYaxis));

		return {
			xAxis: xAxis,
			yAxis: yAxis,
			yAxisR: yAxisR
		};
	};

	this.createGridElements = function (x, y, sizeInfo, isSmallChart, numTicksYaxis) {
		var currentnGridX, currentnGridY;

		if (isSmallChart) {
			currentnGridX = nGridX;
			currentnGridY = numTicksYaxis;
		} else {
			currentnGridX = nGridXSmall;
			currentnGridY = nGridYSmall;
		}
		var xGrid = d3.axisBottom()
			.scale(x)
			.ticks(currentnGridX)
			.tickSizeInner(-sizeInfo.height);

		var yGrid = d3.axisLeft()
			.scale(y)
			.ticks(currentnGridY)
			.tickSizeInner(-sizeInfo.width);

		return {
			xGrid: xGrid,
			yGrid: yGrid
		};
	};

	this.createBushElements = function (timeInfo, sizeInfo, isSmallChart, chartType, brushFunction, yScaleType, yScaleConfigValue) {
		// axis and ticks
		var currentnGridX = isSmallChart? nGridXSmall: nGridX;

		var brushSizeInfo = {
			width: sizeInfo.width,
			height: sizeInfo.height2
		};
		var xy = ChartToolService.getXandY(timeInfo, brushSizeInfo, yScaleType, yScaleConfigValue);
		var x2 = xy.x;
		var y2 = xy.y;

		var xAxis2 = d3.axisBottom()
			.scale(x2)
			.ticks(currentnGridX);
		// graphs in brush
		var brushGraph = this.createGraph(x2, y2, chartType);

		// actual brush
		var brush = d3.brushX()
			.extent([[0, 0], [sizeInfo.width, sizeInfo.height2]])
			.on('brush end', brushFunction);

		return {
			x: x2,
			y: y2,
			xAxis: xAxis2,
			graph: brushGraph,
			brush: brush
		};
	};

	this.createMainBush = function (sizeInfo, brushFunction) {
		var brushMain = d3.brushX()
			.extent([[0, 0], [sizeInfo.width, sizeInfo.height]])
			.on('end', brushFunction);
		return brushMain;
	};

	this.createLine = function (x, y) {
		var line = d3.line()
			.x(function (d) {
				return UtilService.validNumberChecker(x(d[0]));
			})
			.y(function (d) {
				return UtilService.validNumberChecker(y(d[1]));
			});
		return line;
	};

	this.createArea = function (x, y) {
		var area = d3.area()
			.x(function (d) {
				return UtilService.validNumberChecker(x(d[0]));
			})
			.y1(function (d) {
				return UtilService.validNumberChecker(y(d[1]));
			});
		area.y0(y(0));
		return area;
	};
	this.createScatter = function (x, y) {
		// does not actually create a graph element
		return {x: x, y: y};
	};

	this.createGraph = function (x, y, chartType) {
		var graphElement;
		switch (chartType) {
			case 'scatter':
				graphElement = graphElement = this.createScatter(x, y);
				break;
			case 'area':
				graphElement = this.createArea(x, y);
				break;
			// case 'line':
			default:
				graphElement = this.createLine(x, y);
		}
		return graphElement;
	};

	this.createZoom = function (sizeInfo, zoomFunction, chart) {
		var zoom = d3.zoom()
			.scaleExtent([1, Infinity])
			.translateExtent([[0, 0], [sizeInfo.width, sizeInfo.height]])
			.extent([[0, 0], [sizeInfo.width, sizeInfo.height]])
			.on('zoom', zoomFunction)
			.on('start', function () {
				chart.select('.chartOverlay').style('cursor', 'move');
			})
			.on('end', function () {
				chart.select('.chartOverlay').style('cursor', 'crosshair');
			});
		return zoom;
	};

	// generate main containers
	this.generateMainChartElements = function (sizeInfo, container) {
		var svg = d3.select(container).append('svg')
			.attr('width', sizeInfo.width + sizeInfo.margin.left + sizeInfo.margin.right)
			.attr('height', sizeInfo.height + sizeInfo.margin.top + sizeInfo.margin.bottom);

		var svg_g = svg.append('g')
			.attr('class', 'inner_Svg_g')
			.attr('transform', 'translate(' + sizeInfo.margin.left + ',' + sizeInfo.margin.top + ')');

		var mainChart = svg_g.append('g').attr('class', 'mainChart');

		return {
			svg: svg,
			svg_g: svg_g,
			mainChart: mainChart
		};
	};

	// when there is an empty graph
	this.appendEmptyGraphMessage = function (sizeInfo, svg, containerName, messages) {
		if (svg) svg.remove();
		svg = d3.select(containerName).append('svg')
			.attr('width', sizeInfo.width + sizeInfo.margin.left + sizeInfo.margin.right)
			.attr('height', sizeInfo.height + sizeInfo.margin.top + sizeInfo.margin.bottom);
		svg.selectAll('text')
			.data(messages)
			.enter()
			.append('text')
			.attr('x', sizeInfo.margin.left + sizeInfo.width/2)
			.attr('y', function (d, i) {
				return 20*i + sizeInfo.margin.top;
			})
			.style('text-anchor', 'middle')
			.style('font-size', '12px')
			.text(function(d){return d;});
		return svg;
	};

	// add elements to the main containers
	this.appendAxisElements = function (sizeInfo, chart, axises, chartOptionXAxis, chartOptionYAxis) {
		var xAxisG = chart.append('g')
			.attr('class', 'x axis')
			.attr('transform', 'translate(0,' + sizeInfo.height + ')')
			.call(axises.xAxis);
		var yAxisG = chart.append('g')
			.attr('class', 'y axis')
			.call(axises.yAxis);
		var yAxisRG = chart.append('g')
			.attr('class', 'y axis')
			.attr('transform', 'translate(' + sizeInfo.width + ')')
			.call(axises.yAxisR);
		// axis labels
		if (chartOptionXAxis !== undefined && chartOptionXAxis.title !== undefined) {
			chart.append('text')
				.attr('class', 'xAxisLabel')
				.attr('transform', 'translate(' + (sizeInfo.width / 2) + ' ,' + (sizeInfo.height + sizeInfo.margin.top + xAxisLabelHeightFactor) + ')')
				.style('text-anchor', 'middle')
				.style('font-size', 12)
				.text(chartOptionXAxis.title.text);
		}
		if (chartOptionYAxis!== undefined && chartOptionYAxis.title !== undefined) {
			chart.append('text')
				.attr('class', 'yAxisLabel')
				.attr('transform', 'rotate(-90)')
				.attr('y', 0 - sizeInfo.margin.left)
				.attr('x',0 - (sizeInfo.height / 2))
				.attr('dy', '1em')
				.style('text-anchor', 'middle')
				.style('font-size', 12)
				.text(chartOptionYAxis.title.text);
		}
		return {
			xAxisG: xAxisG,
			yAxisG: yAxisG,
			yAxisRG: yAxisRG
		};
	};

	this.appendGridElements = function (sizeInfo, chart, grids) {
		var xGridG = chart.append('g')
			.attr('class', 'x grid')
			.attr('transform', 'translate(0,' + sizeInfo.height + ')')
			.call(grids.xGrid);
		var yGridG = chart.append('g')
			.attr('class', 'y grid')
			.call(grids.yGrid);
		return {
			xGridG: xGridG,
			yGridG: yGridG
		};
	};

	this.appendClip = function (sizeInfo, svg_g, chartId) {
		var clip = svg_g.append('defs').append('clipPath')
			.attr('name','clip')
			.attr('id', 'clip_' + chartId)
			.append('rect')
			.attr('width', sizeInfo.width)
			.attr('height', sizeInfo.height);
		return clip;
	};

	this.appendFlagsElements = function (svg_g, chartId) {
		var flags = svg_g.append('g').attr('class', 'flags');
		var flagsG = d3.select('#' + chartId).select('svg').select('.flags');
		var labelTip = d3.tip().attr('class', 'd3-tip').offset([-10, 0]);
		d3.select('#' + chartId).select('svg').call(labelTip);
		return {
			// flags: flags,
			flagsG: flagsG,
			labelTip: labelTip
		};
	};

	this.appendBrushWithXAxisElements = function (sizeInfo, svg_g, xAxis2) {
		var context = svg_g.append('g')
			.attr('class', 'context')
			.attr('transform', 'translate(0,' + sizeInfo.margin2.top + ')');
		var xAxisG2 = context.append('g')
			.attr('class', 'xBrush axis')
			.attr('transform', 'translate(0,' + sizeInfo.height2 + ')')
			.call(xAxis2);
		return {
			context: context,
			xAxisG2: xAxisG2
		};
	};

	this.appendFocus = function (chart) {
		var focus = chart.append('g')
			.attr('class', 'focus')
			.style('display', 'none');
		return focus;
	};

	this.appendCrossLine = function (focus) {
		var crossLine = focus.append('g')
			.attr('name', 'crossLine');
		crossLine.append('line')
			.attr('name', 'crossLineX')
			.attr('class', 'crossLine');
		crossLine.append('line')
			.attr('name', 'crossLineY')
			.attr('class', 'crossLine crossLineY');
		// axis label background
		crossLine.append('rect')
			.attr('name', 'crossLineTipRectX')
			.attr('class', 'crossLineTipRect');
		crossLine.append('rect')
			.attr('name', 'crossLineTipRectY')
			.attr('class', 'crossLineTipRect crossLineY');
		// axis label text
		crossLine.append('text')
			.attr('name', 'crossLineTipX')
			.attr('class', 'crossLineTip');
		crossLine.append('text')
			.attr('name', 'crossLineTipY')
			.attr('class', 'crossLineTip crossLineY');

		return crossLine;
	};

	this.appendTooltipElements = function (svg_g) {
		var tooltip = svg_g.append('g')
			.attr('class', 'tooltip')
			.style('opacity', 1)
			.style('display', 'none');
		var tipBox = tooltip.append('rect')
			.attr('rx', tipPadding)
			.attr('ry', tipPadding);
		var tipItems = tooltip.append('g')
			.attr('class', 'tooltip-items');

		return {
			tooltip: tooltip,
			tipBox: tipBox,
			tipItems: tipItems
		};
	};

	// add new elements for each sources
	this.renderLineGraph = function (chart, color, metric, line, chartId) {
		chart.append('path')
			.attr('class', 'line ' + metric.graphClassName)
			.style('stroke', color)
			.style('clip-path', "url('#clip_'" + chartId + "'')")
			.datum(metric.data)
			.attr('d', line);
	};

	this.renderAreaGraph = function (chart, color, metric, area, chartId) {
		chart.append('path')
			.attr('class', 'area ' + metric.graphClassName)
			.style('fill', color)
			.style('clip-path', "url('#clip_'" + chartId + "'')")
			.datum(metric.data)
			.attr('d', area);
	};

	this.renderScatterGraph = function (chart, color, metric) {
		chart.selectAll('.dot')
			.data(metric.data)
			.enter().append('circle')
			.attr('class', 'dot ' + metric.graphClassName)
			.style('fill', color).style('opacity', 0.7).attr('r', circleRadius * 0.7);
	};

	this.renderGraph = function (chart, color, metric, graph, chartId, chartType, opacity) {
		if (chartType === 'scatter') {
			this.renderScatterGraph(chart, color, metric, graph, chartId);
		} else {
			var newGraph = chart.append('path')
				.attr('class', chartType + ' ' + metric.graphClassName)
				.style('clip-path', "url('#clip_" + chartId + "')")
				.datum(metric.data)
				.attr('d', graph);
			setGraphColorStyle(newGraph, color, chartType, opacity);
		}
	};

	this.renderBrushLineGraph = function (context, color, metric, line2) {
		context.append('path')
			.attr('class', 'brushLine ' + metric.graphClassName + '_brushLine')
			.style('stroke', color)
			.datum(metric.data)
			.attr('d', line2);
	};

	this.renderBrushAreaGraph = function (context, color, metric, area2) {
		context.append('path')
			.attr('class', 'brushArea ' + metric.graphClassName + '_brushArea')
			.style('fill', color)
			.datum(metric.data)
			.attr('d', area2);
	};

	this.renderBrushScatterGraph = function (context, color, metric) {
		context.selectAll('.dot')
			.data(metric.data)
			.enter().append('circle')
			.attr('class', 'brushDot ' + metric.graphClassName + '_brushDot')
			.style('fill', color)
			.attr('r', 1.5);
	};


	this.renderBrushGraph = function (context, color, metric, graph2, chartType, opacity) {
		var cappedChartTypeStr = UtilService.capitalizeString(chartType);
		if (chartType === 'scatter') {
			this.renderBrushScatterGraph(context, color, metric, graph2);
		} else {
			var newGraph = context.append('path')
				.attr('class', 'brush' + cappedChartTypeStr + ' ' + metric.graphClassName + '_brush' + cappedChartTypeStr)
				.datum(metric.data)
				.attr('d', graph2);
			setGraphColorStyle(newGraph, color, chartType, opacity);
		}
	};

	this.renderFocusCircle = function (focus, color, className) {
		focus.append('circle')
			.attr('r', circleRadius * 1.1)
			.attr('fill', color)
			.attr('class', className);
	};

	this.renderTooltip = function (tipItems, color, className) {
		tipItems.append('circle')
			.attr('r', circleRadius)
			.attr('fill', color)
			.attr('class', className);
		tipItems.append('text')
			.attr('class', className);
	};

	this.renderAnnotationsLabels = function (flags, labelTip, color, className, dataPoint, dateFormatter) {
		// add the info box while hovering over
		var label = flags.append('g')
			.attr('class', 'flagItem ' + className)
			.attr('id', className + dataPoint.flagID)
			.style('stroke', color)
			.on('mouseover', function() {
				// add timestamp to the annotation label
				var tempTimestamp = dateFormatter(dataPoint.x);
				tempTimestamp =  '<strong>' + tempTimestamp + '</strong><br/>' + dataPoint.text;
				labelTip.style('border-color', color).html(tempTimestamp);
				labelTip.show();
				// prevent annotation label goes outside of the view on the  side
				if (parseInt(labelTip.style('left')) < 15) labelTip.style('left', '15px');
			})
			.on('mouseout', labelTip.hide);

		// add the pin on the graph
		label.append('line')
			.attr('y2', 35)
			.attr('stroke-width', 2);
		label.append('circle')
			.attr('r', 8)
			.attr('class', 'flag');
		label.append('text')
			.attr('dy', 4)
			.style('text-anchor', 'middle')
			.style('stroke', 'black')
			.text(dataPoint.title);
	};

	// add overlay stuff (do this last during the rendering process since these will be on top)
	this.appendChartRect = function (sizeInfo, chart, mouseOverFunction, mouseOutFunction, mouseMoveFunction, zoom) {
		var chartRect = chart.append('rect')
			.attr('class', 'chartOverlay')
			.attr('width', sizeInfo.width)
			.attr('height', sizeInfo.height)
			.on('mouseover', mouseOverFunction)
			.on('mouseout', mouseOutFunction)
			.on('mousemove', mouseMoveFunction)
			.call(zoom);
		return chartRect;
	};

	this.appendBrushOverlay = function (context, brush, xRange) {
		var brushG = context.append('g')
			.attr('class', 'brush')
			.call(brush)
			.call(brush.move, xRange); //change the x axis range when brush area changes
		return brushG;
	};

	this.appendMainBrushOverlay = function (chart, mouseOverFunction, mouseOutFunction, mouseMoveFunction, zoom, brush) {
		//have to do this seperately, because rect svg cannot register brush
		var brushMainG = chart.append('g')
			.attr('class', 'brushMain')
			.call(zoom)
			.on('mousedown.zoom', null)
			.call(brush)
			.on('mouseover', mouseOverFunction)
			.on('mouseout', mouseOutFunction)
			.on('mousemove', mouseMoveFunction);
		return brushMainG;
	};

	// mouse related
	this.getMousePositionData = function (x, y, mouse) {
		// mouseX, mouseY are actual values
		// positionX, positionY are coordinates value
		var positionX = mouse[0];
		var positionY = mouse[1];
		var mouseX = x.invert(positionX);
		var mouseY = y.invert(positionY);
		return {
			mouseX: mouseX,
			mouseY: mouseY,
			positionX: positionX,
			positionY: positionY
		};
	};

	this.updateFocusCirclesAndTooltipItems = function (focus, tipItems, series, sources, x, y, mouseX) {
		var datapoints = [];
		series.forEach(function (metric, index) {
			var circle = focus.select('.' + metric.graphClassName);
			if (metric.data.length === 0 || !sources[index].displaying) {
				// if the metric has no data or is toggled to hide
				circle.style('display', 'none');
				tipItems.selectAll('.' + metric.graphClassName).style('display', 'none');
			} else {
				var data = metric.data;
				var i = ChartToolService.bisectDate(metric.data, mouseX, 1);
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

				// set a snapping limit for graph
				var notInSnappingRange = Math.abs(mouseX - d[0]) > ((x.domain()[1] - x.domain()[0]) * snappingFactor);
				var displayProperty = circle.attr('displayProperty');
				if (ChartToolService.isNotInTheDomain(d[0], x.domain()) ||
					ChartToolService.isNotInTheDomain(d[1], y.domain()) ||
					notInSnappingRange) {
					//outside domain
					circle.style('display', 'none');
					displayProperty = 'none';
				} else {
					circle.style('display', null);
				}
				tipItems.selectAll('.' + metric.graphClassName).style('display', displayProperty);
				// update circle's position on each graph
				var newX = UtilService.validNumberChecker(x(d[0]));
				var newY = UtilService.validNumberChecker(y(d[1]));
				circle.attr('dataX', d[0]).attr('dataY', d[1]) //store the data
					.attr('transform', 'translate(' + newX + ',' + newY + ')');
				if (displayProperty !== 'none') {
					datapoints.push({
						data: d,
						graphClassName: metric.graphClassName,
						name: metric.name
					});
				}
			}
		});
		return datapoints;
	};

	this.updateTooltipItemsContent = function (sizeInfo, tooltipConfig, tipItems, tipBox, datapoints, mousePositionData) {
		var XOffset = 0;
		var YOffset = 0;
		var newXOffset = 0;
		var OffsetMultiplier = -1;
		// update tipItems (circle, source name, and data)
		for (var i = 0; i < datapoints.length; i++) {
			// create a new col after every itemsPerCol
			if (i % itemsPerCol === 0) {
				OffsetMultiplier++;
				YOffset = OffsetMultiplier * itemsPerCol;
				XOffset += newXOffset;
				newXOffset = 0;
			}
			tipItems.select('circle.' + datapoints[i].graphClassName)
				.attr('cy', 20 * (0.75 + i - YOffset) + mousePositionData.positionY)
				.attr('cx', mousePositionData.positionX + tipOffset + tipPadding + circleRadius + XOffset);
			var textLine = tipItems.select('text.' + datapoints[i].graphClassName)
				.attr('dy', 20 * (1 + i - YOffset) + mousePositionData.positionY)
				.attr('dx', mousePositionData.positionX + tipOffset + tipPadding + circleLen + 2 + XOffset);
			var dataFormat = tooltipConfig.rawTooltip ? ChartToolService.rawDataFormat : tooltipConfig.customTooltipFormat;
			var name = UtilService.trimMetricName(datapoints[i].name, tooltipConfig.leadingNum, tooltipConfig.trailingNum);
			var tempData = datapoints[i].data[1];
			textLine.text(name + ' -- ' + d3.format(dataFormat)(tempData));
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
		// update tipBox
		var tipBounds = tipItems.node().getBBox();
		tipBox.attr('x', mousePositionData.positionX + tipOffset);
		tipBox.attr('y', mousePositionData.positionY + tipOffset);
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
		if (mousePositionData.positionX + Number(tipBox.attr('width')) > (sizeInfo.width + sizeInfo.margin.right) &&
			mousePositionData.positionX - Number(tipBox.attr('width')) > 0) {
			transformAttr = 'translate(-' + (Number(tipBox.attr('width')) + 2 * tipOffset) + ')';
		} else {
			transformAttr = null;
		}
		tipItems.attr('transform', transformAttr);
		tipBox.attr('transform', transformAttr);
	};

	this.updateCrossLines = function (sizeInfo, dateFormatter, formatYaxis, focus, mousePositionData) {
		/*  Generate cross lines at the point/cursor
		 */

		//if (!mouseY) return; comment this to avoid some awkwardness when there is no data in selected range

		// update crossLineX
		focus.select('[name=crossLineX]')
			.attr('x1', mousePositionData.positionX).attr('y1', 0)
			.attr('x2', mousePositionData.positionX).attr('y2', sizeInfo.height);
		var date = dateFormatter(mousePositionData.mouseX);
		focus.select('[name=crossLineTipX]')
			.attr('x', mousePositionData.positionX)
			.attr('y', 0)
			.attr('dy', crossLineTipHeight)
			.text(date);
		var boxX = focus.select('[name=crossLineTipX]').node().getBBox(); // add background
		focus.select('[name=crossLineTipRectX]')
			.attr('x', boxX.x - crossLineTipPadding)
			.attr('y', boxX.y - crossLineTipPadding)
			.attr('width', boxX.width + 2 * crossLineTipPadding)
			.attr('height', boxX.height + 2 * crossLineTipPadding);
		// update crossLineY if needed
		if(mousePositionData.mouseY !==  undefined && mousePositionData.positionY !== undefined) {
			focus.select('[name=crossLineY]')
				.attr('x1', 0).attr('y1', mousePositionData.positionY)
				.attr('x2', sizeInfo.width).attr('y2', mousePositionData.positionY);
			var textY = isNaN(mousePositionData.mouseY) ? 'No Data' : d3.format(formatYaxis)(mousePositionData.mouseY);
			focus.select('[name=crossLineTipY')
				.attr('x', 0)
				.attr('y', mousePositionData.positionY)
				.attr('dx', -crossLineTipWidth)
				.text(textY);
			var boxY = focus.select('[name=crossLineTipY]').node().getBBox(); // add a background
			focus.select('[name=crossLineTipRectY]')
				.attr('x', boxY.x - crossLineTipPadding)
				.attr('y', boxY.y - crossLineTipPadding)
				.attr('width', boxY.width + 2 * crossLineTipPadding)
				.attr('height', boxY.height + 2 * crossLineTipPadding);
		}
	};

	this.updateFocusCirclesToolTipsCrossLines = function (sizeInfo, dateFormatter, formatYaxis, tooltipConfig, focus, tipItems, tipBox, series, sources, x, y, mousePositionData, brushInNonEmptyRange) {
		var datapoints;
		if (brushInNonEmptyRange) {
			datapoints = this.updateFocusCirclesAndTooltipItems(focus, tipItems, series, sources, x, y, mousePositionData.mouseX);
			// sort items in tooltip if needed
			if (tooltipConfig.isTooltipSortOn) {
				datapoints = datapoints.sort(function (a, b) {
					return b.data[1] - a.data[1];
				});
			}
			this.updateTooltipItemsContent(sizeInfo, tooltipConfig, tipItems, tipBox, datapoints, mousePositionData);
		}
		this.updateCrossLines(sizeInfo, dateFormatter, formatYaxis, focus, mousePositionData);
	};

	this.updateFocusCirclesPositionWithZoom = function (x, y, focus, brushInNonEmptyRange) {
		if (brushInNonEmptyRange) {
			focus.selectAll('circle')
				.each(function () {
					var circle = d3.select(this);
					var displayProperty = circle.attr('displayProperty');
					var dataX = circle.attr('dataX');
					var dataY = circle.attr('dataY');

					circle.attr('transform', 'translate(' + x(dataX) + ',' + y(dataY) + ')')
						.style('display', displayProperty);

					if (ChartToolService.isNotInTheDomain(dataX, x.domain())) {
						circle.style('display', 'none');
					}
				});
		} else {
			// nothing needs to be shown
			focus.selectAll('circle').style('display', 'none');
		}
	};

	this.updateAnnotations = function (series, sources, x, flagsG, height) {
		if (series === undefined) return;
		series.forEach(function(metric, index) {
			if (metric.flagSeries === undefined) return;
			var flagSeries = metric.flagSeries.data;
			flagSeries.forEach(function(d) {
				var label = flagsG.select('#' + metric.graphClassName + d.flagID);
				// d.x is timestamp of X axis and sometimes it can be in second instead of millisecond
				var dx = UtilService.epochTimeMillisecondConverter(d.x);
				var x_Val = x(dx);
				var y_Val = height - 35;
				// dont render flag if it's outside of the range; similar to focus circle
				if (ChartToolService.isNotInTheDomain(dx, x.domain())) {
					label.style('display', 'none');
				} else {
					var displayProperty = sources[index].displaying? null: 'none';
					label.style('display', displayProperty);
					label.attr('transform', 'translate(' + x_Val + ', ' + y_Val + ')');
				}
			});
		});
	};

	this.updateDateRangeLabel = function (dateFormatter, isGMT, chartId, x) {
		if (x === undefined) return;
		var start = dateFormatter(x.domain()[0]);
		var end = dateFormatter(x.domain()[1]);
		var temp = (new Date()).toString();
		var timeZoneInfo = isGMT? ' (GMT/UTC)': temp.substring(temp.length - 6, temp.length);
		var str = start + ' - ' + end + timeZoneInfo;
		// TODO: this should be done in angular with 2 way data binding update view
		$('#date-range-' + chartId).text(str);
	};

	this.setBrushInTheMiddleWithMinutes = function (k, x, x2, context, brush) {
		// change brush focus range, k is the number of minutes
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
			context.select('.brush').call(brush.move, [x2(new Date(start)), x2(new Date(end))]);
		};
	};

	this.resetBrush = function (svg_g, brushClassName, brush) {
		//reset the brush area
		if (svg_g === undefined || brush === undefined) return;
		svg_g.selectAll(brushClassName).call(brush.move, null);
	};

	this.resetBothBrushes = function (svg_g, brushClassNames, brush) {
		var resetBrush = this.resetBrush;
		brushClassNames.map(function (brushClassName) {
			resetBrush(svg_g, brushClassName, brush);
		});
	};

	// show and hide stuff
	this.showFocusAndTooltip = function (focus, tooltip, isTooltipOn, brushInNonEmptyRange) {
		focus.style('display', null);
		if (brushInNonEmptyRange) {
			// if (isTooltipOn) tooltip.style('display', null);
			this.toggleElementShowAndHide(isTooltipOn, tooltip);
		} else {
			//no need to show the circle or tooltip
			focus.selectAll('circle').style('display', 'none');
			tooltip.style('display', 'none');
		}
	};

	this.hideFocusAndTooltip = function (focus, tooltip) {
		focus.style('display', 'none');
		tooltip.style('display', 'none');
	};

	this.toggleWheel = function (wheelOn, zoom, chartRect, brushMainG) {
		if (wheelOn) {
			chartRect.call(zoom);
			brushMainG.call(zoom).on('mousedown.zoom', null);
		} else {
			chartRect.on('wheel.zoom', null);
			brushMainG.on('wheel.zoom', null);
		}
	};

	this.toggleElementShowAndHide = function (elementOn, elementName) {
		var display = elementOn? null: 'none';
		elementName.style('display', display);
	};

	this.updateColors = function (colorPalette, names, colors, graphClassNames, chartType) {
		var newColorZ = ChartToolService.setColorScheme(colorPalette);
		ChartToolService.bindDefaultColorsWithSources(newColorZ, names);
		for (var i = 0; i < names.length; i++) {
			var tempColor = colors[i] === null ? newColorZ(names[i]) : colors[i];
			var allElementsLinkedWithThisSeries = d3.selectAll('.' + graphClassNames[i]);
			allElementsLinkedWithThisSeries.filter('circle').style('fill', tempColor);
			switch (chartType) {
				case 'area':
					allElementsLinkedWithThisSeries.filter('path').style('fill', tempColor);
					d3.select('.' + graphClassNames[i] + '_brushArea').style('fill', tempColor);
					break;
				case 'scatter':
					allElementsLinkedWithThisSeries.filter('dot').style('fill', tempColor);
					d3.selectAll('.' + graphClassNames[i] + '_brushDot').style('fill', tempColor);
					break;
				// case "line":
				default:
					allElementsLinkedWithThisSeries.filter('path').style('stroke', tempColor);
					d3.select('.' + graphClassNames[i] + '_brushLine').style('stroke', tempColor);
			}
			d3.select('.' + graphClassNames[i] + '_legend').style('color', tempColor);
		}
	};

	// TODO: need to separate these 2 since it's a mix of modifying the view and data
	this.adjustSeriesAndTooltips = function (series, sources, x, tipItems, containerWidth, downSampleMethod) {
		var xDomain = x.domain();
		var newCurrSeries = JSON.parse(JSON.stringify(series));
		series.forEach(function (metric, index) {
			var source = sources[index];
			// metric with no data
			if (metric === null || metric.data === undefined || metric.data.length === 0){
				tipItems.selectAll('.' + source.graphClassName).style('display', 'none');
				return;
			}
			// metric out of x domain
			if (ChartToolService.isMetricNotInTheDomain(metric, xDomain)) {
				newCurrSeries[index].data = [];
				tipItems.selectAll('.'+ source.graphClassName).style('display', 'none');
				return;
			}
			tipItems.selectAll('.' + source.graphClassName).style('display', source.displaying? null: 'none');
			//if this metric time range is within the x domain
			var start = ChartToolService.bisectDate(metric.data, xDomain[0]);
			if (start > 0) start -= 1; //to avoid cut off issue on the edge
			var end = ChartToolService.bisectDate(metric.data, xDomain[1], start) + 1; //to avoid cut off issue on the edge
			newCurrSeries[index].data = metric.data.slice(start, end + 1);
		});
		newCurrSeries = ChartToolService.downSample(newCurrSeries, containerWidth, downSampleMethod);
		return newCurrSeries;
	};

	this.redrawAxis = function (xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG) {
		xAxisG.call(xAxis);  //redraw xAxis
		yAxisG.call(yAxis);  //redraw yAxis
		yAxisRG.call(yAxisR); //redraw yAxis right
	};

	this.redrawGrid = function (xGrid, xGridG, yGrid, yGridG) {
		xGridG.call(xGrid);
		yGridG.call(yGrid);
	};

	this.redrawGraphs = function (series, sources, chartType, graph, mainChart) {
		series.forEach(function (metric, index) {
			// metric with no defined data or hidden
			if (metric === null || metric.data === undefined || !sources[index].displaying) return;
			if (chartType === 'scatter') {
				mainChart.selectAll('circle.dot.' + metric.graphClassName)
					.attr('transform', function (d) {
						return 'translate(' + graph.x(d[0]) + ',' + graph.y(d[1]) + ')';
					})
					.style('display', function (d) {
						if (ChartToolService.isNotInTheDomain(d[0], graph.x.domain()) ||
							ChartToolService.isNotInTheDomain(d[1], graph.y.domain())) {
							return 'none';
						} else {
							return null;
						}
					});
			} else {
				mainChart.select('path.' + chartType + '.' + metric.graphClassName)
					.datum(metric.data)
					.attr('d', graph);
			}
		});
	};

	//rescale YAxis based on XAxis Domain
	this.reScaleYAxis = function (series, sources, x, y, yScalePlain, agYMin, agYMax) {
		if (!series) return;
		if (agYMin !== undefined && agYMax !== undefined) return; //hard coded ymin & ymax

		var xDomain = x.domain();
		var datapoints = [];

		series.forEach(function (metric, index) {
			// metric with no data or hidden
			if (metric === null || metric.data === undefined || metric.data.length === 0 || !sources[index].displaying) return;
			// metric out of x domain
			if (ChartToolService.isMetricNotInTheDomain(metric, xDomain)) return;

			var start = ChartToolService.bisectDate(metric.data, xDomain[0]);
			var end = ChartToolService.bisectDate(metric.data, xDomain[1], start);
			datapoints = datapoints.concat(metric.data.slice(start, end + 1));
		});
		var extent = d3.extent(datapoints, function (d) { return d[1]; });
		// TODO: still buggy with log scale when it try to calculate log(0)
		var yMin = UtilService.validNumberChecker(yScalePlain(extent[0]));
		var yMax = UtilService.validNumberChecker(yScalePlain(extent[1]));
		// make sure the domain has more than one value
		var buffer = (yMax - yMin) * bufferRatio;
		if (buffer === 0) buffer = ChartToolService.yAxisPadding;

		yMin = (agYMin === undefined) ? UtilService.validNumberChecker(yScalePlain.invert(yMin - buffer)): agYMin;
		yMax = (agYMax === undefined) ? UtilService.validNumberChecker(yScalePlain.invert(yMax + buffer)): agYMax;
		y.domain([yMin, yMax]);
	};

	this.resizeMainChartElements = function (sizeInfo, svg, svg_g, needToAdjustHeight) {
		if (needToAdjustHeight) {
			svg.attr('height', sizeInfo.height + sizeInfo.margin.top + sizeInfo.margin.bottom);
			svg_g.attr('height', sizeInfo.height);
		}
		svg.attr('width', sizeInfo.width + sizeInfo.margin.left + sizeInfo.margin.right);
		svg_g.attr('width', sizeInfo.width)
			.attr('transform', 'translate(' + sizeInfo.margin.left + ',' + sizeInfo.margin.top + ')');
	};

	this.resizeClip = function (sizeInfo, clip, needToAdjustHeight) {
		if (needToAdjustHeight) {
			clip.attr('height', sizeInfo.height);
		}
		clip.attr('width', sizeInfo.width);
	};

	this.resizeChartRect = function (sizeInfo, chartRect, needToAdjustHeight) {
		if (needToAdjustHeight) {
			chartRect.attr('height', sizeInfo.height);
		}
		chartRect.attr('width', sizeInfo.width);
	};

	this.resizeAxis = function (sizeInfo, xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, needToAdjustHeight, mainChart, xAxisConfig) {
		if (needToAdjustHeight) {
			xAxisG.attr('transform', 'translate(0,' + sizeInfo.height + ')');
		}
		yAxisRG.attr('transform', 'translate(' + sizeInfo.width + ')');
		this.redrawAxis(xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG);
		if (xAxisConfig!== undefined && xAxisConfig.title !== undefined) {
			mainChart.select('.xAxisLabel')
				.attr('transform', 'translate(' + (sizeInfo.width / 2) + ' ,' + (sizeInfo.height + sizeInfo.margin.top + xAxisLabelHeightFactor) + ')');
		}
	};

	this.resizeGrid = function (sizeInfo, xGrid, xGridG, yGrid, yGridG, needToAdjustHeight) {
		if (needToAdjustHeight) {
			xGrid.tickSizeInner(-sizeInfo.height);
			xGridG.attr('transform', 'translate(0,' + sizeInfo.height + ')');
		}
		yGrid.tickSizeInner(-sizeInfo.width);
		this.redrawGrid(xGrid, xGridG, yGrid, yGridG);
	};

	this.resizeBrush = function (sizeInfo, brush, brushG, context, x2, xAxis2, xAxisG2, y2, needToAdjustHeight) {
		if (needToAdjustHeight) {
			context.attr('transform', 'translate(0,' + sizeInfo.margin2.top + ')');
			xAxisG2.attr('transform', 'translate(0,' + sizeInfo.height2 + ')');
			y2.range([sizeInfo.height2, 0]);
		}
		x2.range([0, sizeInfo.width]);
		brush.extent([
			[0, 0],
			[sizeInfo.width, sizeInfo.height2]
		]);
		brushG.call(brush);
		xAxisG2.call(xAxis2);
	};

	this.resizeMainBrush = function (sizeInfo, brushMain, brushMainG) {
		brushMain.extent([
			[0, 0],
			[sizeInfo.width, sizeInfo.height]
		]);
		brushMainG.call(brushMain);
	};

	this.resizeZoom = function (sizeInfo, zoom) {
		zoom.translateExtent([
			[0, 0],
			[sizeInfo.width, sizeInfo.height]
		]).extent([
			[0, 0],
			[sizeInfo.width, sizeInfo.height]
		]);
	};

	this.resizeLineGraphs = function (svg_g, line) {
		svg_g.selectAll('.line').attr('d', line);
	};

	this.resizeAreaGraphs = function (svg_g, area) {
		svg_g.selectAll('.area').attr('d', area);
	};

	this.resizeBrushLineGraphs = function (svg_g, line2) {
		svg_g.selectAll('.brushLine').attr('d', line2);
	};

	this.resizeBrushAreaGraphs = function (svg_g, area2) {
		svg_g.selectAll('.brushArea').attr('d', area2);
	};

	this.resizeGraphs = function (svg_g, graph, chartType) {
		if (chartType === 'scatter') {
			svg_g.selectAll('.dot')
				.attr('transform', function (d) {
					return 'translate(' + graph.x(d[0]) + ',' + graph.y(d[1]) + ')';
				})
				.style('display', function (d) {
					if (ChartToolService.isNotInTheDomain(d[0], graph.x.domain()) ||
						ChartToolService.isNotInTheDomain(d[1], graph.y.domain())) {
						return 'none';
					} else {
						return null;
					}
				});
		} else {
			svg_g.selectAll('.' + chartType).attr('d', graph);
		}
	};

	this.resizeBrushGraphs = function (svg_g, graph2, chartType) {
		if (chartType === 'scatter') {
			svg_g.selectAll('.brushDot')
				.attr('transform', function (d) {
					return 'translate(' + graph2.x(d[0]) + ',' + graph2.y(d[1]) + ')';
				})
				.style('display', function (d) {
					if (ChartToolService.isNotInTheDomain(d[0], graph2.x.domain()) ||
						ChartToolService.isNotInTheDomain(d[1], graph2.y.domain())) {
						return 'none';
					} else {
						return null;
					}
				});
		} else {
			svg_g.selectAll('.brush' + UtilService.capitalizeString(chartType)).attr('d', graph2);
		}
	};
}]);
