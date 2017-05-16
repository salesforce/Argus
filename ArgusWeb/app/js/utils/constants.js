/*global angular:false */

angular.module('argus.constants', [])

.constant('VIEWELEMENT', {
	chart: 'chart',
	heatmap: 'heatmap',
	table: 'table'
})

.constant('CHARTTYPE', {
	line: 'line',
	area: 'area'
})

.constant('BATCH_CHART_OPTIONS', {
	credits: {
		enabled: false
	},
	rangeSelector: {
		selected: 1,
		inputEnabled: false
	},
	xAxis: {
		type: 'datetime',
		ordinal: false
	},
	lang: {
		noData: 'No Data to Display'
	},
	legend: {
		enabled: true,
		maxHeight: 62,
		itemStyle: {
			fontWeight: 'normal',
			fontSize: '10px'
		},
		navigation: {
			style: {
				fontWeight: 'normal',
				fontSize: '10px'
			}
		}
	},
	plotOptions: {
		series: {
			animation: false,
			connectNulls: true
		},
		line: {
			gapSize: 1.5
		}
	},
	chart: {
		animation: false,
		borderWidth: 1,
		borderColor: 'lightGray',
		borderRadius: 5
	}
});
