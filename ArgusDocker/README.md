ArgusDocker
=====

ArgusDocker contains docker-compose files to deploy a complete argus system.  Follow these steps
to deploy:

 1. Follow the build instructions for [`ArgusWeb`](https://github.com/salesforce/Argus/tree/develop/ArgusWeb) to install the NPM dependencies.
 1. Build the images using `mvn install`
 1. Bring up the environment using `docker-compose up -d`

More information about docker can be found at
 * docker: https://docs.docker.com/
   * Dockerfile reference: https://docs.docker.com/engine/reference/builder/
   * docker run reference: https://docs.docker.com/engine/reference/run/
 * docker-compose: https://docs.docker.com/compose/
   * docker-compose reference: https://docs.docker.com/compose/compose-file/

## simple
The simple deployment contains approximately the minimum services required for a full argus deployment:
 * opentsdb
   * The image chosen also includes HBase
   * Available after deployment at http://localhost:4242/
 * grafana
   * not actually required, but useful for troubleshooting the data in opentsdb
   * Available after deployment at http://localhost:3000/
 * redis
 * kafka
   * The image chosen also includes zookeeper
 * argus-web-services
   * The REST api for argus, running inside a tomcat container
   * Available after deployment at http://localhost:8081/argus/
   * The top level tomcat web.xml is overridden to include a CORS filter
 * argus-web
   * The web ui / frontend for argus
   * Available after deployment at http://localhost:8082/app/
 * argus-metrics-client
   * An instance of the argus-client configured to process metrics.  The argus-web-services adds the
   metrics to kafka, and the argus-metrics-client consumes them from kafka and stores them into
   opentsdb.

The simple deployment will use the images you have built locally with `mvn install`, or use images from
docker hub if you have not built anything locally.

## persistent
This uses a postgres database and has persistent volumes for opentsdb, etc.
