/**
 * Created by liuxizi.xu on 2/2/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.downloadHelper', [])
.service('DownloadHelper', function () {
	this.downloadFile = function (data, filename) {
		var blob = new Blob([data], {type: 'text/plain'});
		saveAs(blob, filename);
	};
});