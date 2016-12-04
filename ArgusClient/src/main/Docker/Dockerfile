FROM openjdk:8u111-jre

RUN apt-get update -y && apt-get install -y telnet net-tools less vim

ENV APP_DIR /usr/argus/argusClient

RUN mkdir -p ${APP_DIR}
COPY client.sh ${APP_DIR}/
COPY README.md ${APP_DIR}/
COPY usage.txt ${APP_DIR}/

ARG JAR_WITH_DEPS
COPY ${JAR_WITH_DEPS} ${APP_DIR}/

ENV ARGUSCLIENT_EXE ${APP_DIR}/${JAR_WITH_DEPS}

WORKDIR ${APP_DIR}

ENTRYPOINT ["./client.sh"]