var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var ChunkManifestPlugin = require('chunk-manifest-webpack-plugin');
var WebpackChunkHash = require('webpack-chunk-hash');
var ExtractTextPlugin = require('extract-text-webpack-plugin');
var CleanWebpackPlugin = require('clean-webpack-plugin');

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
            }
        ]
    },
    plugins: [
        // TODO: need to make bower_components into vendor.js
        new CopyWebpackPlugin([
            {from:'bower_components', to:'bower_components'},
            {from: 'img/argus_icon.png', to: 'img/argus_icon.png'},
            {from: 'img/argus_logo_rgb.png', to: 'img/argus_logo_rgb.png'},
            {from: 'js/templates', to: 'js/templates'}
        ]),
        // use copy base html
        new HtmlWebpackPlugin({
            template: __dirname + '/webpack_index.html',
            filename: 'index.html',
            inject: 'body'
            // hash: true
        }),
        // cache hash management
        new webpack.HashedModuleIdsPlugin(),
        new webpack.optimize.CommonsChunkPlugin({
            // name: ["vendor", "manifest"], // vendor libs + extracted manifest
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
            mangle: false
        }),
        new ExtractTextPlugin({
            filename: 'main.[contenthash].css',
            allChunks: true
        }),
        new CleanWebpackPlugin('dist')
    ]
};