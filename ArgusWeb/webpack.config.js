//run: 'npm install webpack --save-dev' to install webpack
//run: 'npm run bundle' to generate the bundle file
var webpack = require('webpack');
console.log("dirname:" + __dirname);
module.exports = {
    context: __dirname + '/app',
    entry: {
      app: './js/argus.js',
    },
output: {
    path: __dirname + '/app/js',
    filename: 'app.bundle.js'
},
};
