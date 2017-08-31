/*global angular:false */
'use strict';
angular.module('argus.services.charts.options', [])
.service('ChartOptionService', ['UtilService', function(UtilService) {
	var options = {
		setCustomOptions: function(options, optionList) {
			for (var idx in optionList) {
				var propertyName = optionList[idx].name;
				var propertyValue = optionList[idx].value;
				var result = UtilService.constructObjectTree(propertyName, propertyValue);

				UtilService.copyProperties(result, options);
			}
			return options;
		},

		getOptionsByChartType: function(config, chartType, smallChart) {
			var options = config ? angular.copy(config) : {};

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

			options.credits = {enabled: false};
			options.rangeSelector = {selected: 1, inputEnabled: false};

			options.xAxis = {
				type: 'datetime',
				ordinal: false
			};

			options.lang = {
				loading: '',    // override default 'Loading...' msg from displaying under spinner img.
				noData: 'No Data to Display'
			};

			// loading spinner for graph
			options.loading = {
				labelStyle: {
					top: '25%',
					backgroundImage: 'url("img/ajax-loader.gif")',
					backgroundSize: '80px 80px',
					backgroundRepeat: 'no-repeat',
					display: 'inline-block',
					width: '80px',
					height: '80px',
					backgroundColor: '#FFF'
				}
			};

			if (chartType && chartType.toUpperCase() === 'AREA') {
				options.plotOptions = {series: {animation: false}};
				options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
			} else if (chartType && chartType.toUpperCase() === 'STACKAREA') {
				options.plotOptions = {
					area: {
						stacking: 'normal',
						// lineWidth: 1.5,
						dataGrouping: {
							enabled: true//,
							//  groupPixelWidth: 2
						},
						animation: false,
						marker: {
							enabled: false
						}
					}
				};
				options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5, type: 'area'};
			} else {
				options.plotOptions = {series: {animation: false}};
				options.chart = {animation: false, borderWidth: 1, borderColor: 'lightGray', borderRadius: 5};
			}

			// override options for a 'small' chart, e.g. 'Services Status' dashboard
			if ( smallChart ) {
				options.legend.enabled = false;
				options.rangeSelector.enabled = false;

				options.scrollbar = {enabled: false};
				options.navigator = {enabled: false};

				options.chart.height = '120';
				options.chart.borderWidth = 0;

				// reset loading options, no spinner required
				options.lang = {
					loading: 'Loading...'
				};
				options.loading = {};
			}

			return options;
		}
	};

	return options;
}]);
