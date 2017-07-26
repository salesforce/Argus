#!/usr/bin/env bash
cp .travis.settings.xml $HOME/.m2/settings.xml
# Builds top level pom w/ zip
# TODO: Figure out if ${PROJECT_VERSION} should be manually incremented in the POM or handled some other way
mvn --batch-mode --non-recursive -DskipTests -DskipDockerBuild -U -Prelease -Dproject.version=${PROJECT_VERSION} clean install
mvn --batch-mode -DskipDockerBuild -DskipTests -Dproject.version=${PROJECT_VERSION} -Prelease clean deploy
