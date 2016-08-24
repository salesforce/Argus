angular.module('argus.controllers.dashboards', ['ngResource', 'ui.codemirror'])
.controller('Dashboards', ['Auth', 'Storage', '$scope', 'growl', 'Dashboards', function (Auth, Storage, $scope, growl, Dashboards) {
		
	$scope.searchText = Storage.get("dashboards-searchText") == null ? "" : Storage.get("dashboards-searchText");
    
    // TODO: refactor to DashboardService
    Dashboards.query().$promise.then(function(dashboards) {
        $scope.allDashboards = dashboards;
        $scope.getDashboards(false);
    });
    
    $scope.dashboards = [];
    $scope.shared = false;
    
    $scope.itemsPerPageOptions = [5, 10, 15, 25, 50, 100, 200];
    $scope.itemsPerPage = Storage.get("dashboards-itemsPerPage") == null ? $scope.itemsPerPageOptions[1] : Storage.get("dashboards-itemsPerPage");

    $scope.selectedTab = 1;
    
    $scope.isTabSelected = function (tab) {
        return $scope.selectedTab === tab;
    };

    $scope.selectTab = function (tab) {
        $scope.selectedTab = tab;
    };

    // TODO: refactor to DashboardService
    $scope.getDashboards = function(shared) {
    	$scope.dashboards = [];
    	if(shared) {
    		$scope.shared = true;
    		for(var i in $scope.allDashboards) {
    			if($scope.allDashboards[i].shared) {
    				$scope.dashboards.push($scope.allDashboards[i]);
    			}
    		}
    	} else {
    		$scope.shared = false;
    		for(var i in $scope.allDashboards) {
    			if(!$scope.allDashboards[i].shared) {
    				$scope.dashboards.push($scope.allDashboards[i]);
    			}
    		}
    	}
    };
    
    // TODO: refactor to DashboardService
    $scope.addDashboard = function () {
        var dashboard = {
            name: 'new-dashboard-' + Date.now(),
            description: 'A new dashboard',
            shared: $scope.shared,
            content: $scope.getContentTemplate()
        };
        Dashboards.save(dashboard, function (result) {
            $scope.dashboards.push(result);
            $scope.allDashboards.push(result);
            growl.success('Created "' + dashboard.name + '"');
        }, function (error) {
            growl.error('Failed to create "' + dashboard.name + '"');
        });
    };

    // TODO: refactor to DashboardService
    $scope.deleteDashboard = function (dashboard) {
        Dashboards.delete({dashboardId: dashboard.id}, function (result) {
            $scope.dashboards = $scope.dashboards.filter(function (element) {
                return element.id !== dashboard.id;
            });
            $scope.allDashboards = $scope.allDashboards.filter(function (element) {
                return element.id !== dashboard.id;
            });
            growl.success('Deleted "' + dashboard.name + '"');
        }, function (error) {
            growl.error('Failed to delete "' + dashboard.name + '"');
        });
    };

    // factor html template to /templates
    $scope.getContentTemplate = function () {
    	
    	var template = "<!-- This is the root level tag. All dashboards must be encapsulated within this tag. -->\n<ag-dashboard>\n\n";
        
    	template += "<!-- <ag-text> are filters used to refine a query. The values of these will be used by the <ag-metric> tag. You may define as many <ag-text> tags as the number of components you want to substitute in the argus query expression. A default value may be specified on each <ag-text> tag. The page will be loaded using these default values. -->\n";
    	template += "<ag-date type='datetime' name='start' label='Start Date' default='-2d'></ag-date>\n";
        template += "<ag-date type='datetime' name='end' label='End Date' default='-0d'></ag-date>\n";
        template += "<ag-text type='text' name='scope' label='Scope' default='argus.jvm'></ag-text>\n";
        template += "<ag-text type='text' name='metric' label='Metric' default='mem.heap.used'></ag-text>\n";
        template += "<ag-text type='text' name='tags' label='Tags' default='host=*'></ag-text>\n"; 
        template += "<ag-text type='text' name='aggregator' label='Aggregator' default='avg'></ag-text>\n";
        template += "<!-- A button used to submit the query. -->\n";
        template += "<ag-submit>Submit</ag-submit>\n\n";

        template += "<!-- A dashboard template can also have arbitrary number of html tags. -->\n";
        template += "<h4>Argus mem heap used - Chart</h4>\n\n";
        
        template += "<!-- This defines a chart on the dashboard. A dashboard can also have tables which are defined using <ag-table> tag. This/these tags encapsulate all the options for the corresponsing tag as well as the actual metric/annotation data. -->\n";
        template += "<ag-chart name='Chart'>\n\n";
        
        template += "<!-- This defines options for a chart or a table. The value of 'name' attribute is directly used as the key for the config object(options object for highcharts/highstocks, config object for at-table. Hence use valid values for name attribute.). The values for the corresponding keys can either be provided using the value attribute on the tag or using innerHtml for the tag. -->\n";
        template += "<ag-option name='title.text' value='This title was set with a chart option'></ag-option>\n";
        template += "<!-- This defines each timeseries to be displayed on a chart/table. The timeseries to be displayed is specified as the innerHtml using the Argus Query Language. The individual component/s can be parameterized by placing them between $ signs and using the value of ag-text tag's name attribute. In the example below, all components have are parameterized. -->\n";
        template += "<ag-metric name='Metric1'>$start$:$end$:$scope$:$metric${$tags$}:$aggregator$</ag-metric>\n";
        
        template += "</ag-chart>\n\n";
        template += "</ag-dashboard>";
        
        return template;
    };
    
    $scope.isDisabled = function(dashboard) {
      var remoteUser = Auth.remoteUser();
    	if (remoteUser && (remoteUser.privileged || remoteUser.userName === dashboard.ownerName)) {
            return false;
    	}
    	return true;
    };
    
    $scope.$watch('searchText', function(newValue, oldValue) {
    	newValue = newValue == null ? "" : newValue;
    	Storage.set("dashboards-searchText", newValue);
    });
    
    $scope.$watch('itemsPerPage', function(newValue, oldValue) {
    	newValue = newValue == null ? $scope.itemsPerPageOptions[1] : newValue;
    	Storage.set("dashboards-itemsPerPage", newValue);
    });

}]);
