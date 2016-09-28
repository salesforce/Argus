angular.module('argus.controllers.dashboards.detail', ['ngResource', 'ui.codemirror'])
.controller('DashboardsDetail', ['Storage', '$scope','$http', '$routeParams', '$location', 'growl', 'Dashboards', 'History',
    function (Storage, $scope,$http, $routeParams, $location, growl, Dashboards, History) {
        $http.pendingRequests = []; //This line should be deleted.
		$scope.dashboardEditable = false;
        
        $scope.isDashboardDirty = function () {
            return !angular.equals($scope.dashboard, $scope.unmodifiedDashboard);
        };

        $scope.updateDashboard = function () {
            if ($scope.isDashboardDirty()) {
                var dashboard = $scope.dashboard;
                Dashboards.update({dashboardId: dashboard.id}, dashboard, function (result) {
                    $scope.unmodifiedDashboard = angular.copy(dashboard);
                    growl.success(('Updated "') + dashboard.name + '"');
                    $scope.fetchHistory();
                }, function (error) {
                    growl.error('Failed to update "' + dashboard.name + '"');
                });
            }
        };

        $scope.resetDashboard = function () {
            $scope.dashboard = angular.copy($scope.unmodifiedDashboard);
        };

        $scope.editorLoaded = function (editor) {
            editor.setSize(null, '30em');
        };

        $scope.isTabSelected = function (tab) {
            return $scope.selectedTab === tab;
        };

        $scope.selectTab = function (tab) {
            $scope.selectedTab = tab;
        };

        $scope.fetchHistory = function() {
            History.query({id: $scope.dashboardId}, function (history) {
                $scope.jobHistoryError='';
                $scope.history = history;
            }, function (error) {
                if(error.status==404){
                    $scope.jobHistoryError = 'No job history details found.';
                }else if(error.status==403){
                    $scope.jobHistoryError = error.statusText;
                    $scope.jobHistoryError = 'You are not authorized to view this data.';
                }
                else {
                    $scope.jobHistoryError = error.statusText;
                    growl.error('Failed to get history for job "' + $scope.jobId + '"');
                }
            });
        };

        $scope.dashboardId = $routeParams.dashboardId;
        $scope.selectedTab = 1;
        
        $scope.editorOptions = {
            lineWrapping: true,
            lineNumbers: true,
            mode: "htmlmixed",
            viewportMargin: 500
        };

        if ($scope.dashboardId > 0) {
            Dashboards.get({dashboardId: $scope.dashboardId}, function (dashboard) {
                $scope.dashboard = dashboard;
                $scope.dashboardEditable = isDashboardEditable();
                $scope.unmodifiedDashboard = angular.copy(dashboard);
            }, function (error) {
                growl.error('Failed to get dashboard "' + $scope.dashboardId + '"');
                $location.path('/dashboards');
            });
            $scope.fetchHistory();
        } else {
            growl.error('Failed to get dashboard "' + $routeParams.dashboardId + '"');
            $location.path('dashboards');
        }

        $scope.resetDashboard();
        
        function isDashboardEditable() {
        	var remoteUser = Storage.get('user');
        	if(remoteUser.privileged || remoteUser.userName === $scope.dashboard.ownerName)
        		return true;
        	return false;
        }
    }]);
