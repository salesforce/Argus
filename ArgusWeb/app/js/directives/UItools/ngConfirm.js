angular.module('argus.directives.confirm', [])
.directive('ngConfirm', [function () {
    //todo delete this file if it can be totally replaced by the confirm modal
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