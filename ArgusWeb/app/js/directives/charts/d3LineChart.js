'use strict';

angular.module('argus.directives.charts.d3LineChart', [])
  .directive('agD3LineGraph', function() {
    return {
      restrict: 'E',
      replace: false,
      link: function(scope, element, attrs) {
        var currSeries = attrs.series;
        // Layout parameters
        var margin = {top: 20, right: 20, bottom: 450, left: 50};
        var tipPadding = 6;
        var width = element.parent().width() - margin.left - margin.right;
        var height = 800 - margin.top - margin.bottom;

        // Local helpers
        var bisectDate = d3.bisector(function(d) { return d[0]; }).left;
        var formatDate = d3.timeFormat('%A, %b %e, %H:%M');
        var formatValue = d3.format(',');
        var tooltipCreator = function() {};

        // Base graph setup
        var x = d3.scaleTime().range([0, width]);
        var y = d3.scaleLinear().range([height, 0]);
        var z = d3.scaleOrdinal().range(d3.schemeCategory10);

        var xAxis = d3.axisBottom().scale(x);

        var yAxis = d3.axisLeft()
          .scale(y)
          .tickFormat(d3.format('s'));

        var line = d3.line()
          .x(function(d) { return x(d[0]); })
          .y(function(d) { return y(d[1]); });

        var svg = d3.select(element[0]).append('svg')
          .attr('width', width + margin.left + margin.right)
          .attr('height', height + margin.top + margin.bottom)
          .append('g')
          .attr('transform', 'translate(' + margin.left + ',' + margin.top + ')');

        svg.append('g')
          .attr('class', 'x axis')
          .attr('transform', 'translate(0,' + height + ')')
          .call(xAxis);

        svg.append('g')
          .attr('class', 'y axis')
          .call(yAxis);

        // Mouseover/tooltip setup
        var focus = svg.append('g')
          .attr('class', 'focus')
          .style('display', 'none');
        focus.append('circle')
            .attr('r', 4.5);

        svg.append('rect')
          .attr('class', 'overlay')
          .attr('width', width)
          .attr('height', height)
          .on('mouseover', function() { focus.style('display', null); })
          .on('mouseout', function() { focus.style('display', 'none'); })
          .on('mousemove', mousemove);

        var tip = svg.append('g')
          .attr('class', 'legend');
        var tipBox = tip.append('rect')
          .attr('rx', tipPadding)
          .attr('ry', tipPadding);
        var tipItems = tip.append('g')
          .attr('class', 'legend-items');

        function mousemove() {
          if (!currSeries || currSeries.length === 0) {
            return;
          }
          var datapoints = [];
          focus.selectAll('circle').remove();
          var mouseX = x.invert(d3.mouse(this)[0]);
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

        // Update graph on new metric results
        scope.$watch(attrs.series, function(series) {
          if (!series) return;
          
          var allDatapoints = [];
          var names = series.map(function(metric) { return metric.id; });
          var svg = d3.select('svg').select('g');
          var svgTransition = d3.select(element[0]).transition();

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

          svg.selectAll('.line').remove();
          series.forEach(function(metric) {
            svg.append('path')
              .attr('class', 'line')
              .attr('d', line(metric.data))
              .style('stroke', z(metric.id));
          });

          svgTransition.select('.x.axis')
            .duration(750)
            .call(xAxis);
          svgTransition.select('.y.axis')
            .duration(750)
            .call(yAxis);
        });
      }
    };
  });
