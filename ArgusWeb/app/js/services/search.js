angular.module('argus.services.search', [])
.service('SearchService', ['$q', '$http', 'CONFIG', function($q, $http, CONFIG) {
    this.search = function(searchParams) {
        if (!searchParams) return;
        
        // TODO: refactor api call to a separate factory for metric queries
        var request = $http({
            method: 'GET',
            url: CONFIG.wsUrl + 'discover/metrics/schemarecords',
            params: searchParams,
            timeout: 30000
        });

        return request;
    };

    this.processResponse = function(response) {
        return response.data;
    };

    /*
    this.processResponses = function(responses) {
        return responses
            .filter(function(res){  //filters successful requests
                return (res.state === 'fulfilled');
            })
            .map(function(res) {    //maps each response
                var resCategory = res.value.config.headers.category;
                res.value.data.map(function(datum) {
                    //adds the category (namespace, scope, metric, etc.)
                    //and expression value to each search result
                    datum['category'] = resCategory;
                    datum.expression = buildExpression(datum);
                });
                res.value.data = res.value.data.filter(function(datum, index, arr) {
                    //filters results with unique expressions
                    var thisExpression = datum.expression;
                    index++;
                    while (index < arr.length) {
                        var otherExpresssion = arr[index].expression;
                        if (thisExpression === otherExpresssion) {
                            return false;   //expression match found
                        }
                        index++;
                    }
                    return true;    //expression match not found
                });
                return res.value.data;  //leaves only the data obj
            })
            .reduce(function(a, b) {
                //flattens the 2D list of responses
                return a.concat(b);
            });
    };

    //Returns a complete expression for a search result depending on
    //which category it belongs to (namespace, scope, metric, etc.)
    var buildExpression = function(searchResult) {
        if (searchResult != null) {
            var categories = ['namespace', 'scope', 'metric', 'tagk', 'tagv'];
            var searchCategory = searchResult.category;
            var expressionArray = [];
            
            for (var i = 0; i < categories.length; i++) {
                var expressionComponent = searchResult[categories[i]];
                expressionArray.push(expressionComponent);
                if (categories[i] === searchCategory) {
                    break;  //Reached the end of expression
                }
            }
            
            var expressionString = expressionArray.join(":");
            return expressionString;
        }
    };
    */
}]);
