var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');
var ChunkManifestPlugin = require('chunk-manifest-webpack-plugin');
var WebpackChunkHash = require('webpack-chunk-hash');

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
    plugins: [
        // TODO: need to make bower_components into vendor.js
        new CopyWebpackPlugin([
            {from:'bower_components', to:'bower_components'},
            {from:'css', to:'css'},
            {from:'img', to:'img'},
            {from:'js/templates', to:'js/templates'}
        ]),
        // use copy base html
        new HtmlWebpackPlugin({
            template: __dirname + '/webpack_index.html',
            filename: 'index.html',
            inject: 'body'
        }),
        // cache hash management
        new webpack.optimize.CommonsChunkPlugin({
            name: [/*"vendor", */"manifest"], // vendor libs + extracted manifest
            minChunks: Infinity
        }),
        new webpack.HashedModuleIdsPlugin(),
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
        })
    ]
};