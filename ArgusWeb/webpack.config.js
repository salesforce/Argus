var HtmlWebpackPlugin = require('html-webpack-plugin');

var webpack = require('webpack');
var path = require('path');

module.exports = {
    context: __dirname + '/app',
    entry: {
        app: './js/argus.js',
        // not sure if the vendor stuff (node_modules) is useful for the UI
        /*'bower', 'grunt', 'grunt-replace', 'http-server', 'karma', 'karma-chrome-launcher', 'karma-firefox-launcher', 'karma-jasmine', 'karma-junit-reporter', 'protractor', 'shelljs',*/
        // the following are the bower stuff
        // vendor: ['angular', 'angular-mocks', 'jquery', 'bootstrap', 'components-font-awesome', 'angular-route', 'angular-growl-v2', 'angular-animate', 'angular-resource', 'angular-utils-pagination', 'highstock-release', 'codemirror', 'angular-ui-codemirror', 'ngstorage', 'at-table', 'angulartics', 'angular-bootstrap', 'angular-bootstrap-datetimepicker', 'angular-selectize2', 'q', 'd3', 'd3-tip']
    },
    output: {
        path: __dirname + '/dist',
        filename: 'argus.bundle.js'
    },
    // module: {
    //     rules: [
    //         {
    //             test: /\.css$/,
    //             use: ['style-loader', 'css-loader']
    //         },
    //         {
    //             test: /\.(jpe?g|png|gif)$/i,
    //             loader: "file-loader?name=/img/[name].[ext]"
    //         }
    //         {
    //             test: /\.(jpe|jpg|woff|woff2|eot|ttf|svg)(\?.*$|$)/,
    //             loader: "file-loader"
    //         }
    //     loaders: [
    //         {
    //             test: /\.(jpg|png)$/,
    //             loader: 'file' // or 'url'
    //         },
    //         {
    //             test: /\.html$/,
    //             loader: 'html?root=.'
    //         },
    //         {
    //             test: /\.css$/,
    //             loader: 'css'
    //         }
    //     ]
    // },
    // resolve: {
    //     modules: [path.resolve(__dirname, "app"), "bower_components"],
    //     descriptionFiles: ["package.json", "bower.json"],
    // },
    plugins: [
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
