#!/usr/bin/env bash
# Import code signing keys
# TODO: NEED IDs FOR THE KEYS AND IV!!!
openssl aes-256-cbc -K $encrypted_ID_key -iv $encrypted_ID_iv -in codesigning.asc.enc -out codesigning.asc -d
gpg --fast-import codesigning.asc

# Remove code signing keys for cleanliness
rm codesigning.asc

cp .travis.settings.xml $HOME/.m2/settings.xml
# PROJECT_VERSION in the commands below should be incremented and sourced from project.version
source project.version
# Builds top level pom w/ zip
mvn versions:set -DnewVersion=${PROJECT_VERSION}
mvn --batch-mode --non-recursive -DskipTests -DskipDockerBuild -U -Prelease -Dproject.version=${PROJECT_VERSION} clean install
mvn --batch-mode -DskipDockerBuild -DskipTests -Dproject.version=${PROJECT_VERSION} -Prelease clean deploy
