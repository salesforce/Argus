ArgusOrchestra [![Build Status](https://travis-ci.org/salesforce/ArgusOrchestra.svg?branch=master)](https://travis-ci.org/salesforce/ArgusOrchestra) [![Coverage](https://codecov.io/gh/salesforce/ArgusOrchestra/branch/master/graph/badge.svg)](https://codecov.io/gh/salesforce/ArgusOrchestra) [![Static Analysis](https://scan.coverity.com/projects/9833/badge.svg)](https://scan.coverity.com/projects/salesforce-argusorchestra)
=====

ArgusOrchetra is a command line client used to extract metric and annotation data from Splunk and publish it to [Argus](https://github.com/SalesforceEng/Argus).

## Building ArgusOrchestra

### Running The Unit Tests

Nothing to out of the ordinary here.  Just clone the repository and execute the test goal.

```
mvn test
```

**Only the unit tests are run by `codecov.io` and as such, the coverage reported by it is significantly less than the coverage obtained by running the full test suite.**

### Running The Integration Tests

The integration tests for ArgusOrchestra require the specification of the Argus web service endpoint to run against, and the Splunk API web service endpoint as well.  Modify the *orchestra-build.properties* file to specify these as shown below.

```
argus.endpoint=http://argusws.mycompany.com:8080
argus.username=argususer
argus.password=S4l3sf0rc3R0ck5!
splunk.host=splunk.mycompany.com
splunk.port=8214
splunk.username=splunkuser
splunk.password=5plunk_15_k0oL_4l50
```

Once the modifications have been made run the complete suite of tests, including the integration tests.  The Splunk integration tests run some queries against Splunk builtin operational data, so they should just run.  If you find otherwise, let us know!

```
mvn verify
```

### Generating Coverage Reports

Coverage is calculated everytime tests are run.  In order to generate a coverage report just run the report generation target.

```
mvn jacoco:report
```

Coverage reports are generated in the `target/site/jacoco` directory.

### Running ArgusOrchestra

ArgusOrchestra can be invoked as any other Java jarfile.  Below is an example that displays the output of the help message.

```
%java -jar argus-orchestra-2.0-SNAPSHOT-jar-with-dependencies.jar -h

[ ORCHESTRA | 2016-08-09 20:28:48.084 | main                 | WARN  ] Could 
     not load Orchestra configuration.  Please specify the configuration file 
     location using -Dorchestra.configuration=<path>.

Usage:
	-h Display the usage and available collector types.
	-t Mandatory option indicating the name of the collector type to invoke.
	-l Optional log level.  Defaults to INFO.
	-s Optional timeout in seconds.  Defaults to 3600.
	-n Preview mode.  Writes output to stdout.

Available types (Refer to type specific API documentation for configuration options):
	SPLUNKNATIVE
```

As you can see from the output above, it complains about a missing configuration file.  The Orchestra client requires two configuration files.  The first is an orchestra.properties file whose location is specified by the Java property *orchestra.configuration*.  For example, 

```
-Dorchestra.configuration=myorchestra.properties.
```

The contents of this file tells orchestra what properties to use to connect to the Argus web service endpoint.  Those properties are listed below.

```
argusws.endpoint - The root endpoint for the web services including 
                   the protocol and port information.  For example, 
                   https://argus:443/argusws.
argusws.username - The username used to authenticate into the webservices with.
argusws.password - The password used to authenticate into the webservices with.
```

The other configuration file is used to specify the properties that drive the Splunk collection.  The location of the Splunk properties is specified by appending the string literal '.configuration' to the fully qualified class name of the collector class.  If you want to see extrememly detailed information about what was collected by Orchestra, be sure to invoke it with the *-l DEBUG* option.

```
-Dcom.salesforce.dva.orchestra.domain.splunk.SplunkNativeReader.configuration=splunk.properties
```

The contents of this file tells Orchestra what properties to use invoking Splunk.  Those properties are listed below.

```
username    - The username used to authenticate into Splunk with.
password    - The password used to authenticate into Splunk with.
timeout_sec - The number of seconds after which collection will timeout.
timestamp   - The name of the result set field used to capture the metric 
              timestamp.  This field must be in the format of 
              '%m/%d/%Y %H:%M:%S'
query       - The query to use.  Java parameter substitution placeholders 
              are supported ( {0}, {1}, etc... )
param.<%d>  - One or more parameters to be substituted when expanding the 
              query template.  For example, param.0, param.1, etc...  The 
              query template is expanded across the parameter value index, 
              so all parameters must have the same number of values.  
              Parameters are not expanded as a cross product.  If your 
              parameters have 5 values, the collector will run 5 queries, 
              where the first uses the 1st set of parameter values, the 
              second using the 2nd, and so on.
metric.<%s> - One or more metric name mappings.  The portion of the key 
              following the period is used as the metric name in Argus.  The 
              value of the parameter corresponds to the result set column 
              name from which the value will be extracted.
key.<%d>    - One or more result set value substitutions.  If you want to use 
              the value of a result set field in the scope of the metric, 
              you'd specify the column name from which the value will be 
              extracted.  The value of the key for any given row value can be 
              substituted in the scope property.
scope       - The scope to be used to categorize the metric.  This is a string 
              literal that may contain one or more key substitutions of the 
              form $key.0$, for example.
```

### Sample Metric Collection Splunk Properties
The example below uses the same configuration as the metric collection integration test.  It collects querycount and linecount from the Splunk _audit index for the trailing 30 minutes (in 10 minute buckets) and groups the results by Splunk server.

```
host=splunk.mycompany.com
port=4848
username=splunkuser
password=12345
key.0=index
metric.querycount=querycount
metric.linecount=linecount
metricformat=$metric$
param.0="_audit"
query=search earliest=-30m@m index={0} | bucket_time span=10m | eval time=strftime(_time, "%m/%d/%Y %H:%M:%S") | stats count as querycount, sum(linecount) as linecount by time, index, splunk_server
scope=$key.0$
tag.server=splunk_server
tagformat=$tag$
timeout_sec=500
timestamp=time
```

### Sample Annotation Collection Splunk Properties
The example below uses the same configuration as the annotation (event) collection integration test.  It collects querycount and linecount from the Splunk _audit index for the trailing 30 minutes (in 10 minute buckets) and groups the results by Splunk server.  It's essentially the same as the metric collection, but instead of storing the result as metrics, it stores them as event annotations in Argus.  The annotations are stored with type *opsdata*, scope name of *_audit*, an event ID field that corresponds to the Splunk server, and a metric named *querycounts*.  The two event fields stored on the event are derived from the *metric.<%s>* parameters that are specified.

```
host=splunk.mycompany.com
port=4848
username=splunkuser
password=12345
key.0=index
metric.querycount=querycount
metric.linecount=linecount
param.0="_audit"
query=search earliest=-30m@m index={0} | bucket_time span=10m | eval time=strftime(_time, "%m/%d/%Y %H:%M:%S") | stats count as querycount, sum(linecount) as linecount by time, index, splunk_server
scope=$key.0$
timeout_sec=500
timestamp=time
annotation_collection=true
annotation_type=opsdata
annotation_metricname=querycounts
annotation_id_field=splunk_server
```

### Splunk Configuration Reference
If you want to get into the nitty gritty of all the options the Splunk collector has, be sure to check out the JavaDoc for it, or look at the [source file] (https://github.com/salesforce/ArgusOrchestra/blob/master/src/main/java/com/salesforce/dva/orchestra/domain/splunk/SplunkConfiguration.java).
