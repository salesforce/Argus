/**
 * Created by liuxizi.xu on 3/28/17.
 */

'use strict';
/* global angular:false, d3:false, $:false. document:false */

angular.module('argus.services.charts.elements', [])
.service('ChartElementService', ['ChartToolService', 'UtilService', function(ChartToolService, UtilService) {
	var nGridXSmall = 5;
	var nGridYSmall = 3;
	var nGridX = 7;
	var xAxisLabelHeightFactor = 15;
	var tipOffset = 8;
	var tipPadding = 3;
	var doublePadding = 2 * tipPadding;
	var circleRadius = 4.5;
	var circleLen = circleRadius * 2;
	var itemsPerCol = 12; // for tooltip
	var crossLineTipWidth = 35;
	var crossLineTipHeight = 15;
	var crossLineTipPadding = 3;
	var annotationLabelFontSize = 14;
	var extraYAxisPadding = ChartToolService.extraYAxisPadding;
	this.customizedChartType = ['scatter', 'bar', 'stackbar'];


	var setGraphColorStyle = function (graph, color, chartType, opacity) {
		graph.style('stroke', color);
		if (chartType === 'stackarea' || chartType === 'area') {
			graph.style('fill', color).style('opacity', opacity);
		}
	};

	var calculateSnappingRange = function (rangeArray) {
		return (rangeArray[1] - rangeArray[0]) * 0.1;
	};

	var flipAnElementHorizontally = function (elements, width, totalWidth, marginRight, startingX, extraPadding) {
		var transformAttr = null;
		if (startingX + width > totalWidth + marginRight) {
			if(startingX - width > 0){
				transformAttr = 'translate(-' + (width + 2 * extraPadding) + ')';
			} else {
				transformAttr = 'translate(-' + startingX + ')';
			}
		}
		elements.map(function(element) {
			element.attr('transform', transformAttr);
		});
	};

	// pre populate the elements
	this.createAxisElements = function (x, y, isSmallChart, yAxisConfig) {
		var xAxis, yAxis, yAxisR,
			currentnGridX, currentnGridY;

		if (isSmallChart) {
			currentnGridX = nGridXSmall;
			currentnGridY = nGridYSmall;
		} else {
			currentnGridX = nGridX;
			currentnGridY = yAxisConfig.numTicksYaxis;
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

	this.createExtraYAxisRElements = function (y, yAxisConfig) {
		return d3.axisRight()
			.scale(y)
			.ticks(yAxisConfig.numTicksYaxis)
			.tickFormat(d3.format(yAxisConfig.formatYaxis));
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

	this.createBrushElements = function (timeInfo, isGMT, sizeInfo, isSmallChart, chartType, brushFunction, yScaleType, yScaleConfigValue) {
		// axis and ticks
		var currentnGridX = isSmallChart? nGridXSmall: nGridX;

		var brushSizeInfo = {
			width: sizeInfo.width,
			height: sizeInfo.height2
		};
		var xy = ChartToolService.getXandY(timeInfo, isGMT, brushSizeInfo, yScaleType, yScaleConfigValue);
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

	this.createMainBrush = function (sizeInfo, brushFunction) {
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

	this.createStackArea = function (x, y) {
		return d3.area()
			.x(function (d) {
				return UtilService.validNumberChecker(x(d.data.timestamp));
			})
			.y0(function (d) {
				return UtilService.validNumberChecker(y(d[0]));
			})
			.y1(function (d) {
				return UtilService.validNumberChecker(y(d[1]));
			});
	};

	/**
		the following chart types are not pre defined in d3.
		No elements is created, but x and y are passed down
	*/
	this.createScatter = function (x, y) {
		return {x: x, y: y};
	};

	this.createBar = function (x, y) {
		return {
			x: x,
			y: y.rangeRound(y.range()),
			x0: d3.scaleBand().range(x.range()).paddingInner(0.05),
			x1: d3.scaleBand().padding(0.01)
		};
	};

	this.createStackbar = function (x, y) {
		return {
			x: x,
			y: y.rangeRound(y.range()),
			x0: d3.scaleBand().range(x.range()).paddingInner(0.05)
		};
	};

	this.createHeatmap = function(x, y){
		return {
			x: x,
			y: y,
			z: d3.scaleLinear().range(["white", "darkblue"]) //TODO make the color a parameter
		};
	};

	this.createGraph = function (x, y, chartType) {
		var graphElement;
		switch (chartType) {
			case 'scatter':
				graphElement = this.createScatter(x, y);
				break;
			case 'bar':
				graphElement = this.createBar(x, y);
				break;
			case 'stackbar':
				graphElement = this.createStackbar(x, y);
				break;
			case 'area':
				graphElement = this.createArea(x, y);
				break;
			case 'stackarea':
				graphElement = this.createStackArea(x, y);
				break;
			// case 'line':

			case 'heatmap':
				graphElement = this.createHeatmap(x, y);
				break;
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
			.attr('width', sizeInfo.widthFull + sizeInfo.margin.left + sizeInfo.margin.right)
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
	this.appendAxisElements = function (sizeInfo, chart, chartOptionXAxis, chartOptionYAxis) {
		var xAxisG = chart.append('g')
			.attr('class', 'x axis')
			.attr('transform', 'translate(0,' + sizeInfo.height + ')');
		var yAxisG = chart.append('g')
			.attr('class', 'y axis');
		var yAxisRG = chart.append('g')
			.attr('class', 'y axis')
			.attr('transform', 'translate(' + sizeInfo.width + ')');
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

	this.appendExtraYAxisElement = function (widthOffSet, chart, yAxisR) {
		var yAxisRG = chart.append('g')
			.attr('class', 'y axis extra')
			.attr('transform', 'translate(' + widthOffSet + ')')
			.call(yAxisR);
		return yAxisRG;
		//not adding the axis label now, not enough space for that
	};

	this.appendGridElements = function (sizeInfo, chart) {
		var xGridG = chart.append('g')
			.attr('class', 'x grid')
			.attr('transform', 'translate(0,' + sizeInfo.height + ')');
		var yGridG = chart.append('g')
			.attr('class', 'y grid');
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

	this.appendFlagsElements = function (svg_g) {
		var flagsG = svg_g.append('g').attr('class', 'flags');
		return flagsG;
	};

	this.appendBrushWithXAxisElements = function (sizeInfo, svg_g) {
		var context = svg_g.append('g')
			.attr('class', 'context')
			.attr('transform', 'translate(0,' + sizeInfo.margin2.top + ')');
		var xAxisG2 = context.append('g')
			.attr('class', 'xBrush axis')
			.attr('transform', 'translate(0,' + sizeInfo.height2 + ')');
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
			.attr('class', 'crossLine')
			.attr('y1', 0);
		crossLine.append('line')
			.attr('name', 'crossLineY')
			.attr('class', 'crossLine crossLineY')
			.attr('x1', 0);
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
			.attr('class', 'crossLineTip')
			.attr('y', 0)
			.attr('dy', crossLineTipHeight);
		crossLine.append('text')
			.attr('name', 'crossLineTipY')
			.attr('class', 'crossLineTip crossLineY')
			.attr('x', 0)
			.attr('dx', -crossLineTipWidth);

		return crossLine;
	};

	this.appendMouseOverHighlightBar = function (chart, height, width) {
		var mouseOverHighlightBar = chart.append('g')
			.attr('class', 'mouseOverHighlight')
			.style('display', 'none');
		mouseOverHighlightBar.append('rect')
			.attr('class', 'highlightBar')
			.attr('height', height)
			.attr('width', width);
		mouseOverHighlightBar.append('rect')
			.attr('name', 'crossLineTipRectX')
			.attr('class', 'crossLineTipRect');
		mouseOverHighlightBar.append('text')
			.attr('name', 'crossLineTipX')
			.attr('class', 'crossLineTip')
			.attr('y', 0)
			.attr('dy', crossLineTipHeight);

		return mouseOverHighlightBar;
	};

	this.appendMouseOverTile = function (chart, height, width) {
		var mouseOverTile = chart.append('g')
			.attr('class', 'mouseOverTile')
			.style('display', 'none');
		mouseOverTile.append('rect')
			.attr('class', 'highlightTile')
			.attr('height', height)
			.attr('width', width);
		mouseOverTile.append('rect')
			.attr('name', 'crossLineTipRectX')
			.attr('class', 'crossLineTipRect');
		mouseOverTile.append('text')
			.attr('name', 'crossLineTipX')
			.attr('class', 'crossLineTip')
			.attr('y', 0)
			.attr('dy', crossLineTipHeight);

		return mouseOverTile;
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

	// add new element for each source
	this.renderLineGraph = function (chart, color, metric, line, chartId) {
		chart.append('path')
			.attr('class', 'line ' + metric.graphClassName)
			.style('stroke', color)
			// please keep this line as it is
			.style('clip-path', 'url(\'#clip_' + chartId + '\')')
			.datum(metric.data)
			.attr('d', line);
	};

	this.renderAreaGraph = function (chart, color, metric, area, chartId) {
		chart.append('path')
			.attr('class', 'area ' + metric.graphClassName)
			.style('fill', color)
			.style('clip-path', 'url(\'#clip_' + chartId + '\')')
			.datum(metric.data)
			.attr('d', area);
	};

	this.renderStackareaGraph = function (chart, color, metric, stackarea, chartId) {
		chart.append('path')
			.attr('class', 'stackarea ' + metric.graphClassName)
			.style('fill', color)
			.style('clip-path', 'url(\'#clip_' + chartId + '\')')
			.datum(metric.data)
			.attr('d', stackarea);
	};

	this.renderScatterGraph = function (chart, color, metric, graph, chartId) {
		chart.selectAll('.dots')
			.data(metric.data)
			.enter().append('circle')
			.attr('cx', function (d) { return UtilService.validNumberChecker(graph.x(d[0])); })
			.attr('cy', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
			.attr('class', 'dot ' + metric.graphClassName + ' extraYAxis_' + (metric.extraYAxis || ''))
			.style('fill', color)
			.style('clip-path', 'url(\'#clip_' + chartId + '\')')
			.attr('r', circleRadius * 0.7);
	};

	this.renderBarGraph = function (chart, color, metric, graph, chartId) {
		var tempHeight = graph.y.range()[0];
		chart.selectAll('.bars')
			.data(metric.data)
			.enter().append('rect')
			.attr('x', function (d) { return UtilService.validNumberChecker(graph.x0(d[0])); })
			.attr('y', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
			.attr('width', graph.x1.bandwidth())
			.attr('height', function(d) { return UtilService.validNumberChecker(tempHeight - graph.y(d[1])); })
			.attr('transform', function() { return 'translate(' + graph.x1(metric.graphClassName) + ',0)'; })
			.attr('class', 'bar ' + metric.graphClassName + ' extraYAxis_' + (metric.extraYAxis || ''))
			.style('fill', color)
			.style('clip-path', 'url(\'#clip_' + chartId + '\')');
	};

	this.renderStackbarGraph = function (chart, color, metric, graph, chartId) {
		chart.selectAll('.stackbars')
			.data(metric.data)
			.enter().append('rect')
			.attr('x', function (d) { return UtilService.validNumberChecker(graph.x0(d.data.timestamp)); })
			.attr('y', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
			.attr('width', graph.x0.bandwidth())
			.attr('height', function (d) { return UtilService.validNumberChecker(graph.y(d[0]) - graph.y(d[1])); })
			.attr('class', 'stackbar ' + metric.graphClassName + ' extraYAxis_' + (metric.extraYAxis || ''))
			.style('fill', color)
			.style('clip-path', 'url(\'#clip_' + chartId + '\')');
	};

	this.renderGraph = function (chart, color, metric, graph, chartId, chartType, opacity) {
		switch (chartType) {
			case 'scatter':
				this.renderScatterGraph(chart, color, metric, graph, chartId);
				break;
			case 'bar':
				this.renderBarGraph(chart, color, metric, graph, chartId);
				break;
			case 'stackbar':
				this.renderStackbarGraph(chart, color, metric, graph, chartId);
				break;
			default:
				var newGraph = chart.append('path')
					.attr('class', chartType + ' ' + metric.graphClassName + ' extraYAxis_' + (metric.extraYAxis || ''))
					.style('clip-path', 'url(\'#clip_' + chartId + '\')')
					.datum(metric.data)
					.attr('d', graph);
				setGraphColorStyle(newGraph, color, chartType, opacity);
		}
	};

	this.appendTileArea = function (chart){
		return chart.append('g').attr('class', 'tileArea');
	};


	this.renderHeatmap = function (tileArea, heatmapData, graph, bucket, chartId) {
			tileArea
			.selectAll('.heatmapTile')
			.data(heatmapData)
			.enter().append('rect')
			.attr('class', 'heatmapTile')
			.attr('x', function(d){ return graph.x(d.timestamp);})
			.attr('y', function(d){ return graph.y(d.bucket + bucket.yStep);})
			.attr('width', graph.x(bucket.xStep) - graph.x(0))
			.attr('height', graph.y(0) - graph.y(bucket.yStep))
			.attr('fill', function(d) {return graph.z(d.frequency);})
			.style('clip-path', 'url(\'#clip_' + chartId + '\')');
	};

	this.removeAllTiles = function (tileArea){
		tileArea.selectAll('.heatmapTile').remove();
	};

	this.resizeHeatmap = function (chart, heatmapData, graph, bucket, chartId) {
		chart.selectAll('.heatmapTile')
			.data(heatmapData)
			.attr('x', function(d){ return graph.x(d.timestamp);})
			.attr('y', function(d){ return graph.y(d.bucket + bucket.yStep);})
			.attr('width', graph.x(bucket.xStep) - graph.x(0))
			.attr('height', graph.y(0) - graph.y(bucket.yStep))
			.attr('fill', function(d) {return graph.z(d.frequency);})
			.style('clip-path', 'url(\'#clip_' + chartId + '\')');
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

	this.renderBrushScatterGraph = function (context, color, metric, graph2) {
		context.selectAll('.dots')
			.data(metric.data)
			.enter().append('circle')
			.attr('cx', function (d) { return UtilService.validNumberChecker(graph2.x(d[0])); })
			.attr('cy', function (d) { return UtilService.validNumberChecker(graph2.y(d[1])); })
			.attr('class', 'brushDot ' + metric.graphClassName + '_brushDot' +' extraYAxis_' + (metric.extraYAxis || ''))
			.style('fill', color)
			.attr('r', 1.5);
	};

	this.renderBrushBarGraph = function (context, color, metric, graph2) {
		var tempHeight = graph2.y.range()[0];
		context.selectAll('.bars')
			.data(metric.data)
			.enter().append('rect')
			.attr('x', function (d) { return UtilService.validNumberChecker(graph2.x0(d[0])); })
			.attr('y', function (d) { return UtilService.validNumberChecker(graph2.y(d[1])); })
			.attr('width', graph2.x1.bandwidth())
			.attr('height', function(d) { return UtilService.validNumberChecker(tempHeight - graph2.y(d[1])); })
			.attr('transform', function() { return 'translate(' + graph2.x1(metric.graphClassName) + ',0)'; })
			.attr('class', 'brushBar ' + metric.graphClassName + '_brushBar' +' extraYAxis_' + (metric.extraYAxis || ''))
			.style('fill', color);
	};

	this.renderBrushStackbarGraph = function (context, color, metric, graph2) {
		context.selectAll('.stackbars')
			.data(metric.data)
			.enter().append('rect')
			.attr('x', function (d) { return UtilService.validNumberChecker(graph2.x0(d.data.timestamp)); })
			.attr('y', function (d) { return UtilService.validNumberChecker(graph2.y(d[1])); })
			.attr('width', graph2.x0.bandwidth())
			.attr('height', function (d) { return UtilService.validNumberChecker(graph2.y(d[0]) - graph2.y(d[1])); })
			.attr('class', 'brushStackbar ' + metric.graphClassName + '_brushStackbar' + ' extraYAxis_' + (metric.extraYAxis || ''))
			.style('fill', color);
	};

	this.renderBrushGraph = function (context, color, metric, graph2, chartType, opacity) {
		var cappedChartTypeStr = UtilService.capitalizeString(chartType);
		switch (chartType) {
			case 'scatter':
				this.renderBrushScatterGraph(context, color, metric, graph2);
				break;
			case 'bar':
				this.renderBrushBarGraph(context, color, metric, graph2);
				break;
			case 'stackbar':
				this.renderBrushStackbarGraph(context, color, metric, graph2);
				break;
			default:
				var newGraph = context.append('path')
					.attr('class', 'brush' + cappedChartTypeStr + ' ' + metric.graphClassName + '_brush' + cappedChartTypeStr + ' extraYAxis_' + (metric.extraYAxis || ''))
					.datum(metric.data)
					.attr('d', graph2);
				setGraphColorStyle(newGraph, color, chartType, opacity);
		}
	};

	this.renderFocusCircle = function (focus, color, className, extraYAxis) {
		focus.append('circle')
			.attr('r', circleRadius * 1.25)
			.style('fill', color)
			.attr('class', className + ' extraYAxis_' + extraYAxis);
	};

	this.renderTooltip = function (tipItems, color, className) {
		tipItems.append('circle')
			.attr('r', circleRadius)
			.style('fill', color)
			.attr('class', className);
		tipItems.append('text')
			.attr('class', className);
	};

	this.renderToolTipForHeatmap = function (tipItems, className){
		tipItems.append('text')
			.attr('class', className);
	};

	this.renderAnnotationsLabels = function (flags, color, className, dataPoint) {
		var flagG = flags.append('g')
			.attr('class', 'flagItem ' + className)
			.attr('id', className + dataPoint.flagID)
			.style('stroke', color)
			.attr('clicked', 'No');

		// add the pin on the graph
		flagG.append('line')
			.attr('y2', 35)
			.attr('class', 'pin');
		flagG.append('circle')
			.attr('r', 8)
			.attr('class', 'pin');
		flagG.append('text')
			.attr('dy', 4)
			.text(dataPoint.title)
			.attr('class', 'pin');

		// add label to the pin
		var dateObj = new Date(dataPoint.x);
		var label = flagG.append('g').attr('class', 'flagLabel').style('display', 'none');
		var labelContainer = label.append('rect')
					.attr('x', -tipPadding)
					.attr('y', -annotationLabelFontSize - tipPadding)
					.attr('rx', tipPadding)
					.attr('ry', tipPadding);
		var labelContent = label.append('g');
		// add time info
		var offset = 6 + annotationLabelFontSize,
			totalOffset = offset;
		labelContent.append('text')
			.append('tspan')
			.style('font-weight', 600)
			.text(dateObj.toUTCString());
		labelContent.append('text')
			.attr('dy', totalOffset)
			.text('In current timezone: ' + dateObj.toLocaleString());
		for (var key in dataPoint.fields) {
			if (dataPoint.fields.hasOwnProperty(key)) {
				totalOffset += offset;
				labelContent.append('text')
					.text(key + ': ' + dataPoint.fields[key])
					.attr('dy', totalOffset);
			}
		}
		// add the info box while hovering over
		flagG.selectAll('.pin').on('click', function () {
				// click to make the label tip stay while hovering over and enlarge the annotation's circle
				if (flagG.attr('clicked') !== 'Yes') {
					flagG.attr('clicked', 'Yes');
					flagG.select('circle').attr('r', 16);
					label.style('display', null);
				} else {
					flagG.attr('clicked', 'No');
					flagG.select('circle').attr('r', 8);
					label.style('display', 'none');
				}
			})
			.on('mouseover', function () {
				label.style('display', null);
			})
			.on('mouseout', function () {
				if (flagG.attr('clicked') !== 'Yes') {
					label.style('display', 'none');
				}
			});
	};

	this.bringMouseOverLabelToFront = function (flags, chartId) {
		// https://github.com/wbkd/d3-extended
		// bring SVG to the front and back
		d3.selection.prototype.moveToFront = function() {
			return this.each(function(){
				this.parentNode.appendChild(this);
			});
		};
		d3.selection.prototype.moveToBack = function() {
			return this.each(function() {
				var firstChild = this.parentNode.firstChild;
				if (firstChild) {
					this.parentNode.insertBefore(this, firstChild);
				}
			});
		};
		var containerDim = document.getElementById(chartId).getBoundingClientRect();
		flags.selectAll('.flagItem')
			.on('mouseover', function () {
				// shift annotation label if its cut off
				var flag = d3.select(this);
				var label = flag.select('.flagLabel');
				var currentTransformAttr = label.attr('transform');
				var labelDim = this.lastElementChild.getBoundingClientRect();
				if (currentTransformAttr) {
					// update transformation if label is cut off
					var splitedTransformAttr = currentTransformAttr.split(',');
					if (labelDim.left < containerDim.left) {
						label.attr('transform', 'translate(' +
												(Number(splitedTransformAttr[0].substring(10)) + (containerDim.left - labelDim.left + tipOffset)) +
												',' + splitedTransformAttr[1]);
					} else if (labelDim.right > containerDim.right) {
						label.attr('transform', 'translate(' +
												(Number(splitedTransformAttr[0].substring(10)) - (labelDim.right - containerDim.right + tipOffset)) +
												',' + splitedTransformAttr[1]);
					}
				} else {
					// update background rect's size and move label to the top of the flags
					// this should be only called the first time mouseover happens to a flag
					label.select('rect')
						.attr('height', labelDim.height + doublePadding)
						.attr('width', labelDim.width + doublePadding);
					label.attr('transform', 'translate(-' + (labelDim.width/2 + tipPadding) + ', -' + (labelDim.height + doublePadding) + ')');
				}
				label.select('rect').style('stroke-width', 3);
				flag.moveToFront();
			})
			.on('mouseout', function () {
				d3.select(this).select('rect').style('stroke-width', 2);
			})
			.on('click', function () {
				d3.select(this).moveToBack();
			});
	};

	// add overlay stuff (do this last during the rendering process since these will be on top)
	this.appendChartRect = function (sizeInfo, chart, mouseOverFunction, mouseOutFunction, mouseMoveFunction, zoom) {
		var chartRect = chart.append('rect')
			.attr('class', 'chartOverlay')
			.attr('width', sizeInfo.width)
			.attr('height', sizeInfo.height)
			.on('mouseover', mouseOverFunction)
			.on('mouseout', mouseOutFunction)
			.on('mousemove', mouseMoveFunction);
		if(zoom){
			chartRect.call(zoom);
		}
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

	this.updateFocusCirclesAndObtainDataPoints = function (focus, tipItems, series, sources, x, y_, extraY_, mousePositionData, timestampSelector, dateBisector, isDataStacked) {
		var snapPoint, datapoints = [];
		var minDistanceVertical = Number.MAX_VALUE;
		var minDistanceHorizontal = Number.MAX_VALUE;
		var snappingRange = calculateSnappingRange(x.range());
		var xDomain = x.domain();

		series.forEach(function (metric) {
			var circle = focus.select('.' + metric.graphClassName);
			var y = metric.extraYAxis ? extraY_[metric.extraYAxis] : y_;

			var displayingInLegend = ChartToolService.findMatchingMetricInSources(metric, sources).displaying;
			if (metric.data.length === 0 || !displayingInLegend) {
				// if the metric has no data or is toggled to hide
				circle.style('display', 'none');
				tipItems.selectAll('.' + metric.graphClassName).style('display', 'none');
			} else {
				var data = metric.data;
				var i = dateBisector(metric.data, mousePositionData.mouseX, 1);
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
				} else {
					// if both data points lives in the domain, choose the closer one to the mouse position
					d = mousePositionData.mouseX - (timestampSelector(d0)) > (timestampSelector(d1)) - mousePositionData.mouseX ? d1 : d0;
				}
				var currentDatapoint = isDataStacked ? [d.data.timestamp, d.data[metric.name]] : d;

				// set a snapping limit for graph
				var notInSnappingRange = Math.abs(mousePositionData.positionX - x(currentDatapoint[0])) > snappingRange;

				if (ChartToolService.isNotInTheDomain(currentDatapoint[0], xDomain) ||
					ChartToolService.isNotInTheDomain(d[1], y.domain()) ||
					notInSnappingRange) {
					//outside domain
					circle.style('display', 'none');
					// displayProperty = 'none';
					tipItems.selectAll('.' + metric.graphClassName).style('display', 'none');
				} else {
					// update circle's position on each graph
					var newX = UtilService.validNumberChecker(x(currentDatapoint[0]));
					var newY = UtilService.validNumberChecker(y(d[1]));
					circle.attr('dataX', currentDatapoint[0]).attr('dataY', d[1]) //store the data
						.attr('transform', 'translate(' + newX + ',' + newY + ')')
						.style('display', null);
					tipItems.selectAll('.' + metric.graphClassName).style('display', null);
					datapoints.push({
						data: currentDatapoint,
						graphClassName: metric.graphClassName,
						name: metric.name
					});
					// calculate snapping point
					var distanceHorizontal = Math.abs(UtilService.validNumberChecker(mousePositionData.positionX - newX));
					var distanceVertical = Math.abs(UtilService.validNumberChecker(mousePositionData.positionY - newY));

					if (distanceHorizontal < minDistanceHorizontal) {
						snapPoint = {
							mouseX : new Date(currentDatapoint[0]),
							mouseY : d[1],
							positionX : newX,
							positionY : newY
						};
						minDistanceHorizontal = distanceHorizontal;
						minDistanceVertical = distanceVertical;

					} else if (distanceHorizontal === minDistanceHorizontal && distanceVertical < minDistanceVertical) {
						snapPoint = {
							mouseX : new Date(currentDatapoint[0]),
							mouseY : d[1],
							positionX : newX,
							positionY : newY
						};
						minDistanceVertical = distanceVertical;
					}
				}
			}
		});
		return	{
			dataPoints: datapoints,
			snapPoint: snapPoint
		};
	};

	this.updateHighlightRangeAndObtainDataPoints = function (graph, mouseOverHighlightBar, tipItems, series, sources, extraY, mousePositionData, timestampSelector, dateBisector, dateFormatter, isDataStacked, distanceToRight) {
		var datapoints = [];
		var bandOffset = graph.x0.bandwidth() + graph.x0.paddingInner()/2;
		var displayHighlightBar = false;

		var xDiscreteDomain = graph.x0.domain();
		var matchingTimestamp, i = d3.bisectLeft(xDiscreteDomain, mousePositionData.mouseX.getTime());
		if (!xDiscreteDomain[i - 1]) {
			// i === 0
			matchingTimestamp = xDiscreteDomain[i];
		} else if (!xDiscreteDomain[i]) {
			// i === xDiscreteDomain.length
			matchingTimestamp = xDiscreteDomain[i - 1];
		} else {
			matchingTimestamp = mousePositionData.positionX > graph.x0(xDiscreteDomain[i - 1]) + bandOffset ? xDiscreteDomain[i] : xDiscreteDomain[i - 1];
		}
		series.forEach(function (metric) {
			var y = metric.extraYAxis ? extraY[metric.extraYAxis] : graph.y;
			var displayingInLegend = ChartToolService.findMatchingMetricInSources(metric, sources).displaying;
			if (metric.data.length === 0 || !displayingInLegend) {
				// if the metric has no data or is toggled to hide
				tipItems.selectAll('.' + metric.graphClassName).style('display', 'none');
			} else {
				var d = metric.data.find(function (d0) {
					return timestampSelector(d0) === matchingTimestamp;
				});
				if (d === undefined) {
					tipItems.selectAll('.' + metric.graphClassName).style('display', 'none');
				} else {
					var currentDatapoint = isDataStacked ? [d.data.timestamp, d.data[metric.name]] : d;
					if (ChartToolService.isNotInTheDomain(currentDatapoint[0], xDiscreteDomain) ||
						ChartToolService.isNotInTheDomain(currentDatapoint[1], y.domain())) {
						tipItems.selectAll('.' + metric.graphClassName).style('display', 'none');
					} else {
						tipItems.selectAll('.' + metric.graphClassName).style('display', null);
						displayHighlightBar = true;
						datapoints.push({
							data: currentDatapoint,
							graphClassName: metric.graphClassName,
							name: metric.name
						});
					}
				}
			}
		});

		if (displayHighlightBar) {
			var dateText = mouseOverHighlightBar.select('.crossLineTip');
			var boxXRect = mouseOverHighlightBar.select('.crossLineTipRect');
			var startingPosition = graph.x0(matchingTimestamp);
			var date = dateFormatter(matchingTimestamp);

			mouseOverHighlightBar.select('.highlightBar')
				.attr('x', startingPosition)
				.attr('dataX', matchingTimestamp)
				.style('display', null);
			dateText.attr('x', startingPosition).text(date);

			var boxX = dateText.node().getBBox();
			boxXRect.attr('x', boxX.x - crossLineTipPadding)
				.attr('y', boxX.y - crossLineTipPadding)
				.attr('width', boxX.width + 2 * crossLineTipPadding)
				.attr('height', boxX.height + 2 * crossLineTipPadding);
			flipAnElementHorizontally([dateText, boxXRect], boxX.width, distanceToRight, 0, boxX.x, crossLineTipPadding);
		}

		return datapoints;
	};

	this.justUpdateDateText = function (graph, mouseOverTile, dateFormatter, timestamp){

		var dateText = mouseOverTile.select('.crossLineTip').attr('display', null);
		var boxXRect = mouseOverTile.select('.crossLineTipRect').attr('display', null);
		var startingPosition = graph.x(timestamp);
		var date = dateFormatter(timestamp);

		dateText.attr('x', startingPosition).text(date);

		var boxX = dateText.node().getBBox();
		boxXRect.attr('x', boxX.x - crossLineTipPadding)
			.attr('y', boxX.y - crossLineTipPadding)
			.attr('width', boxX.width + 2 * crossLineTipPadding)
			.attr('height', boxX.height + 2 * crossLineTipPadding);

	};

	this.updateHighlightTile = function (graph, sizeInfo, bucketInfo, tileDataAndIndex, mouseOverTile, dateFormatter, distanceToRight){
		var timestamp = tileDataAndIndex.data.timestamp;


		var width = graph.x(bucketInfo.xStep) - graph.x(0);
		var height =  graph.y(0) - graph.y(bucketInfo.yStep);
		var xPos = tileDataAndIndex.xIndex * width;
		var yPos = sizeInfo.height - (tileDataAndIndex.yIndex + 1) * height;

		if(tileDataAndIndex.xIndex * width + width > sizeInfo.width) width = sizeInfo.width - tileDataAndIndex.xIndex * width;

		mouseOverTile.select('.highlightTile')
			.attr('x', xPos)
			.attr('y', yPos)
			.attr('width', width)
			.attr('height', height)
			.attr('display', null);


		var dateText = mouseOverTile.select('.crossLineTip');
		var boxXRect = mouseOverTile.select('.crossLineTipRect');
		var startingPosition = graph.x(timestamp);
		var date = dateFormatter(timestamp);

		dateText.attr('x', startingPosition).text(date);

		var boxX = dateText.node().getBBox();
		boxXRect.attr('x', boxX.x - crossLineTipPadding)
			.attr('y', boxX.y - crossLineTipPadding)
			.attr('width', boxX.width + 2 * crossLineTipPadding)
			.attr('height', boxX.height + 2 * crossLineTipPadding);

		flipAnElementHorizontally([dateText, boxXRect], boxX.width, distanceToRight, 0, boxX.x, crossLineTipPadding);
	};

	this.updateTooltipItemsContent = function (sizeInfo, menuOption, tipItems, tipBox, datapoints, mousePositionData) {
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
			var dataFormat = menuOption.tooltipConfig.rawTooltip ? ChartToolService.rawDataFormat : menuOption.tooltipConfig.customTooltipFormat;
			var name = UtilService.trimMetricName(datapoints[i].name, menuOption);
			var tempData = datapoints[i].data[1];
			textLine.text(name + ' -- ' + d3.format(dataFormat)(tempData));
			// update XOffset if existing offset is smaller than textLine
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
			tipBox.attr('width', tipBounds.width + 2 * doublePadding);
			tipBox.attr('height', tipBounds.height + doublePadding);
		}
		// move tooltip to the left if there is not enough space to display it on the right
		flipAnElementHorizontally([tipItems, tipBox], Number(tipBox.attr('width')), sizeInfo.width, sizeInfo.margin.right, mousePositionData.positionX, tipOffset);
	};


	this.updateTooltipItemsContentForHeatmap = function(sizeInfo, menuOption, tipItems, tipBox, aggregateInfo, names, graphClassNamesMap,  mousePositionData) {
		var XOffset = 0;
		var YOffset = 0;
		var newXOffset = 0;
		var OffsetMultiplier = -1;
		// update tipItems (circle, source name, and data)
		tipItems.select('text.aggregateInfo')
			.attr('dy', 20 + mousePositionData.positionY)
			.attr('dx', mousePositionData.positionX + tipOffset + tipPadding + 2 + XOffset)
			.text(aggregateInfo)
			.attr('font-weight', 'bold')
			.attr('xml:space', 'preserve');

		for (var i = 0; i <names.length; i++) {
			// create a new col after every itemsPerCol
			if (i % itemsPerCol === 0) {
				OffsetMultiplier++;
				YOffset = OffsetMultiplier * itemsPerCol;
				XOffset += newXOffset;
				newXOffset = 0;
			}
			var textLine = tipItems.select('text.' + graphClassNamesMap[names[i]])
				.attr('dy', 20 * (2 + i - YOffset) + mousePositionData.positionY)
				.attr('dx', mousePositionData.positionX + tipOffset + tipPadding + 2 + XOffset)
				.attr('display', null);

			var name = UtilService.trimMetricName(names[i], menuOption);
			textLine.text(name);
			// update XOffset if existing offset is smaller than textLine
			var tempXOffset = textLine.node().getBBox().width + circleLen + tipOffset;
			if (tempXOffset > newXOffset) {
				newXOffset = tempXOffset;
			}
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
		// move tooltip to the left if there is not enough space to display it on the right
		flipAnElementHorizontally([tipItems, tipBox], Number(tipBox.attr('width')), sizeInfo.width, sizeInfo.margin.right, mousePositionData.positionX, tipOffset);
	};

	this.unshowAllHeatmapTooltipText = function(tipItems, graphClassnames){
		graphClassnames.forEach(function(name){
			tipItems.select('text.'+name)
				.attr('display', 'none');
		});
	};

	this.generateHeatmapTooltipInfo = function(tileDataAndIndex, bucketInfo, menuOption){

		var dataFormat = menuOption.tooltipConfig.rawTooltip ? ChartToolService.rawDataFormat : menuOption.tooltipConfig.customTooltipFormat;
		var format = d3.format(dataFormat);
		var aggregateInfo = 'Range : ' + format(tileDataAndIndex.data.bucket) + '-' + format(tileDataAndIndex.data.bucket + bucketInfo.yStep) + '    ' +
			'Frequency :' + tileDataAndIndex.data.frequency;

		return aggregateInfo;
	};

	this.updateCrossLines = function (sizeInfo, dateFormatter, formatYaxis, focus, mousePositionData) {
		/*  Generate cross lines at the point/cursor
		 */
		// update crossLineX
		focus.select('[name=crossLineX]')
			.attr('x1', mousePositionData.positionX)
			.attr('x2', mousePositionData.positionX).attr('y2', sizeInfo.height);
		var date = dateFormatter(mousePositionData.mouseX);
		var dateText = focus.select('[name=crossLineTipX]')
			.attr('x', mousePositionData.positionX)
			.text(date);
		var boxX = dateText.node().getBBox(); // add background
		var boxXRect = focus.select('[name=crossLineTipRectX]')
			.attr('x', boxX.x - crossLineTipPadding)
			.attr('y', boxX.y - crossLineTipPadding)
			.attr('width', boxX.width + 2 * crossLineTipPadding)
			.attr('height', boxX.height + 2 * crossLineTipPadding);
		// move box to the left if there is not enough space to display it on the right
		flipAnElementHorizontally([dateText, boxXRect], boxX.width, sizeInfo.width, sizeInfo.margin.right, boxX.x, crossLineTipPadding);
		// update crossLineY if needed
		if (mousePositionData.mouseY !==  undefined && mousePositionData.positionY !== undefined) {
			focus.select('[name=crossLineY]')
				.attr('y1', mousePositionData.positionY)
				.attr('x2', sizeInfo.width).attr('y2', mousePositionData.positionY);
			var textY = isNaN(mousePositionData.mouseY) ? 'No Data' : d3.format(formatYaxis)(mousePositionData.mouseY);
			focus.select('[name=crossLineTipY]')
				.attr('y', mousePositionData.positionY)
				.text(textY);
			var boxY = focus.select('[name=crossLineTipY]').node().getBBox(); // add a background
			focus.select('[name=crossLineTipRectY]')
				.attr('x', boxY.x - crossLineTipPadding)
				.attr('y', boxY.y - crossLineTipPadding)
				.attr('width', boxY.width + 2 * crossLineTipPadding)
				.attr('height', boxY.height + 2 * crossLineTipPadding);
		}
	};

	this.updateFocusCirclesPositionWithZoom = function (x, y, focus, brushInNonEmptyRange, extraY, extraYAxisSet) {
		function processCircle(y, extraYAxis){
			if (brushInNonEmptyRange) {
				focus.selectAll('circle.extraYAxis_'+extraYAxis)
					.each(function () {
						var circle = d3.select(this);
						var displayProperty = circle.attr('displayProperty');
						var dataX = circle.attr('dataX');
						var dataY = circle.attr('dataY');

						circle.attr('transform', 'translate(' + x(dataX) + ',' + y(dataY) + ')')
							.style('display', displayProperty);

						if (ChartToolService.isNotInTheDomain(dataX, x.domain())) circle.style('display', 'none');
					});
			} else {
				// nothing needs to be shown
				focus.selectAll('circle.extraYAxis_').style('display', 'none');
			}
		}

		processCircle(y, '');

		for (var iSet of extraYAxisSet) {
			processCircle(extraY[iSet], iSet);
		}
	};

	this.updateHighlightBarWithZoom = function (graph, mouseOverHighlightBar, highlightBar, brushInNonEmptyRange) {
		var currentTimestamp = Number(highlightBar.attr('dataX'));
		if (brushInNonEmptyRange) {
			var dateText = mouseOverHighlightBar.select('.crossLineTip');
			var boxXRect = mouseOverHighlightBar.select('.crossLineTipRect');
			var startingPosition = graph.x0(currentTimestamp);
			highlightBar.attr('x', startingPosition);
			dateText.attr('x', startingPosition);
			boxXRect.attr('x', startingPosition - crossLineTipPadding);
			if (ChartToolService.isNotInTheDomain(currentTimestamp, graph.x0.domain())) highlightBar.style('display', 'none');
		} else {
			highlightBar.style('display', 'none');
		}
	};

	this.updateAnnotations = function (series, sources, x, flags, height) {
		if (series === undefined) return;
		series.forEach(function(metric) {
			if (metric.flagSeries === undefined) return;
			var flagSeries = metric.flagSeries.data;
			flagSeries.forEach(function(d) {
				var label = flags.select('#' + metric.graphClassName + d.flagID);
				// d.x is timestamp of X axis and sometimes it can be in second instead of millisecond
				var dx = UtilService.epochTimeMillisecondConverter(d.x);
				var x_Val = x(dx);
				var y_Val = height - 35;
				// dont render flag if it's outside of the range; similar to focus circle
				if (ChartToolService.isNotInTheDomain(dx, x.domain())) {
					label.style('display', 'none');
				} else {
					var displayingInLegend = ChartToolService.findMatchingMetricInSources(metric, sources).displaying;
					var displayProperty = displayingInLegend? null: 'none';
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
		var timeZoneInfo = isGMT? ' (GMT/UTC)': ' Local Time';
		var str = start + ' - ' + end + timeZoneInfo;
		// TODO: this should be done in angular with 2 way data binding update view
		$('#date-range-' + chartId).text(str);
		return str;
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

	this.resetBothBrushes = function (svg_g, brushes) {
		var resetBrush = this.resetBrush;
		brushes.map(function (item) {
			resetBrush(svg_g, item.name, item.brush);
		});
	};

	// show and hide stuff
	this.showFocusAndTooltip = function (mouseMoveElement, tooltip, isTooltipOn, brushInNonEmptyRange) {
		mouseMoveElement.style('display', null);
		if (brushInNonEmptyRange) {
			this.toggleElementShowAndHide(isTooltipOn, tooltip);
		} else {
			//no need to show the circle or tooltip
			var circles = mouseMoveElement.selectAll('circle');
			if (!circles.empty()) circles.style('display', 'none');
			tooltip.style('display', 'none');
		}
	};

	this.hideHighlightTile = function(mouseMoveElement){
		mouseMoveElement.select('.highlightTile').attr('display', 'none');
	};

	this.hideTooltip = function(tooltip) {
		tooltip.style('display', 'none');
	};

	this.hideFocusAndTooltip = function (mouseMoveElement, tooltip) {
		mouseMoveElement.style('display', 'none');
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
		if (elementName !== undefined) {
			var display = elementOn? null: 'none';
			elementName.style('display', display);
		}
	};

	this.updateColors = function (colorPalette, names, colors, graphClassNames, chartType, sources) {
		var newColorZ = ChartToolService.setColorScheme(colorPalette);
		ChartToolService.bindDefaultColorsWithSources(newColorZ, names);
		for (var i = 0; i < names.length; i++) {
			var tempColor = colors[i] === null ? newColorZ(names[i]) : colors[i];
			var allElementsLinkedWithThisSeries = d3.selectAll('.' + graphClassNames[i]);
			allElementsLinkedWithThisSeries.filter('circle').style('fill', tempColor);
			switch (chartType) {
				case 'scatter':
					allElementsLinkedWithThisSeries.filter('.dot').style('fill', tempColor);
					d3.selectAll('.' + graphClassNames[i] + '_brushDot').style('fill', tempColor);
					break;
				case 'bar':
					allElementsLinkedWithThisSeries.filter('.bar').style('fill', tempColor);
					d3.selectAll('.' + graphClassNames[i] + '_brushBar').style('fill', tempColor);
					break;
				case 'stackbar':
					allElementsLinkedWithThisSeries.filter('.stackbar').style('fill', tempColor);
					d3.selectAll('.' + graphClassNames[i] + '_brushStackbar').style('fill', tempColor);
					break;
				case 'area':
					allElementsLinkedWithThisSeries.filter('path').style('fill', tempColor).style('stroke', tempColor);
					d3.select('.' + graphClassNames[i] + '_brushArea').style('fill', tempColor);
					break;
				case 'stackarea':
					allElementsLinkedWithThisSeries.filter('path').style('fill', tempColor).style('stroke', tempColor);
					d3.select('.' + graphClassNames[i] + '_brushStackarea').style('fill', tempColor);
					break;
				// case 'line':
				default:
					allElementsLinkedWithThisSeries.filter('path').style('stroke', tempColor);
					d3.select('.' + graphClassNames[i] + '_brushLine').style('stroke', tempColor);
			}
			// update color info for legend
			d3.select('.' + graphClassNames[i] + '_legend').style('color', tempColor);
			sources[i].color = tempColor;
		}
	};

	this.adjustTooltipItemsBasedOnDisplayingSeries = function (series, sources, x, tipItems, timestampSelector) {
		var xDomain = x.domain();
		series.forEach(function (metric) {
			var source = ChartToolService.findMatchingMetricInSources(metric, sources);
			// metric with no data
			if (metric === null || metric.data === undefined || metric.data.length === 0 ||
				ChartToolService.isMetricNotInTheDomain(metric, xDomain, timestampSelector)) {
				tipItems.selectAll('.' + source.graphClassName).style('display', 'none');
			}
		});
	};

	//redraw xAxis
	this.redrawAxis = function (xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, extraYAxisR, extraYAxisRG, extraYAxisSet, isSmallChart) {
		// rotate text label for 'smallChart'
		if (isSmallChart) {
			xAxisG.call(xAxis)
				.selectAll("text")
				.attr("y", 5)
				.attr("x", -9)
				.attr("dy", ".35em")
				.attr("transform", "rotate(-65)")
				.style("text-anchor", "end");
		} else {
			xAxisG.call(xAxis);
		}

		yAxisG.call(yAxis);  //redraw yAxis
		yAxisRG.call(yAxisR); //redraw yAxis right
		if(extraYAxisSet){
			for(var iSet of extraYAxisSet){
				extraYAxisRG[iSet].call(extraYAxisR[iSet]);
			} //redraw extra yAxis
		}
	};

	this.redrawGrid = function (xGrid, xGridG, yGrid, yGridG) {
		xGridG.call(xGrid);
		yGridG.call(yGrid);
	};

	this.redrawGraph = function (metric, source, chartType, graph, mainChart) {
		// metric with no defined data or hidden
		var tempDomain, displayingInLegend = source.displaying;
		if (metric === null || metric.data === undefined || !displayingInLegend) return;
		switch (chartType) {
			case 'scatter':
				mainChart.selectAll('circle.dot.' + metric.graphClassName)
					.attr('cx', function (d) { return UtilService.validNumberChecker(graph.x(d[0])); })
					.attr('cy', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); });
				break;
			case 'bar':
				var tempHeight = graph.y.range()[0];
				tempDomain = graph.x0.domain();
				mainChart.selectAll('rect.bar.' + metric.graphClassName)
					.attr('x', function (d) { return UtilService.validNumberChecker(graph.x0(d[0])); })
					.attr('y', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
					.attr('width', graph.x1.bandwidth())
					.attr('height', function(d) {
						var newHeight = UtilService.validNumberChecker(tempHeight - graph.y(d[1]));
						return newHeight < 0 ? 0 : newHeight;
					})
					.attr('transform', function() { return 'translate(' + graph.x1(metric.graphClassName) + ',0)'; })
					.style('display', function (d) { return ChartToolService.isNotInTheDomain(d[0], tempDomain) ? 'none' : null; });
				break;
			case 'stackbar':
				tempDomain = graph.x0.domain();
				var stackbars = mainChart.selectAll('rect.stackbar.' + metric.graphClassName).style('display', 'none');
				stackbars.data(metric.data)
					.attr('x', function (d) { return UtilService.validNumberChecker(graph.x0(d.data.timestamp)); })
					.attr('y', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
					.attr('width', graph.x0.bandwidth())
					.attr('height', function (d) { return UtilService.validNumberChecker(graph.y(d[0]) - graph.y(d[1])); })
					.style('display', function (d) { return ChartToolService.isNotInTheDomain(d.data.timestamp, tempDomain) ? 'none' : null; });
				break;
			default:
				mainChart.select('path.' + chartType + '.' + metric.graphClassName)
					.datum(metric.data)
					.attr('d', graph);
		}
	};

	this.redrawGraphs = function (series, sources, chartType, graph, mainChart, extraGraph) {
		var chartElementService = this;
		series.forEach(function (metric) {
			var source = ChartToolService.findMatchingMetricInSources(metric, sources);
			if (metric.extraYAxis) {
				chartElementService.redrawGraph(metric, source, chartType, extraGraph[metric.extraYAxis], mainChart);
			} else {
				chartElementService.redrawGraph(metric, source, chartType, graph, mainChart);
			}
		});
	};

	this.redrawBrushGraphsWithNewXDomain = function (series, sources, chartType, graph2, context) {
		var chartElementService = this;
		var cappedChartTypeStr = UtilService.capitalizeString(chartType);
		series.forEach(function (metric) {
			var source = ChartToolService.findMatchingMetricInSources(metric, sources);
			switch (chartType) {
				case 'scatter':
					context.selectAll('circle.brushDot.' + metric.graphClassName + '_brushDot' +'.extraYAxis_' + (metric.extraYAxis || ''))
						.attr('cx', function (d) { return UtilService.validNumberChecker(graph2.x(d[0])); });
					break;
				// TODO: bar chart ones are turn off @ line 1115 in lineChart.js
				case 'bar':
					context.selectAll('rect.bar.' + 'brushBar.' + metric.graphClassName + '_brushBar' +' extraYAxis_' + (metric.extraYAxis || ''))
						.attr('x', function (d) { return UtilService.validNumberChecker(graph2.x0(d[0])); })
						.attr('width', graph2.x1.bandwidth())
						.attr('transform', function() { return 'translate(' + graph2.x1(metric.graphClassName) + ',0)'; });
					break;
				case 'stackbar':
					context.selectAll('rect.stackbar.' + 'brushStackbar.' + metric.graphClassName + '_brushStackbar' + ' extraYAxis_' + (metric.extraYAxis || ''))
						.data(metric.data)
						.attr('x', function (d) { return UtilService.validNumberChecker(graph2.x0(d.data.timestamp)); })
						.attr('width', graph2.x0.bandwidth());
					break;
				default:
					context.select('path.' + 'brush' + cappedChartTypeStr + '.' + metric.graphClassName + '_brush' + cappedChartTypeStr + '.extraYAxis_' + (metric.extraYAxis || ''))
						.datum(metric.data)
						.attr('d', graph2);

			}
		})
	}

	//rescale YAxis based on XAxis Domain
	this.reScaleYAxis = function (series, sources, y, yScalePlain, yScaleType, agYMin, agYMax, isDataStacked, isChartDiscrete, extraY, extraYScalePlain, extraYAxisSet) {
		if (!series) return;
		if (agYMin !== undefined && agYMax !== undefined) return; //hard coded ymin & ymax
		var datapoints = [];
		var extraDatapoints = {};
		var extent;
		for(var iSet of extraYAxisSet){
			extraDatapoints[iSet] = [];
		}
		series.forEach(function (metric) {
			// metric with no data or hidden
			var displayingInLegend = ChartToolService.findMatchingMetricInSources(metric, sources).displaying;
			if (metric === null || metric.data === undefined || metric.data.length === 0 || !displayingInLegend) return;

			if (metric.extraYAxis) {
				extraDatapoints[metric.extraYAxis] = extraDatapoints[metric.extraYAxis].concat(metric.data);
			} else {
				datapoints = datapoints.concat(metric.data);
			}
		});

		extent =  ChartToolService.getYDomainOfSeries(datapoints, isDataStacked);
		y.domain(ChartToolService.processYDomain(extent, yScalePlain, yScaleType, agYMin, agYMax, isDataStacked, isChartDiscrete));

		for (iSet of extraYAxisSet) {
			extent = ChartToolService.getYDomainOfSeries(extraDatapoints[iSet], isDataStacked);
			extraY[iSet].domain(ChartToolService.processYDomain(extent, extraYScalePlain[iSet], yScaleType, undefined, undefined, isDataStacked, isChartDiscrete));
		}
	};

	this.resizeMainChartElements = function (sizeInfo, svg, svg_g, needToAdjustHeight) {
		if (needToAdjustHeight) {
			svg.attr('height', sizeInfo.height + sizeInfo.margin.top + sizeInfo.margin.bottom);
			svg_g.attr('height', sizeInfo.height);
		}
		svg.attr('width', sizeInfo.widthFull + sizeInfo.margin.left + sizeInfo.margin.right);
		svg_g.attr('width', sizeInfo.widthFull)
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

	this.resizeAxis = function (sizeInfo, xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, needToAdjustHeight, mainChart, xAxisConfig, extraYAxisR, extraYAxisRG, extraYAxisSet, isSmallChart) {
		if (needToAdjustHeight) {
			xAxisG.attr('transform', 'translate(0,' + sizeInfo.height + ')');
		}
		yAxisRG.attr('transform', 'translate(' + sizeInfo.width + ')');

		if(extraYAxisRG){
			var index = 1;
			for(var iSet of extraYAxisSet){
				extraYAxisRG[iSet].attr('transform', 'translate(' + (sizeInfo.width + index++ * extraYAxisPadding) + ')');
			}
		}

		this.redrawAxis(xAxis, xAxisG, yAxis, yAxisG, yAxisR, yAxisRG, extraYAxisR, extraYAxisRG, extraYAxisSet, isSmallChart);

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

	this.resizeBrush = function (sizeInfo, brush, brushG, context, x2, xAxis2, xAxisG2, y2, needToAdjustHeight, extraY2, extraYAxisSet) {
		if (needToAdjustHeight) {
			context.attr('transform', 'translate(0,' + sizeInfo.margin2.top + ')');
			xAxisG2.attr('transform', 'translate(0,' + sizeInfo.height2 + ')');
			y2.range([sizeInfo.height2, 0]);
			for(var iSet of extraYAxisSet){
				extraY2[iSet].range([sizeInfo.height2, 0]);
			}
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

	this.resizeGraph = function (svg_g, graph, sources, chartType, extraYAxis) {
		switch (chartType) {
			case 'scatter':
				svg_g.selectAll('.dot' + '.extraYAxis_' + extraYAxis)
					.attr('cx', function (d) { return UtilService.validNumberChecker(graph.x(d[0])); })
					.attr('cy', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); });
				break;
			case 'bar':
				var tempHeight = graph.y.range()[0];
				sources.map(function (source) {
					if (source.displaying) {
						var bars = svg_g.selectAll('.bar' + '.' + source.graphClassName + '.extraYAxis_' + extraYAxis);
						if (!bars.empty()) {
							bars.attr('x', function (d) { return UtilService.validNumberChecker(graph.x0(d[0])); })
								.attr('y', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
								.attr('width', graph.x1.bandwidth())
								.attr('height', function (d) {
									var newHeight = UtilService.validNumberChecker(tempHeight - graph.y(d[1]));
									return newHeight < 0 ? 0 : newHeight;
								})
								.attr('transform', function () { return 'translate(' + graph.x1(source.graphClassName) + ',0)'; });
						}
					}
				});
				break;
			case 'stackbar':
				svg_g.selectAll('.stackbar' + '.extraYAxis_' + extraYAxis)
					.attr('x', function (d) { return UtilService.validNumberChecker(graph.x0(d.data.timestamp)); })
					.attr('y', function (d) { return UtilService.validNumberChecker(graph.y(d[1])); })
					.attr('width', graph.x0.bandwidth())
					.attr('height', function (d) { return UtilService.validNumberChecker(graph.y(d[0]) - graph.y(d[1])); });
				break;
			default:
				svg_g.selectAll('.' + chartType + '.extraYAxis_' + extraYAxis).attr('d', graph);
		}
	};

	this.resizeGraphs = function (svg_g, graph, sources, chartType, extraGraph, extraYAxisSet) {
		this.resizeGraph(svg_g, graph, sources, chartType, '');
		for (var iSet of extraYAxisSet) {
			this.resizeGraph(svg_g, extraGraph[iSet], sources, chartType, iSet);
		}
	};

	this.resizeBrushGraph = function (svg_g, graph2, chartType, extraYAxis){
		switch (chartType) {
			case 'scatter':
				svg_g.selectAll('.brushDot' + '.extraYAxis_' + extraYAxis)
					.attr('cx', function (d) { return UtilService.validNumberChecker(graph2.x(d[0])); } )
					.attr('cy', function (d) { return UtilService.validNumberChecker(graph2.y(d[1])); } );
				break;
			case 'bar':
				var tempHeight = graph2.y.range()[0];
				svg_g.selectAll('.brushBar' + '.extraYAxis_' + extraYAxis)
					.attr('x', function (d) { return UtilService.validNumberChecker(graph2.x0(d[0])); } )
					.attr('y', function (d) { return UtilService.validNumberChecker(graph2.y(d[1])); } )
					.attr('width', graph2.x1.bandwidth())
					.attr('height', function(d) { return tempHeight - graph2.y(d[1]); });
				break;
			case 'stackbar':
				svg_g.selectAll('.brushStackbar' + '.extraYAxis_' + extraYAxis)
					.attr('x', function (d) { return UtilService.validNumberChecker(graph2.x0(d.data.timestamp)); })
					.attr('y', function (d) { return UtilService.validNumberChecker(graph2.y(d[1])); })
					.attr('width', graph2.x0.bandwidth())
					.attr('height', function (d) { return UtilService.validNumberChecker(graph2.y(d[0]) - graph2.y(d[1])); });
				break;
			default:
				svg_g.selectAll('.brush' + UtilService.capitalizeString(chartType) + '.extraYAxis_' + extraYAxis).attr('d', graph2);
		}
	};

	this.resizeBrushGraphs = function (svg_g, graph2, chartType, extraGraph2, extraYAxisSet) {
		this.resizeBrushGraph(svg_g, graph2, chartType, '');
		for(var iSet of extraYAxisSet){
			this.resizeBrushGraph(svg_g, extraGraph2[iSet], chartType, iSet);
		}
	};

	this.createExtraYAxisRelatedElements = function (x, x2, extraYAxisSet, sizeInfo, yScaleType, yScaleConfigValue, yAxisConfig, chart){
		//every extra YAxis related elements
		if (extraYAxisSet.size > 0) {
			var extraY = {};
			var extraY2 = {};
			var extraYScalePlain = {};
			var extraYAxisR = {};
			var extraGraph = {};
			var extraGraph2 = {};
			var extraYAxisRG = {};
			var iSetIndex = 1;
			for(var iSet of extraYAxisSet){
				var extraYObj = ChartToolService.getY(sizeInfo, yScaleType, yScaleConfigValue);
				extraY[iSet] = extraYObj.y;
				var extraYObj2 = ChartToolService.getY({height: sizeInfo.height2}, yScaleType, yScaleConfigValue);
				extraY2[iSet] = extraYObj2.y;
				extraYScalePlain[iSet] = extraYObj.yScalePlain;
				extraYAxisR[iSet] = this.createExtraYAxisRElements(extraY[iSet], yAxisConfig);
				extraGraph[iSet] = this.createGraph(x, extraY[iSet]); //default chart type line chart TODO support dot chart?
				extraGraph2[iSet] = this.createGraph(x2, extraY2[iSet]);
				extraYAxisRG[iSet] = this.appendExtraYAxisElement(sizeInfo.width + extraYAxisPadding * iSetIndex++, chart, extraYAxisR[iSet]);
			}
			return {
				extraY: extraY,
				extraYScalePlain: extraYScalePlain,
				extraYAxisR: extraYAxisR,
				extraGraph: extraGraph,
				extraYAxisRG: extraYAxisRG,
				extraY2: extraY2,
				extraGraph2: extraGraph2
			};
		} else {
			return null;
		}
	};

	this.updateGraphsX = function (graph, x, timestampSelector) {
		graph.x(function (d) {
			return UtilService.validNumberChecker(x(timestampSelector(d)));
		});
	};

	this.updateCustomizedChartTypeGraphX = function (chart, graph, x, chartType) {
		graph.x = x;
		if (chartType === 'scatter') {
			chart.selectAll('circle.dot').attr('cx', function (d) { return UtilService.validNumberChecker(x(d[0])); });
		}
		// bar charts element do not need to update x attr since they use x0 which is a discrete domain of epoch values
	};

	this.toggleGraphDisplay = function (source, displayProperty) {
		d3.selectAll('.' + source.graphClassName)
			.style('display', displayProperty)
			.attr('displayProperty', displayProperty); //this is for recording the display property when circle is outside range
	};
}]);
