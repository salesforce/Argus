'use strict';
/*global require:false, __dirname */

// fix EMFILE: too many open files" or "ENFILE: file table overflow issue
var fs = require('fs');
var gracefulFs = require('graceful-fs');
gracefulFs.gracefulify(fs);

var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var ChunkManifestPlugin = require('chunk-manifest-webpack-plugin');
var WebpackChunkHash = require('webpack-chunk-hash');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var CleanWebpackPlugin = require('clean-webpack-plugin');
var OptimizeCssAssetsPlugin = require('optimize-css-assets-webpack-plugin');

var webpack = require('webpack');
var path = require('path');

module.exports = {
	context: __dirname + '/app',
	entry: {
		argus: './js/argus.js'
		// vendor: ["codemirror","angular","angular-mocks","jquery","bootstrap","angular-route","angular-growl-v2","angular-animate","angular-resource","angular-utils-pagination","angular-ui-codemirror","ngstorage","angulartics","angular-bootstrap","angular-bootstrap-datetimepicker","q","d3","d3-tip","d3fc-rebind","d3fc-sample"]
		// // angular-table not working as require
	},
	output: {
		path: __dirname + '/dist',
		filename: '[name].[chunkhash].js',
		chunkFilename: "[name].[chunkhash].js"
	},
	module: {
		rules: [
			{
				test: /\.css$/,
				use: ExtractTextPlugin.extract({
					fallback: "style-loader",
					use: "css-loader"
				})
			},
			{
				test: /\.(gif|png|jpg)$/,
				loader: 'file-loader',
				options: {
					name: '[path][name].[ext]',
				}
			},
			// for react
			// {
			// 	test: /\.js$/, exclude: /node_modules/, loader: "babel-loader"
			// },
			// {
			// 	test: /\.(eot|woff|woff2|ttf|svg)$/,
			// 	loader: 'url-loader?limit=30000&name=[name]-[hash].[ext]'
			{
				test: /\.js$/,
				exclude: /node_modules/,
				loader: "babel-loader",
				query: {
					presets: ['es2015']
				}
			}
		]
	},
	devtool: "source-map",
	plugins: [
		// copy over static files and vendor
		new CopyWebpackPlugin([
			{from: 'node_modules', to:'node_modules'},
			// {from: 'node_modules/angular-utils-pagination/dirPagination.tpl.html', to: 'node_modules/angular-utils-pagination/dirPagination.tpl.html'},
			{from: 'img/argus_icon.png', to: 'img/argus_icon.png'},
			{from: 'img/argus_logo_rgb.png', to: 'img/argus_logo_rgb.png'},
			{from: 'js/templates', to: 'js/templates'}
		]),
		// copy over base html
		new HtmlWebpackPlugin({
			template: __dirname + '/webpack_index.html',
			filename: 'index.html',
			inject: 'body'
			// hash: true
		}),
		// cache hash management
		new webpack.HashedModuleIdsPlugin(),
		new webpack.optimize.CommonsChunkPlugin({
			name: ["vendor", "manifest"], // vendor libs + extracted manifest
			// name: "manifest",
			// minChunks: Infinity
			minChunks: function (module) {
			   // this assumes your vendor imports exist in the node_modules directory
			   return module.context && module.context.indexOf('node_modules') !== -1;
			}
		}),
		new WebpackChunkHash(),
		new ChunkManifestPlugin({
			filename: "chunk-manifest.json",
			manifestVariable: "webpackManifest"
		}),
		// minifier
		new webpack.optimize.UglifyJsPlugin({
			compress: {
				warnings: false
			},
			output: {
				comments: false
			},
			mangle: false,
			sourceMap: true
		}),
		// copy over css
		new ExtractTextPlugin({
			filename: '[name].[contenthash].css',
			allChunks: true
		}),
		new CleanWebpackPlugin('dist'),
		// // handle jquery naming
		// new webpack.ProvidePlugin({
		// 	jQuery: 'jquery',
		// 	$: 'jquery',
		// 	jquery: 'jquery'
		new OptimizeCssAssetsPlugin({
			// only minify main.css
			assetNameRegExp: /^main.*.css$/g,
			cssProcessor: require('cssnano'),
			cssProcessorOptions: { discardComments: true },
			canPrint: false,
			safe: true
		})
	]
};
