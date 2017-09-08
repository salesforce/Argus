'use strict';
/*global console:false*/
function removeRelativePath (relativePath) {
	return 'js/' + relativePath.substring(relativePath.indexOf('templates'), relativePath.length);
}

export default function transformer(file, api) {
	const j = api.jscodeshift;
	const currentPath = file.path;
	const root = j(file.source);
	const consoleLogCalls = root.find(j.Property, {
		key: {
			name: "template"
		},
		value: {
			type: "CallExpression",
			callee: {
				name: "require"
			}
		}
	});

	consoleLogCalls.forEach(p => {
		p.node.key.name = "templateUrl";
		p.node.value = {
			type: "Literal",
			value: removeRelativePath(p.node.value.arguments[0].value)
		};
	});

	const outputOptions = {
		quote: 'single'
	};
	return root.toSource(outputOptions);
}
