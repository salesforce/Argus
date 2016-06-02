ArgusWeb
=

- Assumptions
- Build the Front-End
- Deployment
- Notes

### Assumptions
> You can skip ahead to 'Build the Front-End' if you already know this stuff!
> Assumes you have already installed the following:

- OSX Xcode & Command line tools
- Macports or Brew, or both if you like :)
- Java SE Runtime, version 8
- Maven
- Tomcat

#
##### Install Java
---
> Install the .dmg version for OS X from:
- http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html

Verify the Java installation:
```sh
$ java -version
```
Should output:
```sh
java version "1.8.0_91"
Java(TM) SE Runtime Environment (build 1.8.0_91-b14)
Java HotSpot(TM) 64-Bit Server VM (build 25.91-b14, mixed mode)
```

Set $PATH for $JAVA_HOME. Edit your **.bash_profile** in **/Users/YOUR_USERNAME** directory
```sh
$ export JAVA_HOME=$(/usr/libexec/java_home)
```

Verify $JAVA_HOME in a new terminal window/tab:
```sh
$ echo $JAVA_HOME
```
Should output:
```sh
/Library/Java/JavaVirtualMachines/jdk1.8.0_91.jdk/Contents/Home
```

Lastly, you can verify the Java executable location:
```sh
$ which java
or
$ whereis java
```
Should output:
```sh
/user/bin/java
```

##### Install Maven
---
> The best option (to avoid build errors) is to install maven via either Homebrew or Macports
```sh
$ brew install maven
```

Verify Maven installation:
```sh
$mvn -version
```
Should output the version of Maven, it's home directory and some info about Java and your machine.

Update $PATH for Maven
```sh
export PATH=$PATH:LOCATION_TO_MAVEN_FOLDER/apache-maven-3.3.9/bin
```

##### Install Tomcat 8
---
>http://shinyfeather.com/tomcat/tomcat-8/v8.5.2/bin/

scripts to be familiar with:
```sh
$ ./startup.sh
$ ./shutdown.sh
```

> Refer to the Google Document: **SettingUpArgusDevandRunningEnvironment.docx** for further instructions on setting up Tomcat and Deploying the .war file

#
#### Build the Front-End
---
> Install the following:

- NodeJS & NPM
- Bower
- Grunt

##### Install NodeJS
#
> **Recommended** - You can use Homebrew to install NodeJS.  This will isntall the latest version.

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

> **Optional**: Node Version Manager is a great tool to switch between multiple versions of NodeJS
    - https://github.com/creationix/nvm

To verify NVM installation:
```sh
$ nvm --version
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

> You should now have a successful build that can be deployed

#
#### Deployment
---
> Now that you have a successful build, you need to copy the app director into the Tomcat /webapps directory.  You can also setup a script to do this after making code changes.
> Refer to the Google Document: **SettingUpArgusDevandRunningEnvironment.docx** for further instructions to:
- Setup Tomcat
- Copy the .war file & web app folder
- Starting your Tomcat server via ./startup.sh

#
#### Notes:
---

> If you install other npm packages, make sure to **save** the package when isntalling
```sh
$ npm install some_new_package --save
or
$ bower install some_new_package --save
```
This will update the package.json or bower.json files, so that others can pull the latest and install the packages as well.

> No More VI editor!! Add the Sublime Text to your command line.  Your favorite text editor, such as Atom may also support the command line!

```sh
$ ln -s "/Applications/Sublime Text.app/Contents/SharedSupport/bin/subl" /usr/local/bin/sublime
```

UI Reference Implementation

> NOTE: Containers to which this SPA are deployed are recommended to add the 'X-Frame-Options: Deny' response header to prevent click-jacking.

