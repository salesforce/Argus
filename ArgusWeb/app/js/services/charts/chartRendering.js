/*global angular:false, $:false */

angular.module('argus.services.charts.rendering', [])
.service('ChartRenderingService', ['UtilService', function(UtilService) {
	'use strict';
	//TODO: clean up the highchart
	var service = {
		getChart: function(chartId, highChartOptions) {
			if (!chartId) return;
			$('#' + chartId).highcharts('StockChart', highChartOptions);
			var chart = $('#' + chartId).highcharts('StockChart');
			return chart;
		},

		setChartContainer: function(element, chartId, cssOpts) {
			if (!element || !chartId) return;
			element.prepend(
				'<div id='+ chartId +' class="chartContainer ' + cssOpts +'"></div>'
			);
		},

		loadChart: function(chartId, highChartOptions) {
			var chart = this.getChart(chartId, highChartOptions);

			// show loading spinner & hide 'no data message' during api request
			if (chart)
				this.loading(chart, true);
		},

		loading: function(chart, loading) {
			if (!chart) return;

			// show loading indicator if chart is loading
			if (loading) {
				chart.showLoading();
				chart.hideNoData();
			} else {
				// chart finishd loading: hide loading indicator when loading is complete; chart may have no data.
				chart.hideLoading();
				if ( !chart.hasData() )
					chart.showNoData();
			}
		},

		displayChart: function(chartId) {
			// display chart in DOM
			var chart = this.getChart(chartId);

			// hide the loading spinner after data loads.
			if (chart)
				this.loading(chart, false);
		},

		updateIndicatorStatus: function(attributes, lastStatusVal) {
			if (attributes === undefined || lastStatusVal === undefined) return;

			var lastStatusValNum = Number(lastStatusVal);
			var lowValNum = Number(attributes.lo);
			var highValNum = Number(attributes.hi);

			// display options
			var showNum = (attributes.shownum == 'true') ? true : false;
			var showLight = (attributes.showlight == 'false') ? false : true;

			var errorMsg = 'value in ag-status-indicator is not defined correctly';
			if (isNaN(lowValNum)) console.log(attributes.name + '\'s lo - ' + lowValNum + ' : ' + isNaN(lowValNum) + ' ' + errorMsg);
			if (isNaN(highValNum)) console.log(attributes.name + '\'s hi - ' + highValNum + ' : ' + isNaN(highValNum) + ' ' + errorMsg);

			// show light indicator if set
			if (showLight) {
				if (lastStatusValNum < lowValNum) {
					$('#' + UtilService.cssNotationCharactersConverter(attributes.name) + '-light').removeClass('red orange green').addClass('red');
				} else if (lastStatusValNum >= lowValNum && lastStatusVal <= highValNum) {
					$('#' + UtilService.cssNotationCharactersConverter(attributes.name) + '-light').removeClass('red orange green').addClass('orange');
				} else if (lastStatusValNum > highValNum) {
					$('#' + UtilService.cssNotationCharactersConverter(attributes.name) + '-light').removeClass('red orange green').addClass('green');
				}
			} else {
				// hide light indicator
				$('#' + UtilService.cssNotationCharactersConverter(attributes.name) + '-light').addClass('hide');
			}

			// show numerical value if set
			if (showNum) {
				if (lastStatusVal !== undefined) {
					lastStatusVal = lastStatusValNum.toLocaleString();
				}
				$('#' + UtilService.cssNotationCharactersConverter(attributes.name) + '-numVal').removeClass('hide').addClass('inlineBlock').html(lastStatusVal);
			}

			// add custom css class if set
			$('#' + UtilService.cssNotationCharactersConverter(attributes.name) + '-container').addClass(attributes.cssclass);
		}
	};
	return service;
}]);
