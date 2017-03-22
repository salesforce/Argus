# makefile for building Argus webservices rpms
#change the following two props

targetpath=/home/sfdc/argus/installed

buildnumber=${BUILD_NUMBER}

all: build-all package

webservices: build-all create-arguswebservices-rpm

client: build-all create-argusclient-rpm

build-all:
	rm -rf Argus
	mvn install -Dmaven.test.skip=true -DskipDockerBuild

package: create-arguswebservices-rpm create-argusclient-rpm create-argusui-rpm

create-arguswebservices-rpm:
	rpmbuild -ba --define "_topdir $(CURDIR)" --define "_targetdir ${targetpath}/ws/${buildnumber}/"   --target=noarch SPECS/argus-webservices.spec
	
create-argusclient-rpm:
	rpmbuild -ba --define "_topdir $(CURDIR)" --define "_targetdir ${targetpath}/client/${buildnumber}/"  --target=noarch SPECS/argus-client.spec

create-argusui-rpm:
	rpmbuild -ba --define "_topdir $(CURDIR)" --define "_targetdir ${targetpath}/ui/${buildnumber}/"  --target=noarch SPECS/argus-ui.spec
		
