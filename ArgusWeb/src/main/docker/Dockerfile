FROM node:7.2.0

RUN apt-get update -y && apt-get install -y telnet net-tools less vim
RUN npm install -g bower
RUN npm install -g grunt

COPY argusWeb /usr/argus/argusWeb
WORKDIR /usr/argus/argusWeb

RUN npm install --quiet && bower --quiet --allow-root install
RUN grunt replace:local && grunt

EXPOSE 8000
CMD ["npm", "start"]
