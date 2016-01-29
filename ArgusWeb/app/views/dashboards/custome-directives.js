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
var custModule = angular.module('argusCustomeDirectives', []);

custModule.service('DashboardService', ['$resource', 'CONFIG', 'Metrics', 'Dashboards', '$sce', '$http', 'Annotations',
    function ($resource, CONFIG, Metrics, Dashboards, $sce, $http, Annotations) {

        this.getDashboardById = function (dashboardId) {
            return $http.get(CONFIG.wsUrl + 'dashboards/' + dashboardId);

        };

        this.populateView = function (metricExpressionList, annotationExpressionList, divId) {

            if (metricExpressionList && metricExpressionList.length > 0 && divId) {

                var chart = $('#' + divId).highcharts('StockChart');

                while (chart && chart.series.length > 0) {
                    chart.series[0].remove();
                }

                if (metricExpressionList && metricExpressionList.length > 0) {
                    Metrics.query({expression: metricExpressionList}, function (data) {
                        updateChart({}, data, divId, annotationExpressionList);
                    }, function (error) {
                        //TODO: pending
                    });

                } else {
                    //TODO: error message
                }
            }
        };



        function updateChart(config, data, divId, annotationExpressionList) {

            if (data && data.length > 0) {

                var chart = $('#' + divId).highcharts('StockChart');
                if (chart) {
                    while (chart.series.length > 0) {
                        chart.series[0].remove(true);
                    }
                    chart.colorCounter = 0;
                    chart.symbolCounter = 0;
                    var seriesArray = copySeries(data);
                    for (var series in seriesArray) {
                        chart.addSeries(seriesArray[series]);
                    }

                } else {
                    var options = config ? angular.copy(config) : {};
                    var series = copySeries(data);
                    options.credits = {enabled: false},
                    options.rangeSelector = {selected: 1, inputEnabled: false};
                    options.xAxis = {type: 'datetime'};
                    options.lang = {noData: 'No Data to Display'};
                    options.legend = {
                        enabled: true,
                        maxHeight: 62,
                        itemStyle: {
                            fontWeight: 'normal',
                            fontSize: '10px'
                        },
                        navigation : {
                            style : {
                                fontWeight: 'normal',
                                fontSize: '10px'
                            }
                        }
                    };
                    options.series = series;
                    options.plotOptions = {series: {animation: false}, line: {gapSize: 1.5}};
                    options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
                    $('#' + divId).highcharts('StockChart', options);
                    chart = $('#' + divId).highcharts('StockChart');
                }

                populateAnnotations(annotationExpressionList, chart);
            }

        }
        ;

        function copySeries(data) {
            var result = [];
            if (data) {
                for (var i = 0; i < data.length; i++) {
                    var series = $.map(data[i].datapoints, function (value, key) {
                        if(value != null)
                         return [[parseInt(key), parseFloat(value)]];
                        else return [[parseInt(key), value]];
                    });
                    result.push({name: createSeriesName(data[i]), data: series, marker: {enabled: true, radius: 1}});
                }
            } else {
                result.push({name: 'result', data: []});
            }
            return result;
        }
        ;

        function createSeriesName(metric) {
            var scope = metric.scope;
            var name = metric.metric;
            var tags = createTagString(metric.tags);
            return scope + ':' + name + tags;
        }
        ;

        function createTagString(tags) {
            var result = '';
            if (tags) {
                var tagString ='';
                for (var key in tags) {
                    if (tags.hasOwnProperty(key)) {
                        tagString += (key + '=' + tags[key] + ',');
                    }
                }
                if(tagString.length) {
                    result += '{';
                    result += tagString.substring(0, tagString.length - 1);
                    result += '}';
                }
            }
            return result;
        };

        function populateAnnotations(annotationsList, chart) {
            if (annotationsList && annotationsList.length > 0 && chart) {
                for (var i = 0; i < annotationsList.length; i++) {
                    addAlertFlag(annotationsList[i], chart);
                }
            }
        }
        ;

        function addAlertFlag(annotationExpression, chart) {
            Annotations.query({expression: annotationExpression}, function (data) {
                if (data && data.length > 0) {
                    var forName = createSeriesName(data[0]);
                    var series = copyFlagSeries(data);
                    //var chart = $('#' + divId).highcharts('StockChart');
                    series.linkedTo = forName;

                    for (var i = 0; i < chart.series.length; i++) {
                        if (chart.series[i].name === forName) {
                            series.color = chart.series[i].color;
                            break;
                        }
                    }

                    chart.addSeries(series);
                }
            });
        }
        ;

        function copyFlagSeries(data) {
            var result;
            if (data) {
                result = {type: 'flags', shape: 'circlepin', stackDistance: 20, width: 16, lineWidth: 2};
                result.data = [];
                for (var i = 0; i < data.length; i++) {
                    var flagData = data[i];
                    result.data.push({x: flagData.timestamp, title: 'A', text: formatFlagText(flagData.fields)});
                }
            } else {
                result = null;
            }
            return result;
        }
        ;

        function formatFlagText(fields) {
            var result = '';
            if (fields) {
                for (var field in fields) {
                    if (fields.hasOwnProperty(field)) {
                        result += (field + ': ' + fields[field] + '<br/>');
                    }
                }
            }
            return result;
        }
        ;


    }]);


custModule.directive('agDashboard', ['DashboardService', '$compile', '$sce', '$rootScope', function (DashboardService, $compile, $sce, $rootScope) {
        return{
            restrict: 'E',
            //transclude: true,
            //template:'<div ng-transclude></div>',
            controller: function ($scope) {
                $scope.controls = {};
                $scope.submitbuttonEventName = name + 'updateViews';
                var uniqueNumber = 1;
                this.getUniqueNumber = function () {
                    return uniqueNumber++;

                };
                this.getAllControls = function () {
                    var tempControls = $scope.controls;
                    var result = [];
                    for (var key in tempControls) {
                        if (tempControls.hasOwnProperty(key)) {
                            result.push(key);
                        }
                    }
                    return result;
                };

                this.updateControl = function (controlName, value) {
                    $scope.controls[controlName] = value;
                };

                this.getControlValue = function (controlName) {
                    return $scope.controls[controlName];
                };
                this.getSubmitBtnEventName = function () {
                    return $scope.submitbuttonEventName;
                };
            },
            link: function (scope, element, attribute) {
                DashboardService.getDashboardById(scope.dashboardId).success(function (data) {

                    element.html('<div> <h4>' + data.name + '</h4>' + $sce.trustAsHtml(data.content) + '</div>');
                    $compile(element.contents())(scope);

                    $rootScope.$emit(scope.submitbuttonEventName, {});
                });

            }
        };
    }]);



custModule.directive('agControls', function ($compile) {
    return{
        restrict: 'E',
        scope: {},
        transclude: true,
        replace: true,
        template: '<div ng-transclude> </div>',
        link: function (scope, element, attributes) {
            element.append('<ag-submit-button> </ag-submit-button> <br><br>');
            $compile(element.contents())(scope);
        }
    };

});



custModule.directive('agControl', function () {

    return{
        restrict: 'E',
        transclude: true,
        scope: {
            defaultvalue: '@default'
        },
        template: '<span></span>',
        require: '^agDashboard',
        compile: function (element, attributes, tranclude) {

            var controlNameWithPrefix = attributes.name;
            element.html('<label>' + attributes.label + '</label> &nbsp;');
            element.append('<input type=text ng-model="' + controlNameWithPrefix + '"/>');

            return function (scope, element, attributes, dashboardCtrl) {

                dashboardCtrl.updateControl(controlNameWithPrefix, scope.defaultvalue);
                scope[controlNameWithPrefix] = scope.defaultvalue;
                scope.$watch(controlNameWithPrefix, function (newValue) {
                    dashboardCtrl.updateControl(controlNameWithPrefix, newValue);
                });
            };
        }
    };

});

custModule.directive('agSubmitButton', function ($rootScope) {
    return {
        restrict: 'E',
        require: '^agDashboard',
        template: '<button> Submit</button> <br><br>',
        link: function (scope, element, attributes, dashBoardCtrl) {
            element.on('click', function () {
                var dataToPass = dashBoardCtrl.getAllControls();
                $rootScope.$emit(dashBoardCtrl.getSubmitBtnEventName(), {data: dataToPass});
                console.log('Submit button event emitted.');

            });
        }
    };

});

custModule.directive('agViews', function ($rootScope) {

    return{
        restrict: 'E',
        require: '^agDashboard',
        transclude: true,
        scope: {},
        controller: function ($scope) {
            $scope.viewList = [];
            this.addView = function (view) {
                $scope.viewList.push(view);
                console.log('View is added.');
            };
        },
        template: '<div ng-transclude=""> </div>',
        link: function (scope, element, attributes, dashBoardCtrl) {
            $rootScope.$on(dashBoardCtrl.getSubmitBtnEventName(), function (event, result) {
                console.log('Submit button event received.');
                for (var myView in scope.viewList) {
                    scope.$broadcast(scope.viewList[myView], {});
                    console.log('The event (for populating the view) broadcasted for the view: ' + scope.viewList[myView]);
                }
            });
        }
    };

});

custModule.directive('agView', ['DashboardService', function (DashboardService) {
        return{
            restrict: 'E',
            require: ['^agViews', '^agDashboard'],
            transclude: true,
            scope: {},
            template: '<div ng-transclude=""> </div>',
            controller: function ($scope) {
                $scope.chartList = [];

                this.addChart = function (name, type, metricList, annotationList) {
                    $scope.chartList.push({name: name, type: type, metricList: metricList, annotationList: annotationList});
                    //console.log('chart is added' );
                };

            },
            link: function (scope, element, attributes, controllers) {
                var viewsCtrl = controllers[0];
                var dashboardCtrl = controllers[1];
                var viewNameWithPrefix = 'view_' + attributes.name + '_' + dashboardCtrl.getUniqueNumber();
                viewsCtrl.addView(viewNameWithPrefix);
                var htmlContent = '';
                for (var chartIndex in scope.chartList) {
                    var chart = scope.chartList[chartIndex];
                    //Provide the unique names to charts (divId) so that the jquery will use them to populate the charts
                    chart.name = viewNameWithPrefix + '_chart_' + chart.name + '_' + chartIndex; //chartIndex will make the chart name unique in case of chart names are not provided in XML
                    htmlContent = htmlContent + '<div id="' + chart.name + '"> </div>';
                }

                htmlContent = htmlContent + ' <br/><br/>';
                element.prepend(htmlContent);

                scope.$on(viewNameWithPrefix, function () {

                    for (var chartIndex in scope.chartList) {
                        console.log(JSON.stringify(scope.chartList[chartIndex]));
                        var chart = scope.chartList[chartIndex];
                        var updatedMetricList = [];
                        var updatedAnnotationList = [];

                        for (var metricIndex in chart.metricList) {
                            updatedMetricList.push(replaceWithControlsData(chart.metricList[metricIndex].expression));
                        }

                        for (var annotationIndex in chart.annotationList) {
                            updatedAnnotationList.push(replaceWithControlsData(chart.annotationList[annotationIndex].expression));
                        }

                        DashboardService.populateView(updatedMetricList, updatedAnnotationList, chart.name);
                    }
                });

                function replaceWithControlsData(metricExpression) {

                    var result = metricExpression;
                    var controls = dashboardCtrl.getAllControls();
                    for (var control in controls) {
                        var controlValue = dashboardCtrl.getControlValue(controls[control]);
                        var controlName = '$' + controls[control] + '$';
                        result = result.replace(controlName, controlValue);
                    }
                    result = result.replace(/(\r\n|\n|\r|\s+)/gm, "");

                    return result;
                }
            }
        };

    }]);

custModule.directive('agChart', function () {


    return {
        restrict: 'E',
        require: '^agView',
        transclude: true,
        scope: {},
        controller: function ($scope) {
            $scope.metricList = [];
            $scope.annotationList = [];
            this.addMetric = function (name, expression) {
                $scope.metricList.push({name: name, expression: expression});
            };

            this.addAnnotation = function (name, expression) {
                $scope.annotationList.push({name: name, expression: expression});
            };
        },
        template: '<div ng-transclude=""> </div>',
        link: function (scope, element, attributes, viewCtrl) {
            var chartName = '';
            if (attributes.name) {
                chartName = attributes.name;
            }
            viewCtrl.addChart(chartName, attributes.type, scope.metricList, scope.annotationList);
        }

    };

});


custModule.directive('agMetric', function () {

    return {
        restrict: 'E',
        require: '^agChart',
        scope: {},
        template: '',
        link: function (scope, element, attributes, chartCtrl) {
            console.log('ag-metric is adding');
            chartCtrl.addMetric(attributes.name, element.text());
            element.html('<span> </span>');
        }
    };
});


custModule.directive('agFlags', function () {

    return {
        restrict: 'E',
        require: '^agChart',
        scope: {},
        template: '',
        link: function (scope, element, attributes, chartCtrl) {
            //console.log('ag-flag is adding');
            chartCtrl.addAnnotation(attributes.name, element.text());
            element.html('<span> </span>');
        }

    };

});


//Delete the following TODO

custModule.controller('testCtrl', function () {

});




custModule.directive('divfour', function () {

    return{
        restrict: 'E',
        scope: {},
        controller: function ($scope) {
            $scope.mylist = [];
            console.log($scope);

            this.setme = function (val) {
                $scope.mylist.push(val);
            };
        },
        link: function (s, e, a) {
            alert(JSON.stringify(s.mylist));
        }
    };

});




custModule.directive('divfive', function () {

    return{
        restrict: 'E',
        scope: {},
        require: '^divfour',
        link: function (s, e, a, c) {
            if (a.hh) {
                alert('no');
            } else {
                alert('tes');
            }
            c.setme(a.name);
        }
    };

});
