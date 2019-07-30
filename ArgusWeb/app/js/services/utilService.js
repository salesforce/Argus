/*global angular:false, copyProperties:false */
'use strict';

angular.module('argus.services.utils', [])
.service('UtilService', ['$filter', function($filter) {
	var options = {
		assignController: function(controllers) {
			if (!controllers) return;
			for (var i=0; i < controllers.length; i++) {
				if (controllers[i])
					return controllers[i];
			}
		},

		copyProperties: function(from, to) {
			for (var key in from) {
				if (from.hasOwnProperty(key)) {
					//if from[key] is not an object and is last property then just copy so that it will overwrite the existing value
					if (!to[key] || typeof from[key] == 'string' || from[key] instanceof String ) {
						to[key] = from[key];
					} else {
						copyProperties(from[key],to[key]);
					}
				}
			}
		},

		constructObjectTree: function(name, value) {
			var result = {};
			var index = name.indexOf('.');

			if (index == -1) {
				result[name] = this.getParsedValue(value);
				return result;
			} else {
				var property = name.substring(0, index);
				result[property] = this.constructObjectTree(name.substring(index + 1), value);
				return result;
			}
		},

		getParsedValue: function(value) {
			if (value instanceof Object || value.length === 0) {
				return value;
			}
			if (value == 'true') {
				return true;
			} else if (value == 'false') {
				return false;
			} else if (!isNaN(value)) {
				return parseInt(value);
			}
			return value;
		},

		cssNotationCharactersConverter: function (name) {
			return name.replace( /(:|\.|\[|\]|,|=|@)/g, '\\$1' );
		},

		trimMetricName: function (metricName, menuOption) {
			if (!metricName) return;

			return $filter('truncateMetricName')(metricName, menuOption);
		},

		validNumberChecker: function (num) {
			return isFinite(num)? num: 0;
		},

		capitalizeString: function (string) {
			return string.charAt(0).toUpperCase() + string.slice(1);
		},

		epochTimeMillisecondConverter: function (timestampNum) {
			// sometimes epoch time is in second instead of milisecond
			// http://stackoverflow.com/questions/23929145/how-to-test-if-a-given-time-stamp-is-in-seconds-or-milliseconds
			if (timestampNum.toString().length < 12) {
				return timestampNum * 1000;
			} else {
				return timestampNum;
			}
		},

		alphabeticalSort: function(a, b) {
			var textA = a.name.toUpperCase();
			var textB = b.name.toUpperCase();
			return (textA < textB) ? -1 : (textA > textB) ? 1 : 0;
		},

		objectWithoutProperties: function (obj, keys) {
			var target = {};
			for (var i in obj) {
				if (keys.indexOf(i) >= 0) continue;
				if (!Object.prototype.hasOwnProperty.call(obj, i)) continue;
				target[i] = obj[i];
			}
			return target;
		},

		ExpressionNode: class {
			constructor(type, text){
				this.type = type
				this.text = text
				this.children = []
			}
			appendChild(node){
				this.children.push(node)
			}
		},

		getExpressionTree: function(expression){
			expression = expression.trim()
			const n = expression.length
			const stack = []
			let curT = undefined //current transform
			let tmpText = ''
			let tmpType = 'expression'
			
			for(let i = 0; i < n; i ++ ) {
				const c = expression[i]
				if(c.match(/\s/)) continue
				let node
				switch (c) {
					case '(':
						if (curT) {
							stack.push(curT)
						}
						curT = new this.ExpressionNode('transform', tmpText)
						tmpText = ''
						tmpType = 'expression'
						continue
					case ')':
						if(tmpText !== ''){
							node = new this.ExpressionNode(tmpType, tmpText)
							curT.appendChild(node)
						}
						if (stack.length === 0){
							//end of outter most expression
							return curT
						}
						const lastT = stack.pop()
						lastT.appendChild(curT) //add just ended transform to parent
						curT = lastT
						tmpText = ''
						tmpType = 'expression'
						continue
					case ',':
						if (tmpText === '') continue // xxx),xxx
						if (tmpType === 'tag') { //do not take comma as seperator
							tmpText += c
							continue
						}
						node = new this.ExpressionNode(tmpType, tmpText)
						curT.appendChild(node)
						tmpText = ''
						tmpType = 'expression'
						continue
					case '{':
						tmpText += c
						tmpType = 'tag' //TODO: add tag children for expression
						continue
					case '}':
						tmpText += c
						tmpType = 'expression'
						continue
					case '#':
						tmpText += c
						tmpType = 'constant'
						continue
					default:
						tmpText += c
				}
			}
			if (tmpText !== '') {
				//just a normal expression without transform
				return new this.ExpressionNode(tmpType, tmpText)
			}
			return curT // if there is a tranform, root should be returned in the loop
		},

		printTree: function(depth, isFirstChild, stringArr, previousNode, node) {
			const indentation = ' '.repeat(depth * 2)
			if (previousNode && previousNode.type === 'transform'){
				stringArr.push(`\n${indentation}`)
			}
			if (isFirstChild){
				stringArr.push(indentation) //indentation
			}
			if (node.type === 'transform'){
				stringArr.push(`${node.text}(\n`)
				let isFirstChild = true
				let previousChild
				for(let child of node.children){
					if (!isFirstChild) {
						stringArr.push(',')
					}
					this.printTree(depth + 1, isFirstChild,  stringArr, previousChild, child) 
					previousChild = child
					if (isFirstChild) isFirstChild = false
				}
				stringArr.push(`\n${indentation})`)
			} else {
				stringArr.push(node.text)
			}
		},

		printTreeFlat: function(stringArr, node) {
			if (node.type === 'transform'){
				stringArr.push(`${node.text}(`)
				let isFirstChild = true
				for(let child of node.children){
					if (!isFirstChild) {
						stringArr.push(',')
					}
					this.printTreeFlat(stringArr,child) 
					if (isFirstChild) isFirstChild = false
				}
				stringArr.push(`)`)
			} else {
				stringArr.push(node.text)
			}
		},

		prettifyExpression: function(expression) {
			const tree = this.getExpressionTree(expression)
			const stringArr = []
			this.printTree(0, true, stringArr, undefined, tree)
			return stringArr.join('')
		},

		flatTree: function(tree) {
			const stringArr = []
			this.printTreeFlat(stringArr, tree)
			return stringArr.join('')
		},

		typeOfNode: function(text) {
			const firstChar = text.trim()[0]
			if (firstChar === '#') return 'constant'
			if (/[A-Z]/.test(firstChar)) return 'transform'
			return 'expression'
		}
	};
	return options;
}]);
