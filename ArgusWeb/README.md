ArgusWeb
=

#
#### Build the Front-End
---
> Install the following:

- NodeJS & NPM
- Bower
- Grunt

##### Install NodeJS
#
> **Recommended** - You can use Homebrew to install NodeJS.  This will install the latest version.

```sh
$ brew install node
```
This will prevent NPM permission issues when installing bower & grunt globally.

> OR, you can download and install the latest **stable** version of NodeJS - https://nodejs.org/en/download/

#
###### To fix any NPM permission errors:
- http://stackoverflow.com/questions/16151018/npm-throws-error-without-sudo
- https://docs.npmjs.com/getting-started/fixing-npm-permissions

To verify NodeJS installation:
```sh
$ node -v
```

##### Install Bower globally
#
```sh
$ npm install -g bower
```

To verify Bower installation:
```sh
$ bower -v
```

##### Install Grunt globally
#
```sh
$ npm install -g grunt
```

##### Build Commands
#
###### Run npm to install:
#
```sh
$ npm install
```

###### Run bower to install packages from bower.json:
#
```sh
$ bower install
```

###### Run grunt to build
#
```sh
$ grunt
```
#
> NOTE: You can update the following .json files to reflect the environment you want to build for.

./config/local.json
```sh
$ grunt replace:local
```

./config/development.json
```sh
$ grunt replace:development
```

./config/production.json
```sh
$ grunt replace:production
```