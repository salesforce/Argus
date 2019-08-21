# ArgusWeb

- [ArgusWeb](#argusweb)
  - [Prerequisites](#prerequisites)
    - [Install NodeJS & NPM](#install-nodejs--npm)
    - [Install Grunt](#install-grunt)
  - [Setup](#setup)
  - [Developing locally](#developing-locally)
  - [Building for Staging/Prod](#building-for-stagingprod)
  - [Other commands](#other-commands)
  
## Prerequisites
### Install NodeJS & NPM

**Recommended** - You can use Homebrew to install NodeJS.  This will install the latest version.

```sh
$ brew install node
```
This will prevent NPM permission issues when `npm install`ing anything else

**OR** - you can download and install the latest **stable** version of NodeJS - https://nodejs.org/en/download/


To verify NodeJS installation:
```sh
$ node -v
```

### Install Grunt
```sh
$ npm install -g grunt
```
You should not have to use `sudo` when running `npm install`. See resolving permission errors:
- http://stackoverflow.com/questions/16151018/npm-throws-error-without-sudo
- https://docs.npmjs.com/getting-started/fixing-npm-permissions


## Setup
This installs all dependencies both at the project-level (`ArgusWeb/`) and Angular app level (`app/`)
```sh
$ npm install
```

## Developing locally
Run one of these commands according to what webservice environment you wish to use
```sh
$ grunt replace:local
# OR
$ grunt replace:qa
```
**Note: you may have to modify `wsUrl` in `app/js/config.js` if webservices is running on a different port**
```sh
# This automatically uses jscodeshift to replace all templateUrl references
# to require format for webpack build process. The replace will be undone on Ctrl+C
$ npm start
```

To [eslint](http://eslint.org/) check against *app* folder (all the source code):
```sh
$ npm run lint
```

## Building for Staging/Prod
```sh
$ grunt replace:<ENVIRONMENT>
# modify app/js/config.js as needed
$ npm run bundle
```
This will bundle all files into a flat `dist/` folder, which can then be copied to the staging/prod host.

Example for copying the UI so it runs on Tomcat's `localhost:8080/argus/`:
```sh
$ cp -r dist $TOMCAT_HOME/webapps/argus
```

## Other commands
Remove all existing npm modules
```sh
$ npm run clean
```

