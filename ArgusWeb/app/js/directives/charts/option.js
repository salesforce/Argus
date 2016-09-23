/* currently not being used? */

angular.module('argus.directives.charts.option', [])
.directive('agOption', function() {
    return {
        restrict: 'E',
        require: ['?^agChart', '?^agHeatmap', '?^agTable', '?^agMetric'], 
        scope: {},
        template: '',
        link: function(scope, element, attributes, controllers) {
        	var elementCtrl;

        	if (controllers[3]) {
        		elementCtrl = controllers[3];
        	} else if(controllers[0]) {
                elementCtrl = controllers[0];
            } else if (controllers[1]) {
                elementCtrl = controllers[1];
            } else {
                elementCtrl = controllers[2];
            }
        	
            var value = '';
            if (attributes.value && attributes.value.length > 0) {
                value = attributes.value;
            } else {
                value = element.text();
            }
            
            elementCtrl.updateOption(attributes.name, value);
            element.html('<span> </span>');
        }
    }
});