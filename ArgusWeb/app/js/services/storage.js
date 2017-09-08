'use strict';
/*global angular:false, LZString:false, Promise:false */

angular.module('argus.services.storage', [])
.factory('Storage', ['$rootScope', '$localStorage', '$sessionStorage','$injector', '$window', '$location', function ($rootScope, $localStorage, $sessionStorage, $injector, $window, $location) {
	var storageKeyPrefix = 'ngStorage-';
	var serializer = angular.toJson;
	var deserializer = angular.fromJson;
	var localStorage = $window.localStorage;
	$rootScope.storage = $localStorage;
	var warnModalCount = 0; //prevent user from clicking so many confirm modals
	function warn (ls) {
		if (warnModalCount > 0) return;
		warnModalCount ++;
		var ConfirmClick = $injector.get('ConfirmClick');
		ConfirmClick.openConfirmModal(
			'Clear Browser\'s local storage',
			'Your browser\'s local storage is full, click confirm to clear browser\'s local storage. <br>'+
			'<strong style="color: red;">Warning: This will clear your menu options preferences.</strong>',
			function(){
				var user = ls.get('user');
				var target = ls.get('target');
				var accessToken = ls.get('accessToken');
				var refreshToken = ls.get('refreshToken');
				ls.resetAll();
				ls.set('user', user);
				ls.set('target', target);
				ls.set('accessToken', accessToken);
				ls.set('refreshToken', refreshToken); //prevent user from login again
				warnModalCount--;
			},
			function(){
				warnModalCount--;
			}
		);
	}

	function isQuotaExceeded (e) {
		var quotaExceeded = false;
		if (e) {
			if (e.code) {
				switch (e.code) {
					case 22:
						quotaExceeded = true;
						break;
					case 1014:
						// Firefox
						if (e.name === 'NS_ERROR_DOM_QUOTA_REACHED') {
							quotaExceeded = true;
						}
						break;
				}
			} else if (e.number === -2147024882) {
				// Internet Explorer 8
				quotaExceeded = true;
			}
		}
		return quotaExceeded;
	}

	return {
		get : function (key) {
			var result = localStorage.getItem(storageKeyPrefix + key);
			if(result !== undefined) result = deserializer(result);
			return angular.isDefined(result) ? result : null;
		},

		set : function (key, value) {
			var self = this;
			try {
				//using $localStorage cannot handle the error here
				var val = serializer(value);
				if(val !== undefined){
					localStorage.setItem(storageKeyPrefix + key, val);
				}
			} catch (e) {
				if(isQuotaExceeded(e)){
					warn(self);
				}
			}
		},
		clear : function (key) {
			localStorage.removeItem(storageKeyPrefix + key);
		},
		reset : function () {
			//delete user info, but preserve the storage of preferences
			this.clear('user');
			this.clear('target');
			$sessionStorage.$reset();
		},
		resetAll : function () {
			for (var k in localStorage) {
				if (k.substring(0, storageKeyPrefix.length) === storageKeyPrefix) {
					localStorage.removeItem(k);
				}
			}
			$sessionStorage.$reset();
		},
		initializeSessionList : function (listName) {
			$sessionStorage[listName] = {
				cachedData: {},
				cachedCompressedData: '',
				emptyData: true,
				loadedEverything: false,
				selectedTab: undefined
			};
		},
		getSessionList : function (listName) {
			return $sessionStorage[listName];
		},
		compressData : function (data) {
			return Promise.resolve(LZString.compress(JSON.stringify(data)));
		},
		decompressData : function (data) {
			return Promise.resolve(JSON.parse(LZString.decompress(data)));
		},
		roughSizeOfObject: function (object) {
			var objectList = [];
			var recurse = function(value) {
				var bytes = 0;
				if (typeof value === 'boolean') {
					bytes = 4;
				} else if (typeof value === 'string') {
					bytes = value.length * 2;
				} else if (typeof value === 'number') {
					bytes = 8;
				} else if (typeof value === 'object' && objectList.indexOf(value) === -1) {
					objectList[ objectList.length ] = value;
					for (var i in value) {
						bytes+= 8; // assumed existence overhead
						bytes+= recurse( value[i] );
					}
				}
				return bytes;
			};
			return recurse(object);
		}
	};
}]);