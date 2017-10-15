#!/usr/bin/env bash
cp .travis.settings.xml $HOME/.m2/settings.xml
# PROJECT_VERSION in the commands below should be incremented and sourced from project.version
source project.version
# Builds top level pom w/ zip
mvn --batch-mode --non-recursive -DskipTests -DskipDockerBuild -U -Prelease -Dproject.version=${PROJECT_VERSION} clean install
mvn --batch-mode -DskipDockerBuild -DskipTests -Dproject.version=${PROJECT_VERSION} -Prelease clean deploy
