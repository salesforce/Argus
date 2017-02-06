//run: 'npm install webpack -g --save-dev' to install webpack globally
//run: 'npm run bundle' to generate the bundle file
//run: 'webpack' to build a bundle of all js files
//(production) run: 'PROD_ENV=1 webpack' to build mininfied file ready for deployment to production

var webpack = require('webpack');
console.log("dirname:" + __dirname);
var PROD = JSON.parse(process.env.PROD_ENV || 0);

module.exports = {
	context: __dirname + '/app',
	entry: {
		app: './js/argus.js',
	},
	output: {
		path: __dirname + '/app/js',
		filename: PROD ? 'app.bundle.min.js' : 'app.bundle.js'
	},
	plugins: PROD ? [
		new webpack.optimize.UglifyJsPlugin({
			compress: {
				warnings: false
			},
			output: {
				comments: false,
			},
		})
	] : []
};
