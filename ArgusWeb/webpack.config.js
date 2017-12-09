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
	},
	output: {
		path: __dirname + '/dist',
		filename: '[name].[chunkhash].js',
		chunkFilename: "[name].[chunkhash].js"
	},
	module: {
		rules: [
			{
				test: /\.js$/,
				exclude: /node_modules/,
				use: {
					loader: 'babel-loader',
					options: {
						presets: ['es2015']
					}
				}
			},
			{
				test: /\.css$/,
				use: ExtractTextPlugin.extract({
					fallback: "style-loader",
					use: "css-loader"
				})
			},
			{
				test: /\.html$/,
				use: {
					loader: 'html-loader',
					options: {
						attrs: [':data-src']
					}
				}
			},
			{
				test: /\.(jpg|png|gif)$/,
				use: {
					loader: 'file-loader',
					options: {
						name: '[path][name].[ext]'
					}
				}
			}
		]
	},
	devtool: "source-map",
	plugins: [
		// copy over static files and vendor files
		new CopyWebpackPlugin([
			{from: 'node_modules', to:'node_modules'}
		]),
		// copy over base html
		new HtmlWebpackPlugin({
			template: __dirname + '/webpack_index.html',
			filename: 'index.html',
			inject: 'body',
			favicon: __dirname + '/app/img/argus_icon.png',
			cache: true,
			hash: true
		}),
		// cache hash management
		new webpack.HashedModuleIdsPlugin(),
		new webpack.optimize.CommonsChunkPlugin({
			name: "manifest",
			minChunks: Infinity
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
			filename: 'main.[contenthash].css',
			allChunks: true
		}),
		// remove any existing previous build
		new CleanWebpackPlugin('dist'),
		// css
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
