package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.MetricStorageService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.perfeng.akc.exceptions.AKCException;
import org.apache.kafka.clients.consumer.CommitFailedException;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndTimestamp;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.salesforce.perfeng.akc.consumer.InstrumentationService.METRIC_CONSUMER_LAG;


public class AmurKafkaRunner<K extends Serializable, V extends Serializable> implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(AmurKafkaRunner.class);
    private static final Map<ConsumerType, String> OVERALL_LATENCY_METRIC_NAMES = ImmutableMap.<ConsumerType, String>of(
            ConsumerType.METRICS, InstrumentationService.METRIC_OVERALL_LATENCY,
            ConsumerType.SCHEMA, InstrumentationService.SCHEMA_OVERALL_LATENCY,
            ConsumerType.ANNOTATIONS, InstrumentationService.ANNOTATIONS_OVERALL_LATENCY
    );
    private static final Map<ConsumerType, String> POLL_LATENCY_METRIC_NAMES = ImmutableMap.<ConsumerType, String>of(
            ConsumerType.METRICS, InstrumentationService.METRIC_POLL_LATENCY,
            ConsumerType.SCHEMA, InstrumentationService.SCHEMA_POLL_LATENCY,
            ConsumerType.ANNOTATIONS, InstrumentationService.ANNOTATIONS_POLL_LATENCY
    );
    private static final Map<ConsumerType, String> HANDLEBATCH_LATENCY_METRIC_NAMES = ImmutableMap.<ConsumerType, String>of(
            ConsumerType.METRICS, InstrumentationService.METRIC_HANDLEBATCH_LATENCY,
            ConsumerType.SCHEMA, InstrumentationService.SCHEMA_HANDLEBATCH_LATENCY,
            ConsumerType.ANNOTATIONS, InstrumentationService.ANNOTATIONS_HANDLEBATCH_LATENCY
    );

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private KafkaConsumer<K, V> consumer;
    private final ConsumerType consumerType;
    private final Properties kafkaProps;
    private final SystemMain system;
    private AmurSinkTask amurSinkTask = null;
    private Class amurTaskClass;
    private String topics;
    private InstrumentationService instrumentationService;
    private TSDBService tsdbService;
    private SchemaService schemaService;
    private AnnotationStorageService annotationStorageService;
    private MetricStorageService consumerOffsetMetricStorageService;
    private long consumptionStartTime;
    private long consumptionStopTime;
    private Map<TopicPartition,Long> stopOffsets = new HashMap<>();
    private String CATCHUP_CONSUMER_CURRENT_OFFSET = "catchup.consumer.current.offset";
    private String CATCHUP_CONSUMER_START_OFFSET = "catchup.consumer.start.offset";
    private String CATCHUP_CONSUMER_STOP_OFFSET = "catchup.consumer.stop.offset";

    private Set<TopicPartition> partitionsSubscribed = new HashSet<>();
    private String CUSTOM_CATCHUP_CONSUMER = new String("custom");

    private static final String[] mandatoryKafkaProps = { "topics", "bootstrap.servers", "key.deserializer",
            "value.deserializer", "group.id" };

    private static ThreadLocal<Map<MetricName, ? extends org.apache.kafka.common.Metric>> threadLocalKafkaMetrics =
            ThreadLocal.withInitial(()-> new HashMap<>());
    private String groupId;

    public static Map<MetricName, ? extends org.apache.kafka.common.Metric> getKafkaConsumerMetrics() {
        return threadLocalKafkaMetrics.get();
    }

    /**
     * Create a KafkaConsumer manager that runs a handleBatch() callback
     * @param kafkaProps    Properties to start the Consumer with
     * @param system        An initialized Argus system
     * @param jobCounter    Counter tracking the number of completed tasks
     * @param amurTaskClass AmurSinkTask to run on each poll
     */
    public AmurKafkaRunner(ConsumerType ct, Properties kafkaProps, SystemMain system, AtomicInteger jobCounter, Class amurTaskClass) {
        logger.info("Starting AmurKafkaRunner for taskClass=" + amurTaskClass.getName());
        verifyParams(kafkaProps);
        this.consumerType = ct;
        this.kafkaProps = kafkaProps;
        this.system = system;
        this.topics = this.kafkaProps.getProperty("topics").replaceAll(",", "|");
        this.groupId = AKCConfiguration.getParameter(AKCConfiguration.Parameter.GROUP_ID);
        if (!AmurSinkTask.class.isAssignableFrom(amurTaskClass)) {
            logger.error("EXITING since invalid task class passed, passedclass=" + amurTaskClass);
            System.exit(2);
        }
        this.amurTaskClass = amurTaskClass;
        if(isCustomCatchupConsumer()) {
            logger.info("Recognized that this consumer wants to be started at custom offsets");
            try {
                this.consumptionStartTime = Long.parseLong(this.kafkaProps.getProperty(AKCConfiguration.Parameter.CUSTOM_START_TIME_EPOCH_MS.getKeyName()));
                this.consumptionStopTime = Long.parseLong(this.kafkaProps.getProperty(AKCConfiguration.Parameter.CUSTOM_STOP_TIME_EPOCH_MS.getKeyName()));

                logger.info(" Setting the Start and End time for consumption to " + toHumanDate(consumptionStartTime) + " GMT  and" + toHumanDate(consumptionStopTime) + " GMT");
                if(consumptionStartTime < 0 || consumptionStopTime < 0 || consumptionStartTime > consumptionStopTime) {
                    throw new IllegalArgumentException("Invalid Start time " + consumptionStartTime + " or Stop time " + consumptionStopTime + " specified, or Start time is later than stop time");
                }
                if (consumptionStartTime > Instant.now().toEpochMilli()) {
                    throw new IllegalArgumentException("Start time "+ consumptionStartTime +" for a catchup consumer cannot be in the future");
                }

            } catch (NumberFormatException e) {
                logger.error("Start timestamp '{}' Or End timestamp '{}' provided via custom.start.time.epoch.ms or custom.stop.time.epoch.ms is invalid", consumptionStartTime, consumptionStopTime);
                throw e;
            }
        }
    }

    private void verifyParams(Properties kafkaProps) {
        for (String prop : mandatoryKafkaProps) {
            if (null == kafkaProps.getProperty(prop)) {
                logger.error("Mandatory property not present: " + prop);
                System.exit(2);
            }
        }
    }


    private String toHumanDate(Long epoch_time) {
        Date date = new Date(epoch_time);
        DateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String formatted = format.format(date);
        return formatted;
    }

    private boolean isCustomCatchupConsumer(){
        return CUSTOM_CATCHUP_CONSUMER.equals(this.kafkaProps.getProperty(AKCConfiguration.Parameter.CUSTOM_OFFSET_SET.getKeyName()));
    }

    private void setCustomStartOffset(TopicPartition tp){

        logger.info("Going to commence Seeking the offset for topic partition {}" +
                " to consumption start time of {} ", tp, consumptionStartTime);

        Map<TopicPartition, Long> toSearch = new HashMap<>();
        toSearch.put(tp, consumptionStartTime);

        // Get the offsets corresponding to consumptionStartTime from Kafka broker.
        Map<TopicPartition, OffsetAndTimestamp> brokerOffsetMap = consumer.offsetsForTimes(toSearch);

        if(brokerOffsetMap == null)
            throw new AKCException("No suitable offsets found for the topic with a newer timestamp than start time "+ consumptionStartTime +" for starting a catchup consumer");

        brokerOffsetMap.forEach((topicPartition, startTimeOffsets) -> {
            try {

                Long customStartOffset = startTimeOffsets.offset();
                consumer.seek(topicPartition, customStartOffset);

                Map<String,String> tags = new HashMap<>();
                tags.put("topic", topicPartition.topic());
                tags.put("partition", String.valueOf(topicPartition.partition()));
                instrumentationService.updateCounter(CATCHUP_CONSUMER_START_OFFSET,customStartOffset,tags);

                logger.info("Seeking partition {} of the topic {} to offset {} for timestamp {} ",
                        topicPartition.partition(),
                        topicPartition.topic(), customStartOffset, consumptionStartTime);

            } catch (IllegalArgumentException e) {
                logger.error("Illegal arguments for seek for the custom timestamps for consumer");
                throw e;
            }
        });
    }

    private void getCustomStopOffsets(Set<TopicPartition> partitions) {

        Map<TopicPartition, Long> toSearch = new HashMap<>();
        for(TopicPartition tp : partitions) {
            toSearch.put(tp, consumptionStopTime);
        }


        // Get the offsets corresponding to consumptionStartTime from Kafka broker.
        Map<TopicPartition, OffsetAndTimestamp> offsetMap = consumer.offsetsForTimes(toSearch);

        if (offsetMap == null)
            throw new AKCException("No suitable offsets found for the topic for consumption to stop around the stop time "+ consumptionStopTime +" for starting a catchup consumer");

        offsetMap.forEach((tp,partitionOffsets) -> {
            stopOffsets.put(tp, partitionOffsets.offset());
        });
    }

    @Override
    public void run() {
        this.consumer = new KafkaConsumer<>(this.kafkaProps);
        this.amurSinkTask = (AmurSinkTask) Utils.newInstance(this.amurTaskClass);
        this.tsdbService = this.system.getServiceFactory().getTSDBService();
        this.schemaService = this.system.getServiceFactory().getSchemaService();
        this.annotationStorageService = this.system.getServiceFactory().getAnnotationStorageService();
        this.consumerOffsetMetricStorageService = this.system.getServiceFactory().getConsumerOffsetMetricStorageService();
        this.instrumentationService = InstrumentationService.getInstance(tsdbService, consumerOffsetMetricStorageService);

        if (null != amurSinkTask) {
            threadLocalKafkaMetrics.set(consumer.metrics());//must be before amurSinkTask.init()
            this.amurSinkTask.init(tsdbService,
                    schemaService,
                    annotationStorageService,
                    instrumentationService,
                    QuotaUtilFactory.getBlacklistService());
            Pattern p = getRegexPattern();
            consumer.subscribe(p, new ConsumerRebalanceListener() {
                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                    for (TopicPartition tp: partitions) {
                        logger.info("Partitions revoked for topic=" + tp.topic() + " and partition=" + tp.partition());
                        partitionsSubscribed.remove(tp);
                    }
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {

                    partitionsSubscribed.addAll(partitions);
                    if (isCustomCatchupConsumer()) {
                        getCustomStopOffsets(partitionsSubscribed);
                        Map<TopicPartition, Long> earliestOffsetsAtBroker = consumer.beginningOffsets(partitions);
                        Map<TopicPartition, Long> latestOffsetAtBroker = consumer.endOffsets(partitions);

                        for (TopicPartition tp : partitions) {
                            logger.info("Partitions assigned for topic=" + tp.topic() + " and partition=" + tp.partition());
                            Long offsetAtBroker = consumer.position(tp);

                            logger.info("For Topic" + tp.topic() + " Partition" + tp.partition() + " offsetAtBroker:" + offsetAtBroker + " earliestOffsetAtBroker:" + earliestOffsetsAtBroker.get(tp) + "latestOffsetAtBroker:" + latestOffsetAtBroker.get(tp));
                            boolean existing_consumer = false;
                            if (offsetAtBroker < latestOffsetAtBroker.get(tp) && offsetAtBroker > earliestOffsetsAtBroker.get(tp)) {
                                existing_consumer = true;
                            }

                            if(!existing_consumer)
                                setCustomStartOffset(tp);
                            else
                                logger.info("Not seeking topic {} partition {} since broker is already at offset {}, and earliest is {} latest is {}", tp.topic(), tp.partition(), offsetAtBroker, earliestOffsetsAtBroker, latestOffsetAtBroker);
                        }
                    } else {
                        for (TopicPartition tp : partitions)
                            logger.info("Partitions assigned for topic=" + tp.topic() + " and partition=" + tp.partition());
                    }
                }
            });

            while (!closed.get()) {
                double t0 = System.currentTimeMillis();
                try {
                    ConsumerRecords<K, V> records = consumer.poll(Long.MAX_VALUE);

                    double t1 = System.currentTimeMillis();
                    instrumentationService.updateTimer(POLL_LATENCY_METRIC_NAMES.get(consumerType),
                            t1 - t0,
                            null);

                    this.amurSinkTask.handleBatch(records);
                    instrumentationService.updateTimer(HANDLEBATCH_LATENCY_METRIC_NAMES.get(consumerType),
                            System.currentTimeMillis() - t1,
                            null);

                    computeAndPushLagOffsetPerPartitionPerTopic();
                    this.consumer.commitSync();



                    if(isCustomCatchupConsumer()) {
                        for (TopicPartition tp : partitionsSubscribed) {

                            Long currentOffset = consumer.position(tp);
                            Map<String,String> tags = new HashMap<>();
                            tags.put("topic", tp.topic());
                            tags.put("partition", String.valueOf(tp.partition()));
                            instrumentationService.updateCounter(CATCHUP_CONSUMER_CURRENT_OFFSET,currentOffset,tags);
                            instrumentationService.updateCounter(CATCHUP_CONSUMER_STOP_OFFSET, stopOffsets.get(tp),tags);
                            logger.debug(" Topic {} Partition {} has been consumed upto {} and stopOffset is {} ", tp.topic(), tp.partition(), currentOffset, stopOffsets.get(tp));

                            if (currentOffset >= stopOffsets.get(tp)) {
                                consumer.pause(Collections.singleton(tp));
                                partitionsSubscribed.remove(tp);
                                logger.info("Partition " + tp.partition() + " for topic:" + tp.topic() + " is already at offset:"
                                        + consumer.position(tp) + " and stopping consumption because it exceeds:" + stopOffsets.get(tp));
                            }
                        }

                        // Once all the partitions for this consumer have been paused, shut it down!
                        if (partitionsSubscribed.isEmpty()) {
                            logger.info("Stopping the consumers because all partitions have reached their Stop timestamps");
                            this.shutdown();
                        }
                    }


                } catch (WakeupException e) {
                    // Ignore exception if closing
                    if (closed.get()) {
                        logger.info("Received expected consumer wakeup from shutdown");
                        break;
                    } else {
                        logger.warn("Received unexpected wakeup exception:");
                        throw e;
                    }
                } catch (CommitFailedException e) {
                    logger.error("Commit failed, continuing polls: ", e) ;
                } catch (AKCException e) {
                    logger.error("Error in AKC consumer: ", e);
                    break;
                }
                catch (Exception e) {
                    logger.error("Unexpected error during last poll:", e);
                    e.printStackTrace();
                } finally {
                    instrumentationService.updateTimer(OVERALL_LATENCY_METRIC_NAMES.get(consumerType),
                            ((double)System.currentTimeMillis() - t0),
                            null);
                }
            }
            logger.info("AmurKafkaRunner has EXITED poll loop");
            consumer.close();
        } else {
            logger.error("Unknown task class passed, exiting");
            System.exit(2);
        }
    }

    private void computeAndPushLagOffsetPerPartitionPerTopic() {

        if (ConsumerType.METRICS != this.consumerType) { // Only compute and push for metrics consumer type.
            return;
        }

        Map<TopicPartition, Long> latestOffsetAtBroker = consumer.endOffsets(partitionsSubscribed);
        latestOffsetAtBroker.forEach((tp, latestOffset) -> {
            Long currentLag = latestOffset - consumer.position(tp);
            Map<String,String> tags = new HashMap<>();
            tags.put("topic", tp.topic());
            tags.put("partition", String.valueOf(tp.partition()));
            tags.put("groupId", groupId);

            instrumentationService.setGaugeValue(METRIC_CONSUMER_LAG, Double.valueOf(currentLag), tags);
            logger.debug("Topic: {}, Partition: {} has lag of {}", tp.topic(), tp.partition(), currentLag);
        });
    }

    private Pattern getRegexPattern() {
        Pattern p = null;
        try{
            p = Pattern.compile(this.topics);
        } catch(PatternSyntaxException e) {
            logger.error("Irregular regular expression entered");
            System.exit(2);
        }
        return p;
    }

    public void shutdown() {
        closed.set(true);
        consumer.wakeup();
        this.amurSinkTask.shutdownTask();
    }
}
