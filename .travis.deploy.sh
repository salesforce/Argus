#!/usr/bin/env bash
cp .travis.settings.xml $HOME/.m2/settings.xml
ls $HOME/.m2/settings.xml
# Builds top level pom w/ zip
#mvn --non-recursive -DskipTests -DskipDockerBuild -U -Prelease -Dproject.version=${PROJECT_VERSION} clean install
# mvn -DskipDockerBuild -DskipTests -Dproject.version=${PROJECT_VERSION} -Prelease clean deploy
