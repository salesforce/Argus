ArgusWebServices
================
ArgusWebServices exposes the Argus REST web services.  Issuing a GET request to the /help endpoint produces a description of the available service endpoints.  Issuing a GET request to the /\<service\>/help endpoint produces a detailed description of all of the methods that are available on the service endpoint.

## Embedded Tomcat

This project can be started using an embedded Tomcat server:
`java -jar target/argus-webservices-4.0-SNAPSHOT-war-exec.jar`

More details about commandline arguments can be found on the 
[Official page of the maven plugin](http://tomcat.apache.org/maven-plugin-2.1/executable-war-jar.html#Generated_executable_jarwar)

## More information about this project

To find out more [see the wiki.](https://github.com/SalesforceEng/Argus/wiki)
