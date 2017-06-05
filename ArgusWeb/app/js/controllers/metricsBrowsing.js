/*global angular:false, $:false, console:false, window:false, d3:false */
'use strict';

angular.module('argus.controllers.metricsBrowsing', ['ngResource'])
.controller('MetricsBrowsing', ['$scope', 'growl', 'Browsing', 'UtilService', function ($scope, growl, Browsing, UtilService) {
	$scope.expression = '';
	var currentExpression = '';

    var createHierarchicalData = function (array) {
		var result = [];
		array.map(function(item) {
			// assume there is no duplicates
			result.push({
				name: item,
				children: []
			});
		});
		return result;
	};
    var createHierarchicalDataWithExpression = function (array, existingExpression) {
        var result = [];
        array.map(function(item) {
            result.push({
                name: existingExpression + item,
                children: []
            });
        });
        return result;
    };

    var treeHeight = 1;
    var treeData = {
		'name': 'Start',
		'children': []
	};
	var margin = {top: 20, right: 50, bottom: 20, left: 50},
		width = angular.element('#treeDiagram').width() - margin.left - margin.right,
		height = 800 - margin.top - margin.bottom;

	var svg = d3.select('#treeDiagram').append('svg')
		.attr('width', width + margin.right + margin.left)
		.attr('height', height + margin.top + margin.bottom)
		.append('g')
		.attr('transform', 'translate('+ margin.left + ',' + margin.top + ')');

	var i = 0,
		duration = 750,
		root;
	// declares a tree layout and assigns the size
	var treemap = d3.tree().size([height, width]);

	Browsing.query({query: currentExpression}).$promise.then(function(data) {
		treeData.children = createHierarchicalData(data);
		root = d3.hierarchy(treeData, function(d) { return d.children; });
		root.x0 = height / 2;
		root.y0 = 0;
		root.children.forEach(collapse);
		update(root);
	}, function (error) {
		growl.error(error.data.message);
		console.log(error);
	});

	// Collapse the node and all it's children
	function collapse(d) {
		if (d.children) {
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
		nodes.forEach(function(d){
            d.y = d.depth * 180;
            d.index = d.parent ? d.parent.children.indexOf(d) : 0;
        });

		// ****************** Nodes section ***************************

		// Update the nodes...
		var node = svg.selectAll('g.node')
						.data(nodes, function(d) {return d.id || (d.id = ++i); });

		// Enter any new modes at the parent's previous position.
		var nodeEnter = node.enter().append('g')
							.attr('class', 'node')
							.attr('transform', function(d) {
								return 'translate(' + source.y0 + ',' + source.x0 + ')';
							})
							.on('click', click);

		// Add Circle for the nodes
		nodeEnter.append('circle')
					.attr('class', 'node')
					.attr('r', 1e-6)
					.style('fill', function(d) {
						return d._children ? 'lightsteelblue' : '#fff';
					});

		// Add labels for the nodes
		nodeEnter.append('text')
					.attr('dy', '.35em')
                    .attr('x', function(d) {
                        return d.depth === 0 ? -13 : 13;
                    })
					.attr('text-anchor', function(d) {
                        return d.depth === 0 ? 'end': 'start';
					})
					.text(function(d) { return d.data.name; });

		// UPDATE
		var nodeUpdate = nodeEnter.merge(node);

		// Transition to the proper position for the node
		nodeUpdate.transition()
					.duration(duration)
					.attr('transform', function(d) {
						return 'translate(' + d.y + ',' + d.x + ')';
					});

		// Update the node attributes and style
		nodeUpdate.select('circle.node')
					.attr('r', 4)
					.style('fill', function(d) {
						return d._children ? 'lightsteelblue' : '#fff';
					})
					.attr('cursor', 'pointer');


		// Remove any exiting nodes
		var nodeExit = node.exit().transition()
							.duration(duration)
							.attr('transform', function(d) {
								return 'translate(' + source.y + ',' + source.x + ')';
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
						.data(links, function(d) {
                            return d.id; });

		// Enter any new links at the parent's previous position.
		var linkEnter = link.enter().insert('path', 'g')
							.attr('class', 'link')
							.attr('d', function(d){
								var o = {x: source.x0, y: source.y0}
								return diagonal(o, o);
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
                update(d);
            } else if (d._children) {
                d.children = d._children;
                d._children = null;
                update(d);
            } else {
                if (d.data.name.length > 1) {
    				Browsing.query({query: currentExpression}).$promise.then(function(data) {
                        // TODO: add too the tree while having new names
                        console.log('expand more');
                    }, function (error) {
                        growl.error(error.data.message);
                		console.log(error);
                    });
    			} else {
    				Browsing.query({query: d.data.name}).$promise.then(function(data) {
                        if (data.length > 0) {
                            // tree's height need to increase
                            if (d.depth + 1 > treeHeight) {
                                treeHeight += 1;
                                updateHeight(root);
                            }
                            d.data.children = createHierarchicalData(data);
                            var additionalTree = d3.hierarchy(d.data, function(d) { return d.children; });
                            // append new Leafs
                            d.children = additionalTree.children;
                            // update depth and parent for each child
                            additionalTree.children.forEach(function(child) {
                                child.depth += d.depth;
                                child.parent = d;
                            });
                            d.children.forEach(collapse);
                        } else {
                            // this is the end of the query
                            d.children = d._children;
                            d._children = null;
                        }
            			update(d);
                    }, function (error) {
                        growl.error(error.data.message);
                		console.log(error);
                    });
    			}
            }
		}

        function updateHeight(d) {
            d.height++;
            if (d.children) d.children.forEach(updateHeight);
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
