ArgusDocker
=====

ArgusDocker contains docker-compose files to deploy a complete argus system.  Follow these steps
to deploy:

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
 * grafana
   * not actually required, but useful for troubleshooting the data in opentsdb
 * redis
 * kafka
   * The image chosen also includes zookeeper
 * argus-web-services
   * The REST api for argus
 * argus-web
   * The web ui / frontend for argus
 * argus-metrics-client
   * An instance of the argus-client configured to process metrics.  The argus-web-services adds the
   metrics to kafka, and the argus-metrics-client consumes them from kafka and stores them into
   opentsdb.
