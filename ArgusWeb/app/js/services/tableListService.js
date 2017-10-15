/**
 * Created by liuxizi.xu on 1/9/17.
 */
'use strict';
/*global angular:false */

angular.module('argus.services.tableListService', [])
.service('TableListService', [function () {
	this.deleteItemFromListHelper = function (itemList, itemToDelete) {
		return itemList.filter(function(element) {
			return element.id !== itemToDelete.id;
		});
	};

	this.getListUnderTab = function (allItems, userName, userPrivileged) {
		var i;
		var result = {
			usersList: [],
			sharedList: [],
			privilegedList: []
		};
		var totNum = allItems.length;
		if (userPrivileged) {
			result.privilegedList= [];
			for (i = 0; i < totNum; i++) {
				// get all shared items
				if (allItems[i].shared) {
					result.sharedList.push(allItems[i]);
				}
				// get all user's items
				if (allItems[i].ownerName === userName) {
					result.usersList.push(allItems[i]);
				// get all privileged items
				} else if (!allItems[i].shared) {
					result.privilegedList.push(allItems[i]);
				}
			}
		} else {
			for (i = 0; i < totNum; i++) {
				if (allItems[i].shared) result.sharedList.push(allItems[i]);
				if (allItems[i].ownerName === userName) result.usersList.push(allItems[i]);
			}
		}
		return result;
	};

	this.addItemToTableList = function (allList, propertyType, item, userName, userPrivileged) {
		if (item.shared) allList.sharedList.push(item);
		if (item.ownerName === userName || userPrivileged) allList.usersList.push(item);
		if (userPrivileged && !item.shared && item.ownerName !== userName) allList.privilegedList.push(item);
		return allList;
	};

	this.deleteItemFromTableList = function (allList, propertyType, item, userName, userPrivileged) {
		if (item.shared) allList.sharedList = this.deleteItemFromListHelper(allList.sharedList, item);
		if (item.ownerName === userName || userPrivileged)
			allList.usersList = this.deleteItemFromListHelper(allList.usersList, item);
		if (userPrivileged && !item.shared && item.ownerName !== userName)
			allList.privilegedList = this.deleteItemFromListHelper(allList.privilegedList, item);
		return allList;
	};
}]);