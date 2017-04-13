/**
 * Created by liuxizi.xu on 2/2/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.downloadHelper', [])
.service('DownloadHelper', function () {
	this.downloadFile = function (data, filename) {
		var url = window.URL.createObjectURL(new Blob([data]));
		var a = document.createElement('a');
		a.href = url;
		a.download = filename;
		a.target = '_blank';
		a.click();
	};
});