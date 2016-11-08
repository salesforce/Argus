angular.module('argus.directives.controls.submit', [])
.directive('agSubmit', ['$rootScope','$http', function($rootScope, $http) {
    return {
        restrict: 'E',
        require: '^agDashboard',
        template: '',
        link: function(scope, element, attributes, dashboardCtrl) {
            var buttonName = 'Submit';
            if (element.text() && element.text().length > 0) {
                buttonName = element.text();
            }
            element.html('<button class="btn btn-primary btn-md">' +  buttonName + '</button>');

            element.on('click', function() {
                $http.pendingRequests = []; //This line should be deleted.
                dashboardCtrl.broadcastEvent(dashboardCtrl.getSubmitBtnEventName(), dashboardCtrl.getAllControls());
            });
        }
    }
}]);