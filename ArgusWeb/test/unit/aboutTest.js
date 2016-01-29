'use strict';

describe('AboutModuleTest -', function () {

    describe('testAboutConfig', function () {
        var scope, ctrl;

        beforeEach(module('argusMain'));
        beforeEach(inject(function ($rootScope, $controller) {
            scope = $rootScope.$new();
            ctrl = $controller('AboutDetailCtrl', {$scope: scope});
        }));


        it('should contain non-null values for appVersion, emailUrl, wikiUrl, feedUrl, issueUrl', function () {
            expect(scope.config).not.toBeUndefined();
            var appVersion = scope.config.appVersion;
            expect(appVersion !== null && parseFloat(appVersion) >= 0).toBe(true);
            expect(scope.config.emailUrl).toBeDefined();
            expect(scope.config.emailUrl).not.toBeNull();
            expect(scope.config.wikiUrl).toBeDefined();
            expect(scope.config.wikiUrl).not.toBeNull();
            expect(scope.config.feedUrl).toBeDefined();
            expect(scope.config.feedUrl).not.toBeNull();
            expect(scope.config.issueUrl).toBeDefined();
            expect(scope.config.issueUrl).not.toBeNull();
        });

    });


});
