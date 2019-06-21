/* Copyright Salesforce 2002,2014 All Rights Reserved. **********************************************************************************************/
package com.salesforce.perfeng.akc;

import com.salesforce.perfeng.akc.consumer.ConsumerType;
import com.salesforce.perfeng.akc.exceptions.AKCException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;

public class AKCConfiguration {

	//~ Static fields/initializers *******************************************************************************************************************
    private static final Logger logger = LoggerFactory.getLogger(AKCConfiguration.class);
    private static final Properties CONFIGURATION;

    private static Properties readAkcConfiguration() {
        try (InputStream is = new FileInputStream(System.getProperty("akc.configuration"))) {
            Properties result = new Properties();
            result.load(is);
            return result;
        } catch (IOException fne) {
            logger.warn("akc.configuration file not found. Skipping...");
            return null;
        }
    }
    public static Properties getConfiguration() {
		return CONFIGURATION;
	}

	static {
        CONFIGURATION = new Properties();
        for (Parameter param : Parameter.values()) {
            CONFIGURATION.put(param.keyName, param.defaultValue);
        }

        Properties result = new Properties();
        try (InputStream is = new FileInputStream(System.getProperty("akc.common.configuration")))  {
            result.load(is);
            CONFIGURATION.putAll(result);
            if (StringUtils.isBlank(System.getProperty("akc.configuration"))) {
                throw new AKCException("no akc.configuration");
            }
            Properties akcProps = readAkcConfiguration();
            if (null != akcProps) {
                CONFIGURATION.putAll(akcProps);
            }
        } catch (AKCException ex) {
            logger.warn("akc.configuration file not found. Skipping...");
        } catch(FileNotFoundException fne) {
            throw new AKCException("AKC Common config file not found. Please specify the configuration file location using -Dakc.configuration=<path>.", fne);
        } catch (Exception ex) {
            String msg = "Some exception occured loading the Argus Kafka Consumer configuration. Please check the configuration.";
            throw new AKCException(msg, ex);
        }
        }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the value for a give parameter key.
     *
     * @param   key  The parameter key.
     *
     * @return  The parameter name.
     */
    public static String getParameter(Parameter key) {
        return CONFIGURATION.getProperty(key.keyName);
    }

    public static void setConsumerType(ConsumerType type) {
        CONFIGURATION.setProperty(Parameter.CONSUMER_TYPE.keyName, type.toString());
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Supported configuration parameters.
     *
     * @author  Colby Guan (colbert.guan@salesforce.com)
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Parameter {
        /** --OPTIONAL--, default consumer.type is METRICS. Other supported types are ANNOTATIONS, SCHEMA
         * This config option is overridden if another consumer type is specified from the command line */
        CONSUMER_TYPE("consumer.type", "METRICS"),
        /** --REQUIRED-- The topics to consume. Defaults to everything that graphite consumes:
         * com.salesforce.mandm.ajna.Metric.+  */
        TOPICS("topics", "com\\.salesforce\\.mandm\\.ajna\\.Metric\\..+"),
        /* Times to retry a push request to persistent store if the first attempt fails */
        RETRIES("retries", "3"),

        /** --REQUIRED-- The zookeeper connection string in the form hostname:port. Multiple connection strings
         * must be separated using a semicolon.*/
        BOOTSTRAP_SERVERS("bootstrap.servers", ""),

        KEY_DESERIALIZER("key.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer"),

        VALUE_DESERIALIZER("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer"),

        GROUP_ID("group.id", "argus-kafka-consumer"),

        ENABLE_AUTO_COMMIT("enable.auto.commit", "false"),
        MAX_POLL_RECORDS("max.poll.records", "500"),

        /* The batch size that objects should be sent to their respective Argus stores. eg. schema.batch.size=500 will submit schemas to Elasticsearch, 500 records at a time
         * A value of 0 will have the AjnaConsumerTask send the entire ConsumerRecords batch as one */
        METRICS_BATCH_SIZE("metrics.batch.size", "0"),
        SCHEMA_BATCH_SIZE("schema.batch.size", "0"),
        ANNOTATIONS_BATCH_SIZE("annotations.batch.size", "0"),

        MAX_ANNOTATION_SIZE_BYTES("annotations.maxsize.bytes", "2000"),

        /** The frequency in ms that the consumer offsets are committed to zookeeper.*/
        AUTO_COMMIT_INTERVAL_MS("auto.commit.interval.ms", "5000"),

        /** What to do when there is no initial offset in Kafka or if the current offset
         * does not exist any more on the server (e.g. because that data has been deleted):
         * 		earliest: automatically reset the offset to the earliest offset
         * 		latest: automatically reset the offset to the latest offset
         * 		none: throw exception to the consumer if no previous offset is found for the consumer's group
         * 	    custom: will start the consumer to consume from custom.start.time.epoch.ms and custom.stop.time.epoch.ms
         * 		anything else: throw exception to the consumer.
         */
        AUTO_OFFSET_RESET("auto.offset.reset", "latest"),

        /** default will honor whatever is set via auto.offset.reset
         * custom will activate seeking the consumer to custom.start.time.epoch.ms.
         * Can be set to "default" or "custom".
         */
        CUSTOM_OFFSET_SET("custom.offset.set", "default"),

        /** start_time setting for a catch up consumer being brought up
         * to fix a datalag issue. The consumer will automatically start consuming
         * from the offset in the kafka broker for all topics it is subscribed to, that
         * corresponds to this time. This setting is used to start consuming from a particular spot
         * in the kafka queue, instead of being limited to consuming from the "earliest" or "latest" offset.
         * This Setting defaults to "earliest". Setting expects an epoch timestamp. eg. 1547343278000
         * Will only take effect when auto.offset.reset is set to "custom".
         */
        CUSTOM_START_TIME_EPOCH_MS("custom.start.time.epoch.ms", "0"),

        /** stop_time setting for a catch up consumer being brought up
         * to fix a datalag issue. The consumers will automatically stop consuming
         * once they reach the offset that corresponds to the time specified.
         * Defaults to the clock time when the consumer was brought up.
         * Setting expects an epoch timestamp. (eg. 1547361278000)
         * Will only take effect when auto.offset.reset is set to "custom"
         */
        CUSTOM_STOP_TIME_EPOCH_MS("custom.stop.time.epoch.ms", Long.toString(Instant.now().toEpochMilli())),


        /** A comma separated list of regexes that can be used to filter out unwanted tags from Ajna metrics.*/
        AJNA_TAGS_BLACKLIST("ajna.tags.blacklist", ""),

        /** A comma separated list of strings that can be used to drop Ajna metrics based on scope.
         * Use explicit scope name, regexes aren't supported with this setting.
         */
        ARGUS_SCOPE_BLACKLIST("argus.scope.blacklist", ""),

        /** Whether or not to drop old metrics based on how old their timestamp is. Applies to metrics and schema consumers */
        ENABLE_MAX_METRICS_AGE("max.metrics.age.enabled", "true"),
        /** Default maximum age of 15 days */
        MAX_METRICS_AGE_MS("max.metrics.age.ms", "1296000000"),

        /*--------------------------------------------------------------------------------------------------------------------*/

        /** --REQUIRED-- The number of threads for AjnaConsumerTask to run */
        NUM_STREAMS("num.streams", "8"),

        /** A unique id for each consumer connecting to a particular zookeeper. This is appended to the hostname for generating
         * a uniqueId for this consumer. For e.g. 1 or 2**/
        ID("id", "1"),

        /** The server/ funnel vip to lookup schema so backward compatibility is maintained **/
        SCHEMA_LOOKUP_SERVER("schema.lookup.server",""),

        /** Timeout for schema lookup **/
        SCHEMA_LOOKUP_TIMEOUT_MS("schema.lookup.timeout.ms","10000"),

        /** On/Off Switch for Quota SubSystem **/
        QUOTA_SWITCH("quota.switch","off"),

        /** Quota Enforcement Group. All topics in the same group will be subject to a per group quota to be throttled **/
        QUOTA_GROUP("quota.group","groupMinus1"),

        /** persistence-unit name for quota service, specified in persistence.xml **/
        QUOTA_PERSISTENCE_UNIT("quota.persistence.unit","quota"),

        /** Switch to enable quota system on scope-level **/
        QUOTA_SCOPE_LEVEL_ENABLED("quota.scopelevel.enabled", "false"),

        /** Switch to enable quota throttling on group-level **/
        QUOTA_GROUP_THROTTLING_ENABLED("quota.throttling.enabled", "false"),

        /** Quota system global principal name **/
        QUOTA_GLOBAL_PRINCIPAL("quota.global.principal", "GLOBAL");

        private String keyName;
		private String defaultValue;

        private Parameter(String name, String defaultValue) {
            this.keyName = name;
            this.defaultValue = defaultValue;
        }

        public String getKeyName() {
			return keyName;
		}

    }

}
/* Copyright Salesforce 2002,2014 All Rights Reserved. **********************************************************************************************/
