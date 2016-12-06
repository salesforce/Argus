FROM tomcat:7.0.73-jre8-alpine

RUN apk add -q --update vim bash

ARG WAR
COPY ${WAR} /usr/local/tomcat/webapps/argus.war