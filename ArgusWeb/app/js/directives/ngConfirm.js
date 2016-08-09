angular.module('argus.directives.confirm', [])
.directive('ngConfirm', [function () {
    return {
        restrict: 'A',
        link: function (scope, element, attrs) {
            element.bind('click', function () {
                var message = attrs.ngConfirm;
                if (message && confirm(message)) {
                    scope.$apply(attrs.ngConfirmAction);
                }
            });
        }
    };
}]);