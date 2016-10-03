angular.module('argus.services.jobexecutiondetails', [])
.factory('JobExecutionDetails', ['$resource', 'CONFIG', function ($resource, CONFIG) {
    return $resource(CONFIG.wsUrl + 'history/job/:id', {id: '@id', limit: '20'}, {});
}]);