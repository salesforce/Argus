/**
 * Created by pfu on 8/24/16.
 */
angular.module('argus.directives')
.directive('totalNumber', function(){
   return{
       restrict: 'E',
       replace: true,
       transclude: true,
       scope: {
           total: '=',
           currentPage: '=',
           itemsPerPage: '=',
           type : '@'
       },
       controller: ['$scope', function ($scope) {
           //update the start, end and total of the pagination info
           $scope.update = function(){
               var start = ($scope.currentPage - 1)* $scope.itemsPerPage + 1;
               $scope.start = start < $scope.total ? start : $scope.total;
               var end = $scope.start + $scope.itemsPerPage - 1;
               $scope.end = end < $scope.total ? end : $scope.total;
           };
           //watch the related variables
           $scope.$watch('total', function () {
               $scope.update();
           });
           $scope.$watch('currentPage', function () {
               $scope.update();
           });
           $scope.$watch('itemsPerPage', function () {
               $scope.update();
           });
       }],
       template: '<span ng-transclude>Showing {{start}}-{{end}} of all {{total}} {{type}}</span>',
   }
});