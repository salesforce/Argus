/*global angular:false */

angular.module('argus.services.storage', [])
.factory('Storage', ['$rootScope', '$localStorage', '$sessionStorage','$injector', '$window', '$location', function ($rootScope, $localStorage, $sessionStorage, $injector, $window, $location) {
	var storageKeyPrefix = 'ngStorage-';
	var serializer = angular.toJson;
	var deserializer = angular.fromJson;
	var localStorage = $window.localStorage;
	$rootScope.storage = $localStorage;
	var warnModalCount = 0; //prevent user from clicking so many confirm modals
	function warn(ls){
		if(warnModalCount > 0) return;
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

	function isQuotaExceeded(e) {
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
			var result = deserializer(localStorage.getItem(storageKeyPrefix + key));
			return angular.isDefined(result) ? result : null;
		},

		set : function (key, value) {
			var self = this;
			try {
				//using $localStorage cannot handle the error here
				localStorage.setItem(storageKeyPrefix + key, serializer(value));
			}catch (e) {
				if(isQuotaExceeded(e)){
					warn(self);
				}
			}
		},
		clear : function (key) {
			localStorage.removeItem(storageKeyPrefix + key);
		},
		reset : function(){
			//delete user info, but preserve the storage of preferences
			this.clear('user');
			this.clear('target');
			$sessionStorage.$reset();
		},
		resetAll : function(){
			for (var k in localStorage) {
				if(k.substring(0, storageKeyPrefix.length) === storageKeyPrefix){
					localStorage.removeItem(k);
				}
			}
			$sessionStorage.$reset();
		}
	};
}]);