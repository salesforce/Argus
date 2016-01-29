'use strict';

describe('DashboardsModuleTest -', function () {

    describe('testFetchOfDashboardList', function () {
        var scope, ctrl, $httpBackend;
        var expected = {
            "id": "200001",
            "name": "S1 EPT",
            "description": "S1 EPT for top usage scenarios.",
            "owner": "admin"
        }

        beforeEach(module('argusMain'));
        beforeEach(inject(function (_$httpBackend_, $rootScope, $controller) {
            $httpBackend = _$httpBackend_;
            $httpBackend.expectGET('views/dashboards/dashboards.json').
                    respond([expected]);

            scope = $rootScope.$new();
            ctrl = $controller('DashboardListCtrl', {$scope: scope});
        }));


        it('should return a list of available dashboards.', function () {
            expect(scope.dashboards).toBeUndefined();
            $httpBackend.flush();

            expect(scope.dashboards).toEqual([expected]);
        });

    });


    describe('PhoneDetailCtrl', function () {
    });
});
