/*global angular:false, $:false, console:false, window:false, d3:false */
'use strict';

angular.module('argus.controllers.metricsBrowsing', ['ngResource'])
.controller('MetricsBrowsing', ['$scope', 'growl', 'Browsing', 'UtilService', function ($scope, growl, Browsing, UtilService) {
	$scope.expression = '';
	var defaultExpression = '';

	var treeData = {
        "name": "Start",
        "children": []
    };
	var margin = {top: 20, right: 90, bottom: 30, left: 90},
		width = angular.element('#treeDiagram').width() - margin.left - margin.right,
		height = 600 - margin.top - margin.bottom;

	var svg = d3.select("body").append("svg")
		.attr("width", width + margin.right + margin.left)
		.attr("height", height + margin.top + margin.bottom)
		.append("g")
		.attr("transform", "translate("+ margin.left + "," + margin.top + ")");

	var i = 0,
		duration = 750,
		root;
	// declares a tree layout and assigns the size
	var treemap = d3.tree().size([height, width]);

    function createHierarchicalData(array) {
        var result = [];
        array.map(function(item) {
            // assume there is no duplicates
            result.push({
                name: item,
                children: []
            });
        });
        return result;
    }

	Browsing.query({query: defaultExpression}).$promise.then(function(data) {
        // TODO: better ways to remove promise on
        // treeData = JSON.parse(angular.toJson(data));
        treeData.children = createHierarchicalData(UtilService.removeDataResponseOverhead(data));
		root = d3.hierarchy(treeData, function(d) { return d.children; });
		root.x0 = height / 2;
		root.y0 = 0;
		root.children.forEach(collapse);
		update(root);
		// console.log(data);
	}, function (error) {
		growl.error(error.data.message);
		console.log(error);
	});

	// Collapse the node and all it's children
	function collapse(d) {
		if(d.children) {
			d._children = d.children;
			d._children.forEach(collapse);
			d.children = null;
		}
	}

	function update(source) {
		// Assigns the x and y position for the nodes
		var treeData = treemap(root);

		// Compute the new tree layout.
		var nodes = treeData.descendants(),
		links = treeData.descendants().slice(1);

		// Normalize for fixed-depth.
		nodes.forEach(function(d){ d.y = d.depth * 180});

		// ****************** Nodes section ***************************

		// Update the nodes...
		var node = svg.selectAll('g.node')
						.data(nodes, function(d) {return d.id || (d.id = ++i); });

		// Enter any new modes at the parent's previous position.
		var nodeEnter = node.enter().append('g')
							.attr('class', 'node')
							.attr("transform", function(d) {
								return "translate(" + source.y0 + "," + source.x0 + ")";
							})
							.on('click', click);

		// Add Circle for the nodes
		nodeEnter.append('circle')
					.attr('class', 'node')
					.attr('r', 1e-6)
					.style("fill", function(d) {
						return d._children ? "lightsteelblue" : "#fff";
					});

		// Add labels for the nodes
		nodeEnter.append('text')
					.attr("dy", ".35em")
					.attr("x", function(d) {
						return d.children || d._children ? -13 : 13;
					})
					.attr("text-anchor", function(d) {
						return d.children || d._children ? "end" : "start";
					})
					.text(function(d) { return d.data.name; });

		// UPDATE
		var nodeUpdate = nodeEnter.merge(node);

		// Transition to the proper position for the node
		nodeUpdate.transition()
					.duration(duration)
					.attr("transform", function(d) {
						return "translate(" + d.y + "," + d.x + ")";
					});

		// Update the node attributes and style
		nodeUpdate.select('circle.node')
					.attr('r', 4)
					.style("fill", function(d) {
						return d._children ? "lightsteelblue" : "#fff";
					})
					.attr('cursor', 'pointer');


		// Remove any exiting nodes
		var nodeExit = node.exit().transition()
							.duration(duration)
							.attr("transform", function(d) {
								return "translate(" + source.y + "," + source.x + ")";
							})
							.remove();

		// On exit reduce the node circles size to 0
		nodeExit.select('circle')
				.attr('r', 1e-6);

		// On exit reduce the opacity of text labels
		nodeExit.select('text')
				.style('fill-opacity', 1e-6);

		// ****************** links section ***************************

		// Update the links...
		var link = svg.selectAll('path.link')
						.data(links, function(d) { return d.id; });

		// Enter any new links at the parent's previous position.
		var linkEnter = link.enter().insert('path', "g")
							.attr("class", "link")
							.attr('d', function(d){
								var o = {x: source.x0, y: source.y0}
								return diagonal(o, o)
							});

		// UPDATE
		var linkUpdate = linkEnter.merge(link);

		// Transition back to the parent element position
		linkUpdate.transition()
					.duration(duration)
					.attr('d', function(d){ return diagonal(d, d.parent) });

		// Remove any exiting links
		var linkExit = link.exit().transition()
							.duration(duration)
							.attr('d', function(d) {
								var o = {x: source.x, y: source.y}
								return diagonal(o, o)
							})
							.remove();

		// Store the old positions for transition.
		nodes.forEach(function(d){
			d.x0 = d.x;
			d.y0 = d.y;
		});

		// Creates a curved (diagonal) path from parent to the child nodes
		function diagonal(s, d) {
            // /*jshint esversion: 6 */
			var path = `M ${s.y} ${s.x}
			C ${(s.y + d.y) / 2} ${s.x},
			${(s.y + d.y) / 2} ${d.x},
			${d.y} ${d.x}`

			return path
		}

		// Toggle children on click.
		function click(d) {
			if (d.children) {
				d._children = d.children;
				d.children = null;
			} else {
				d.children = d._children;
				d._children = null;
			}
			update(d);
		}
	}

	// search text box
	$scope.$watch('expression', function (newValue, oldValue) {
		if (newValue === oldValue) return;
		Browsing.query({query: newValue}).$promise.then(function (data) {
			console.log(data);
		}, function (error) {
			growl.error(error.data.message);
			console.log(error);
		});
	});
}]);
