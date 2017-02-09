/**
 * Created by liuxizi.xu on 1/9/17.
 */
'use strict';

angular.module('argus.services.tableListService', [])
.service('TableListService', ['$sessionStorage', function ($sessionStorage) {
    this.deleteItemFromListHelper = function (itemList, itemToDelete) {
        return itemList.filter(function(element) {
            return element.id !== itemToDelete.id;
        });
    };

    this.getListUnderTab = function (allItems, shared, userName) {
        var i, result = [];
        var totNum = allItems.length;
        if(shared) {
            for(i = 0; i < totNum; i++) {
                if(allItems[i].shared) {
                    result.push(allItems[i]);
                }
            }
        } else {
            for(i = 0; i < totNum; i++) {
                if (allItems[i].ownerName === userName) {
                    result.push(allItems[i]);
                }
            }
        }
        return result;
    };

    this.addItemToTableList = function (allList, propertyType, item, userName) {
        $sessionStorage[propertyType].cachedData.push(item);
        if (item.shared) allList.sharedList.push(item);
        if (item.ownerName === userName) allList.usersList.push(item);
        return allList;
    };

    this.deleteItemFromTableList = function (allList, propertyType, item, userName) {
        $sessionStorage[propertyType].cachedData = this.deleteItemFromListHelper($sessionStorage[propertyType].cachedData, item);
        if (item.shared) allList.sharedList = this.deleteItemFromListHelper(allList.sharedList, item);
        if (item.ownerName === userName) allList.usersList = this.deleteItemFromListHelper(allList.usersList, item);
        return allList;
    };
}]);