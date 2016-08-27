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
           numberOf: '=numberOf'
       },
       controller: ['$scope', function ($scope) {
          $scope.getTotalNumber = function(){
              if($scope.numberOf){
                  return $scope.numberOf.length;
              }else{
                  return 0;
              }
          }
       }],
       template: '<span ng-transclude>Total: {{getTotalNumber()}}</span>',
   }
});