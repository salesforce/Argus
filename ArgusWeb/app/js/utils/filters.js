angular.module('argus.filters', [])
.filter('capitalize', function() {
		return function(input, scope) {
				if (input != null) {
						input = input.toLowerCase();
						return input.substring(0,1).toUpperCase() + input.substring(1);
				}
		}
})

.filter('urlencode', function () {
		return window.encodeURIComponent;
})

.filter('newline', function() {
    return function(data) {
        if (data && data.length > 0) {
            var retvalue = data.replace(/</g, '&lt');
            retvalue = retvalue.replace(/>/g, '&gt');
            retvalue = retvalue.replace(/\n/g, '<br/>');
            return retvalue;
        } else return "";
    }
})

.filter('trustedhtml', ['$sce', function ($sce) {
		return function (text) {
				return $sce.trustAsHtml(text);
		};
}]);