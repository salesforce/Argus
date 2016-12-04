FROM tomcat:7.0.73-jre8

RUN apt-get update -y && apt-get install -y telnet net-tools less vim

ARG WAR
COPY ${WAR} /usr/local/tomcat/webapps/argus.war