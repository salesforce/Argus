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

	var queryUntilMultipleOrNoReturns = function (Browsing, expression, firstCall) {
		return Browsing.query({query: expression}).$promise.then(function(data) {
			if (data.length === 1) {
				// keep querying since there is only one new param
				return queryUntilMultipleOrNoReturns(Browsing, expression + data[0] + '.', false);
			} else if (data.length === 0) {
				// the end of the query is reached
				return {
					endOfQuery: true,
					data: firstCall ? [] : [expression.slice(0, -1)],
					expression: expression
				};
			} else {
				// multiple nwe params are returned from query
				return {
					data: data,
					expression: expression
				};
			}
		});
	};

	var treeHeight = 1;
	var treeData = {
		'name': 'Start',
		'children': []
	};
	var defaultHeight = window.innerHeight < 850 ? 550 : 750;
	var margin = {top: 5, right: 5, bottom: 5, left: 40},
		width = angular.element('#treeDiagram').width() - margin.left - margin.right,
		height = defaultHeight - margin.top - margin.bottom;

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
						var result = a.parent == b.parent ? 1 : 2;
						// give more separation when its really deep in the tree
						if (a.depth > 2) result *= (a.depth * 0.4);
						return result;
					});

	Browsing.query({query: ''}).$promise.then(function(data) {
		treeData.children = createHierarchicalData(data);
		root = d3.hierarchy(treeData, function(d) { return d.children; });
		root.displayName = root.data.name;
		root.x0 = height / 2;
		root.y0 = 0;
		root.children.forEach(function(d) { d.displayName = d.data.name; });
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
			if (d.parent) {
				d.y = d.parent.y + 80;
				if (d.parent.parent) {
					// find the longest name among other children of the current node
					var peers = d.parent.parent.children;
					var longestNameLength = peers.reduce(function(currentMax, node) {
						return node.displayName.length > currentMax ? node.displayName.length : currentMax;
					}, 0);
					if (longestNameLength > 8) d.y += longestNameLength * 4;
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
								var o = {x: source.x0, y: source.y0};
								return diagonal(o, o);
							});

		// UPDATE
		var linkUpdate = linkEnter.merge(link);

		// Transition back to the parent element position
		linkUpdate.transition()
					.duration(duration)
					.attr('d', function(d){ return diagonal(d, d.parent); });

		// Remove any exiting links
		var linkExit = link.exit().transition()
							.duration(duration)
							.attr('d', function(d) {
								var o = {x: source.x, y: source.y};
								return diagonal(o, o);
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
			${d.y} ${d.x}`;

			return path;
		}

		// Toggle children on click.
		function click(d) {
			if (d.children) {
				// collapse if there are known children
				d._children = d.children;
				d.children = null;
				update(d);
			} else if (d._children) {
				// expand if there are known children
				d.children = d._children;
				d._children = null;
				update(d);
			} else if (d.noChildren) {
				// do nothing on the tree diagram if it's known that there are no children
				// maybe redirect to a another page or print out the expression or something
				return;
			} else {
				// send new query to see if there are children
				if (d.depth === 1) {
					Browsing.query({query: d.data.name}).$promise.then(function(data) {
						var queryResult = {
							data: data,
							expression: d.data.name
						};
						addQueryDataToTree(d, queryResult, true);
					}, function (error) {
						growl.error(error.data.message);
						console.log(error);
					});
				} else {
					queryUntilMultipleOrNoReturns(Browsing, d.data.name + '.', true).then(function(queryResult) {
						addQueryDataToTree(d, queryResult, false);
					}, function (error) {
						growl.error(error.data.message);
						console.log(error);
					});
				}
			}
		}

		function addQueryDataToTree(d, queryResult, depth1) {
			var data = queryResult.data;
			var expression = queryResult.expression;
			// console.log(expression);
			if (data.length > 0) {
				var initialLevels = d.depth === 1;
				if (d.depth + 1 > treeHeight) {
					treeHeight += 1;
					updateHeight(root);
				}
				if (depth1 || queryResult.endOfQuery) {
					d.data.children = createHierarchicalData(data);
				} else {
					d.data.children = createHierarchicalDataWithExpression(data, expression);
				}
				var additionalTree = d3.hierarchy(d.data, function(d) { return d.children; });
				// append new tree to the existing one
				d.children = additionalTree.children;
				additionalTree.children.forEach(function(child) {
					child.depth += d.depth;
					child.parent = d;
				});
				d.children.forEach(function(child){
					if (queryResult.endOfQuery) child.noChildren = true;
					// create a short name to display
					if (depth1) {
						child.displayName = child.data.name;
					} else {
						// only show the new params
						child.displayName = child.data.name.replace(d.data.name, '');
					}
				});
			} else {
				// there is the end of the expression: no query results
				d.noChildren = true;
			}
			update(d);
		}

		function updateHeight(d) {
			d.height++;
			if (d.children) d.children.forEach(updateHeight);
		}
	}

	// TODO: search text box, including hint box for auto complete
	$scope.$watch('expression', function (newValue, oldValue) {
		if (newValue === oldValue) return;
		Browsing.query({query: newValue}).$promise.then(function (data) {
			console.log(data);
		}, function (error) {
			growl.error(error.data.message);
			console.log(error);
		});
	});

	// TODO: go to a particular node based on the input box
	$scope.goToNode = function () {
		// need to have a dot at the end
		if ($scope.expression[$scope.expression.length - 1] !== '.') {
			growl.error('Incomplete parameter in the expression');
			return;
		}
		var firstLetter = $scope.expression[0];
		var startingNode = root.children.filter(function(d) {
			return d.displayName === firstLetter;
		});
		// first letter must be one of the given ones
		if (startingNode.length === 0) {
			growl.error('Invalid parameter in the expression');
			return;
		}
		startingNode = startingNode[0];
		// TODO: need to run query for all the params and add it to the startingNode
		// Browsing.query({query: $scope.expression}).$promise.then(function(data) {
		//     update(root);
		// }, function (error) {
		//     growl.error(error.data.message);
		//     console.log(error);
		// });
	};
}]);
