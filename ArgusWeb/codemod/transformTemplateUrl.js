'use strict';
/*global console:false*/
function getRelativePath (currentPath, targetFile) {
	const previousDir = '../';
	var result = targetFile.substring(3); // all targetFile should have the format 'js/template/*/.html'
	var levelsUp = currentPath.substring(currentPath.indexOf('js/') + 3).split("/").length - 1;
	if (levelsUp === 0) {
		result = './' + result;
	} else {
		while (levelsUp > 0) {
			result = previousDir + result;
			levelsUp--;
		}
	}
	return result;
}

export default function transformer(file, api) {
	const j = api.jscodeshift;
	const currentPath = file.path;
	const root = j(file.source);
	const consoleLogCalls = root.find(j.Property, {
		key: {
			name: "templateUrl"
		}
	});

	consoleLogCalls.forEach(p => {
		p.node.key.name = "template";
		p.node.value = {
			type: "CallExpression",
			callee: {
				type: "Identifier",
				name: "require"
			},
			arguments: [
				{
					type: "Literal",
					value: getRelativePath(currentPath, p.node.value.value)
				}
			]
		};
	});

	const outputOptions = {
		quote: 'single'
	};
	return root.toSource(outputOptions);
}
