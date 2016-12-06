FROM node:7.2.0-alpine

RUN apk add -q --update vim bash git

RUN npm --quiet install -g bower
RUN npm --quiet install -g grunt

COPY argusWeb /usr/argus/argusWeb
WORKDIR /usr/argus/argusWeb

RUN npm --quiet install && bower --quiet --allow-root install

EXPOSE 8000
CMD ["npm", "start"]
