FROM tomcat:7.0.73-jre8

RUN apt-get -qq update -y && apt-get -qq install -y telnet net-tools less vim

ARG WAR
COPY ${WAR} /usr/local/tomcat/webapps/argus.war