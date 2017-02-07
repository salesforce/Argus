var HtmlWebpackPlugin = require('html-webpack-plugin');
var CopyWebpackPlugin = require('copy-webpack-plugin');

var webpack = require('webpack');
var path = require('path');

module.exports = {
    context: __dirname + '/app',
    entry: {
        app: './js/argus.js',
        // the following are the bower stuff
        // vendor: ['angular', 'angular-mocks', 'jquery', 'bootstrap', 'components-font-awesome', 'angular-route', 'angular-growl-v2', 'angular-animate', 'angular-resource', 'angular-utils-pagination', 'highstock-release', 'codemirror', 'angular-ui-codemirror', 'ngstorage', 'at-table', 'angulartics', 'angular-bootstrap', 'angular-bootstrap-datetimepicker', 'angular-selectize2', 'q', 'd3', 'd3-tip']
    },
    output: {
        path: __dirname + '/dist',
        filename: 'argus.bundle.js'
    },
    module: {
        // rules: [
        //     {
        //         test: /\.css$/,
        //         use: ['style-loader', 'css-loader']
        //     },
        //     {
        //         test: /\.(jpe?g|png|gif)$/i,
        //         loader: "file-loader?name=/img/[name].[ext]"
        //     }
        //     {
        //         test: /\.(jpe|jpg|woff|woff2|eot|ttf|svg)(\?.*$|$)/,
        //         loader: "file-loader"
        //     }
        rules: [
            {
                test: /\.jpe?g$|\.gif$|\.png$|\.svg$|\.woff$|\.ttf$|\.wav$|\.mp3$/,
                loader: 'file-loader'
            }
        ]
    },
    // resolve: {
    //     modules: [path.resolve(__dirname, "app"), "bower_components"],
    //     descriptionFiles: ["package.json", "bower.json"],
    // },
    plugins: [
        new CopyWebpackPlugin([
            {from:'bower_components', to:'bower_components'},
            {from:'css', to:'css'},
            {from:'img', to:'img'},
            {from:'js/templates', to:'js/templates'}
        ]),
        new HtmlWebpackPlugin({
            template: __dirname + '/app/index.html',
            filename: 'index.html',
            inject: 'body'
        })
        // new webpack.optimize.CommonsChunkPlugin({
        //     name: 'vendor',
        //     filename: 'vendor.js'
        // }),
        // new webpack.optimize.UglifyJsPlugin()
    ]
};
