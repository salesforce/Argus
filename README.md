Argus  [![Build Status](https://travis-ci.com/SalesforceEng/Argus.svg?token=oGrB7zUSh9VsE9p5yapV&branch=master)](https://travis-ci.com/SalesforceEng/Argus) [![codecov.io](https://codecov.io/github/SalesforceEng/Argus/coverage.svg?token=wkwA144Mqa&branch=master)](https://codecov.io/github/SalesforceEng/Argus?branch=master)
=====

Argus is a time series monitoring and alerting platform.  It consists of discrete services to configure alerts, ingest and transform metrics & events, send notifications, create namespaces, and to both establish and enforce policies and quotas for usage.

It's architecture allows any and all of these services to retargeted to new technology as it becomes available with little to no impact on the users.

To find out more [see the wiki.](https://github.com/SalesforceEng/Argus/wiki)

![Argus UI](https://cloud.githubusercontent.com/assets/15337203/12775758/53f98b02-ca05-11e5-88b0-1fd11afe335f.png)

## Build process

### First time after you clone the project

The project uses resource filtering and the parent project installs the resource templates into the local mvn repository and those resources are downloaded by the sub projects and then they have the filter applied.

Once the resources are installed, it should be able to run all other targets normally.  

You can do: `mvn -DskipTests=true --non-recursive install` on the parent project.