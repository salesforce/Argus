[![Build Status](https://dva-ci.internal.salesforce.com/job/ArgusMonitoring/job/ArgusKafkaConsumer/job/master/badge/icon)](https://dva-ci.internal.salesforce.com/job/ArgusMonitoring/job/ArgusKafkaConsumer/job/master/)

[Sonarqube Code Analysis](https://strata-sonarqube.eng.sfdc.net/dashboard?id=ArgusMonitoring.ArgusKafkaConsumer.master)

# ArgusKafkaConsumer


## To build the repo:
```
mvn -DskipTests -DskipDockerBuild clean install
```

## Development Process
1. Fork this repo
1. Change/Commit to the fork
1. Create Pull Request
1. Make sure all automated tests have passed, and SonarQube analysis is GREEN
1. Request Team Members to Review your change
1. Once Approved, a Team Member will merge the change

## Release Process
1. Fork this repo
1. In your repo, change the release to the next SNAPSHOT version:
```mvn -DnewVersion=3.1.4-SNAPSHOT versions:set```
1. Create a Pull Request
1. Once all tests have passed, merge the change
1. Let Jenkins build/release the SNAPSHOT to Nexus
1. DONE
