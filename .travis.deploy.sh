#!/usr/bin/env bash
# Generate & push code signing keys to keyserver.ubuntu.com, source into environment
source ${TRAVIS_BUILD_DIR}/.travis.gpg.sh

set -ex
# Utilize tokenized `.travis.settings.xml` to do deployment
cp ${TRAVIS_BUILD_DIR}/.travis.settings.xml ${HOME}/.m2/settings.xml

# PROJECT_VERSION in the commands below should be incremented and sourced from project.version
source ${TRAVIS_BUILD_DIR}/project.version

# Set the version of all `pom.xml`s to ${PROJECT_VERSION} from the `project.version` file
echo "Setting Maven version to ${PROJECT_VERSION}"
mvn versions:set -DnewVersion=${PROJECT_VERSION}
# Builds top level pom w/ zip
mvn --batch-mode --non-recursive -DskipTests -DskipDockerBuild -U -Prelease -Dproject.version=${PROJECT_VERSION} clean install
mvn --batch-mode -DskipDockerBuild -DskipTests -Dproject.version=${PROJECT_VERSION} -Prelease clean deploy
