/*! Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 *      Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 *      Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *
 *      Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */
'use strict';

angular.module('argus.controllers.dashboards', ['ngResource', 'ui.codemirror'])
.controller('Dashboards', ['Auth', '$scope', 'growl', 'Dashboards', '$sessionStorage', function (Auth, $scope, growl, Dashboards, $sessionStorage) {

    $scope.dashboards = [];
    $scope.dashboardsLoaded = false;
    var sharedDashboards = [];
    var usersDashboards = [];
    var remoteUser = Auth.remoteUser();

    // session storage for selectedTab
    if ($sessionStorage.selectedTab) {
        $scope.selectedTab = $sessionStorage.selectedTab;
        $scope.shared = $sessionStorage.shared;
    } else {
        $scope.selectedTab = 1;
        $scope.shared = false;
    }

    // used in html files only
    $scope.isTabSelected = function (tab) {
        return $scope.selectedTab === tab;
    };
    $scope.selectTab = function (tab) {
        $scope.selectedTab = tab;
        $sessionStorage.selectedTab = tab;
    };

    // TODO: refactor to DashboardService
    $scope.getDashboards = function(shared) {
        if ($scope.dashboardsLoaded) {
            $scope.dashboards = shared? sharedDashboards: usersDashboards;
        }
        $scope.shared = shared;
        $sessionStorage.shared = shared;
    };

    function getDashboardsUnderTab (allDashboards, shared) {
        var result = [];
        var totNum = allDashboards.length;
        if(shared) {
            for(var i = 0; i < totNum; i++) {
                if(allDashboards[i].shared) {
                    result.push(allDashboards[i]);
                }
            }
        } else {
            for(var i = 0; i < totNum; i++) {
                if (allDashboards[i].ownerName === remoteUser.userName) {
                    result.push(allDashboards[i]);
                }
            }
        }
        return result;
    }

    function setDashboardsAfterLoading(dashboards) {
      $scope.dashboardsLoaded = true;
      sharedDashboards = getDashboardsUnderTab(dashboards, true);
      usersDashboards = getDashboardsUnderTab(dashboards, false);
      $scope.getDashboards($scope.shared);
    }

    // TODO: refactor to DashboardService
    if ($sessionStorage.cachedDashboards) {
        var dashboards = $sessionStorage.cachedDashboards;
        setDashboardsAfterLoading(dashboards);
    } else {
        Dashboards.getMeta().$promise.then(function(dashboards) {
            setDashboardsAfterLoading(dashboards);
            $sessionStorage.cachedDashboards = dashboards;
        });
    }

    $scope.refreshDashboards = function () {
        delete $sessionStorage.cachedDashboards;
        delete $scope.dashboards;
        $scope.dashboardsLoaded = false;
        Dashboards.getMeta().$promise.then(function(dashboards) {
            setDashboardsAfterLoading(dashboards);
            $sessionStorage.cachedDashboards = dashboards;
        });
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
            // update all dashboards
            result.content = "";
            $sessionStorage.cachedDashboards.push(result);
            // update individual tab's dashboards if needed
            if(result.shared) {
                sharedDashboards.push(result);
            }
            if (result.ownerName === remoteUser.userName) {
                usersDashboards.push(result);
            }
            // update dashboards to be seen
            $scope.getDashboards($scope.shared);
            growl.success('Created "' + dashboard.name + '"');
        }, function (error) {
            growl.error('Failed to create "' + dashboard.name + '"');
        });
    };

    // TODO: refactor to DashboardService
    $scope.deleteDashboard = function (dashboard) {
        Dashboards.delete({dashboardId: dashboard.id}, function (result) {
            // update all dashboards
            $sessionStorage.cachedDashboards = deleteDashboardFromList($sessionStorage.cachedDashboards, dashboard);
            // update individual tab's dashboards if needed
            if(dashboard.shared) {
                sharedDashboards = deleteDashboardFromList(sharedDashboards, dashboard);
            }
            if (dashboard.ownerName === remoteUser.userName) {
               usersDashboards = deleteDashboardFromList(usersDashboards, dashboard);
            }
            // update dashboards to be seen
            $scope.getDashboards($scope.shared);
            growl.success('Deleted "' + dashboard.name + '"');
        }, function (error) {
            growl.error('Failed to delete "' + dashboard.name + '"');
        });
    };

    function deleteDashboardFromList(dashboardList, dashboardToDelete) {
        return dashboardList.filter(function (element) {
            return element.id != dashboardToDelete.id;
        });
    }

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
        template += "<ag-metric>$start$:$end$:$scope$:$metric${$tags$}:$aggregator$</ag-metric>\n";

        template += "</ag-chart>\n\n";
        template += "</ag-dashboard>";

        return template;
    };

    $scope.isDisabled = function(dashboard) {
        return !(remoteUser && (remoteUser.privileged || remoteUser.userName === dashboard.ownerName));
    };

    $scope.colName = {
        id:'ID',
        name:'Name',
        description:'Description',
        createdDate:'Created',
        modifiedDate:'Last Modified',
        ownerName:'Owner'
    };

    $scope.properties = {
        title: "Dashboard",
        type: "dashboards"
    };

}]);
