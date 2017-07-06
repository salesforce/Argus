 /*global angular:false, moment:false */

angular.module('argus.filters', [])

.filter('isEmpty', function() {
	return function(object) {
		return angular.equals({}, object);
	};
})

.filter('duration', function () {
	return function (duration) {
		var seconds = Math.floor(( duration / 1000 ) % 60),
			minutes = Math.floor(( duration / (1000 * 60) ) % 60),
			hours = (duration < 86400000) ? Math.floor(( duration / (1000*60*60) ) % 24) : Math.floor( duration / (1000*60*60) );

		hours = (hours < 1) ? '' : hours + 'h\u00A0';
		minutes = (minutes < 1) ? '' : minutes + 'm\u00A0';
		seconds = (seconds < 1) ? '' : seconds + 's';

		if (seconds === '' && minutes === '' && hours === '') {
			seconds = '0s';
		}

		return hours + minutes + seconds;
	};
})

.filter('duration_fractional', function() {
	return function(original) {
		var s = original;
		var ms = s % 1000;
		s = (s - ms) / 1000;
		var secs = s % 60;
		s = (s - secs) / 60;
		var mins = s % 60;
		var hrs = (s - mins) / 60;

		if ( hrs > 0 ) {
			return Math.floor(10 * original / (60*60*1000) ) / 10 + 'h';
		} else if ( mins > 0 ) {
			return Math.floor( 10 * original / (60*1000) ) / 10 + 'm';
		} else if ( original > 10) {
			return Math.floor( original / 10 ) / 100 + 's';
		} else if ( original > 0) {
			return Math.floor( original ) + 'ms';
		} else {
			return 0;
		}
	};
})

.filter('bytes', function() {
	return function(input) {
		if (input === undefined || input === null) return 'n/a';
		if (input === 0) return '0';

		var sizes = ['B', 'KB', 'MB', 'GB', 'TB'];

		var sizeIndex = Math.floor(Math.log(input) / Math.log(1024));
		var outputNum = Math.round(100 * input / Math.pow(1024, sizeIndex)) / 100;

		return outputNum + ' ' + sizes[sizeIndex];
	};
})

.filter('base10si', function() {
	return function(input) {
		if (input === undefined || input === null) return 'n/a';
		if (input === 0) return '0';

		var sizes = ['', 'K', 'M', 'G', 'T'];

		var sizeIndex = Math.floor(Math.log(input) / Math.log(1000));
		var outputNum = Math.round(100 * input / Math.pow(1000, sizeIndex)) / 100;

		return outputNum + ' ' + sizes[sizeIndex];
	};
})

.filter('nullable_decimal', function() {
	return function(input) {
		if (input === undefined || input === null) return 'n/a';
		return parseFloat(input).toFixed(2);
	};
})

.filter('_date_', function() {
	return function(input) {
		if (input === undefined || input === null || input === 0) return 'n/a';
		return moment(input).format('MMM Do, YYYY');
	};
})

.filter('_time_', function() {
	return function(input) {
		if (input === undefined || input === null || input === 0) return 'n/a';
		return moment(input).format('h:mm:ss a');
	};
})

// all date/times come back from the server in UTC - reformat to local (browser) time
.filter('_date_time_', function() {
	return function(input) {
		if (input === undefined || input === null || input === 0) return 'n/a';
		return moment(input).format('MMM Do, YYYY h:mm:ss a');
	};
})

// all date/times come back from the server in UTC - reformat to utc (browser) time
.filter('_date_time_utc', function() {
	return function(input) {
		if (input === undefined || input === null || input === 0) return 'n/a';
		return moment.utc(input).format('MMM Do, YYYY h:mm:ss a');
	};
})

// short version of date_time, format:  Jan 10th
.filter('_short_date_time_', function() {
	return function(input) {
		if (input === undefined || input === null || input === 0) return 'n/a';
		return moment(input).format('MMM Do');
	};
})

.filter('_date_time_from_timestamp_', function() {
	return function(input, asRangeFrom) {
		var num = parseInt(input);
		if ( isNaN(num) ) return 'n/a';
		var time = moment(num);
		var formatString = 'MMM Do, YYYY h:mm a';
		var dateComponent = true;
		var yearComponent = true;
		var hourComponent = true;
		var minuteComponent = true;

		if ( asRangeFrom ) {
			var asRangeNum = parseInt(asRangeFrom);
			if ( !isNaN(asRangeNum) ) {
				var fromTime = moment(asRangeNum);
				if(time.year() == fromTime.year()){
					yearComponent = false;
				}
				if ( time.year() == fromTime.year() && time.date() == fromTime.date() && time.month() == fromTime.month() ) {
					dateComponent = false;
				}
			}
		}

		formatString = (dateComponent ? 'MMM Do' : '') + (dateComponent && yearComponent ? ', YYYY ' : (dateComponent ? ' ' : '')) + (hourComponent ? 'h' : '') + ( minuteComponent ? ':mm' : '' ) + ( hourComponent || minuteComponent ? ' a' : '' );
		return time.format(formatString);
	};
})

.filter('truncateString', function() {
	return function(input, length) {
		if ( input && input.length > length ) {
			return input.substring(0,length) + '\u2026';
		}
		return input;
	};
})

.filter('capitalize', function() {
	return function(input) {
		if (input != null) {
			input = input.toLowerCase();
			return input.substring(0,1).toUpperCase() + input.substring(1);
		}
	};
})

.filter('urlencode', function () {
	return window.encodeURIComponent;
})

.filter('newline', function() {
	return function(data) {
		if (data && data.length > 0) {
			var retvalue = data.replace(/</g, '&lt');
			retvalue = retvalue.replace(/>/g, '&gt');
			retvalue = retvalue.replace(/\n/g, '<br/>');
			return retvalue;
		} else return '';
	};
})

.filter('trustedhtml', ['$sce', function ($sce) {
	return function (text) {
		return $sce.trustAsHtml(text);
	};
}])

.filter('agTableSort', function(){
	return function(item, sortedIndices){
		var newArray = [];

		if(!sortedIndices || sortedIndices.length == 0){
			for(var key in item){
				if(key !== 'timestamp'){
					newArray.push(item[key]);
				}
			}
		}else{
			for(var i = 0; i< sortedIndices.length; i++) {
				newArray.push(item[sortedIndices[i]]);
			}
		}
		return newArray;
	};
});
