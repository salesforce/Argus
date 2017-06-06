/*global angular:false, $:false, console:false, window:false, d3:false */
'use strict';

angular.module('argus.controllers.metricsBrowsing', ['ngResource'])
.controller('MetricsBrowsing', ['$scope', 'growl', 'Browsing', 'UtilService', function ($scope, growl, Browsing, UtilService) {
	$scope.expression = '';
	// TODO: move to a service
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
	// TODO: move to a service
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
	// TODO: work on call backs
	var queryUntilMultipleReturns = function (expression) {
		console.log(expression);
		Browsing.query({query: expression}).$promise.then(function(data) {
			if (data.length === 1) {
				queryUntilMultipleReturns(expression + data[0] + '.');
			} else if (data.length > 0) {
				return data;
			} else {
				return;
			}
		}, function (error) {
			growl.error(error.data.message);
			console.log(error);
		});
	};

	var treeHeight = 1;
	var treeData = {
		'name': 'Start',
		'children': []
	};
	var margin = {top: 20, right: 50, bottom: 20, left: 50},
		width = angular.element('#treeDiagram').width() - margin.left - margin.right,
		height = 650 - margin.top - margin.bottom;

	var svg = d3.select('#treeDiagram').append('svg')
		.attr('width', width + margin.right + margin.left)
		.attr('height', height + margin.top + margin.bottom)
		.append('g')
		.attr('transform', 'translate('+ margin.left + ',' + margin.top + ')');

	var i = 0,
		duration = 750,
		root;
	// declares a tree layout and assigns the size
	var treemap = d3.tree().size([height, width])
					.separation(function(a, b) {
						return (a.parent == b.parent ? 1 : 2) * a.depth * 0.8;
					});

	Browsing.query({query: ''}).$promise.then(function(data) {
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


		nodes.forEach(function(d){
			// add a field of display name
			if (d.displayName === undefined) {
				var indexCutOff = d.data.name.lastIndexOf('.');
				if (indexCutOff === -1) {
					d.displayName = d.data.name;
				} else {
					d.displayName = d.data.name.substr(indexCutOff);
				}
			}
			// Normalize for fixed-depth.
			if (d.parent) {
				d.y = d.parent.y + 80;
				if (d.parent.parent) {
					// find the longest name among other children of the current node
					var peers = d.parent.parent.children;
					var longestNameLength = peers.reduce(function(currentMax, node) {
						return node.displayName.length > currentMax ? node.displayName.length : currentMax;
					}, 0);
					if (longestNameLength > 10) d.y += longestNameLength * 4;
				} else if (d.parent.displayName && d.parent.displayName.length > 10) {
					// this does not really happen
					d.y += d.parent.displayName.length * 4;
				}
			} else {
				d.y = d.depth * 80;
			}
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
					.style('stroke', function(d) {
						return d.noChildren ? 'maroon' : 'steelblue';
					})
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
					.text(function(d) { return d.displayName; });

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
					.style('stroke', function(d) {
						return d.noChildren ? 'maroon' : 'steelblue';
					})
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
				var queryExpression;
				queryExpression = d.data.name.length > 1 > 0 ? d.data.name + '.' : d.data.name;
				// console.log(queryUntilMultipleReturns(queryExpression));

				// var data = queryUntilMultipleReturns(queryExpression);
				// if (data !== undefined) {
				//     if (d.depth + 1 > treeHeight) {
				//         treeHeight += 1;
				//         updateHeight(root);
				//     }
				//     if (d.data.name.length > 1) {
				//         d.data.children = createHierarchicalDataWithExpression(data, queryExpression);
				//     } else {
				//         d.data.children = createHierarchicalData(data);
				//     }
				//     var additionalTree = d3.hierarchy(d.data, function(d) { return d.children; });
				//     d.children = additionalTree.children;
				//     additionalTree.children.forEach(function(child) {
				//         child.depth += d.depth;
				//         child.parent = d;
				//     });
				//     d.children.forEach(collapse);
				// } else {
				//     d.noChildren = true;
				// }
				Browsing.query({query: queryExpression}).$promise.then(function(data) {
					if (data.length > 0) {
						if (d.depth + 1 > treeHeight) {
							treeHeight += 1;
							updateHeight(root);
						}
						// creat a new tree using current node as the root with new data
						if (d.data.name.length > 1) {
							d.data.children = createHierarchicalDataWithExpression(data, queryExpression);
						} else {
							d.data.children = createHierarchicalData(data);
						}
						var additionalTree = d3.hierarchy(d.data, function(d) { return d.children; });
						// append new tree to the existing one
						d.children = additionalTree.children;
						additionalTree.children.forEach(function(child) {
							child.depth += d.depth;
							child.parent = d;
						});
						d.children.forEach(collapse);
					} else {
						// there is the end of the expression: no query results
						d.noChildren = true;
					}
					update(d);
				}, function (error) {
					growl.error(error.data.message);
					console.log(error);
				});
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
