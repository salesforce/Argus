package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.mandm.avro.util.AjnaWireFormatDecoder;
import com.salesforce.mandm.avro.util.AvroUtils;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_BLOCKED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_CONSUMED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_DROPPED_TOOLARGE;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_POSTED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.ANNOTATIONS_PROCESS_LATENCY;

class AnnotationConsumer extends BaseConsumer {
    private static final Logger logger = LoggerFactory.getLogger(AnnotationConsumer.class);
    private static final int ANNOTATIONS_BATCH_SIZE = Integer.parseInt(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ANNOTATIONS_BATCH_SIZE));
    private static final int MAX_ANNOTATION_SIZE_BYTES = Integer.parseInt(AKCConfiguration.getParameter(AKCConfiguration.Parameter.MAX_ANNOTATION_SIZE_BYTES));
    static final String ANNOTATION_SCHEMA_FINGERPRINT = AvroUtils.getSchemaFingerprint(com.salesforce.mandm.ajna.Annotation.getClassSchema());

    AjnaWireFormatDecoder<com.salesforce.mandm.ajna.Annotation> annotationAvroDecoder;
    private TSDBService tsdbService;

    AnnotationConsumer(TSDBService tsdbService, InstrumentationService instrumentationService, IBlacklistService blacklistService) {
        super(instrumentationService, blacklistService);
        annotationAvroDecoder = new AjnaWireFormatDecoder<>();
        this.tsdbService = tsdbService;
    }

    void processAjnaAnnotationKafkaRecords(ConsumerRecords<byte[], byte[]> records, List<Annotation> argusAnnotations) {
        for(ConsumerRecord<byte[], byte[]> record : records) {
            Map<String, String> dpTags = new HashMap<>();
            dpTags.put("topic", record.topic());
            List<com.salesforce.mandm.ajna.Annotation> ajnaAnnotations = null;
            try {
                ajnaAnnotations = annotationAvroDecoder.listFromBytes(record.value(), ANNOTATION_SCHEMA_FINGERPRINT);
                instrumentationService.updateCounter(ANNOTATIONS_CONSUMED, ajnaAnnotations.size(), dpTags);
                for (com.salesforce.mandm.ajna.Annotation ajnaAnnotation : ajnaAnnotations) {
                    Annotation annotation = transformToArgusAnnotation(ajnaAnnotation);
                    if (annotation != null) {
                        boolean isSafe = isAnnotationSizeSafe(annotation);
                        if (isSafe) {
                            argusAnnotations.add(annotation);
                        } else {
                            logger.warn(String.format("Dropping isSizeSafe=%b, annotation=%s", isSafe, annotation));
                            Map<String, String> tags = new HashMap<>(dpTags);
                            tags.put(SchemaField.SOURCE, annotation.getSource());
                            instrumentationService.updateCounter(ANNOTATIONS_DROPPED_TOOLARGE, 1, tags);
                        }
                    } else {
                        instrumentationService.updateCounter(ANNOTATIONS_DROPPED,1, dpTags);
                    }
                }
            } catch (Exception e) {
                if (ajnaAnnotations != null) {
                    instrumentationService.updateCounter(ANNOTATIONS_DROPPED, ajnaAnnotations.size(), dpTags);
                }
                logger.warn("Exception while decoding message from bytes. Dropping this ajna annotation", e);
            }
        }
        if (argusAnnotations.isEmpty()) {
            return;
        }
        int batches = processInBatches(argusAnnotations, ANNOTATIONS_BATCH_SIZE, this::processArgusAnnotations);
        instrumentationService.updateCounter(ANNOTATIONS_BATCH_COUNT, batches, null);
    }

    private void processArgusAnnotations(List<Annotation> annotations) {
        int size = annotations.size();
        double start = System.currentTimeMillis();
        try {
            tsdbService.putAnnotations(annotations);
            instrumentationService.updateCounter(ANNOTATIONS_POSTED, size, null);
        } catch (Exception ex) {
            logger.warn("Retrying annotation commit due to exception " + ex);
            if (retryWithExponentialBackoff(annotations, tsdbService::putAnnotations)) {
                instrumentationService.updateCounter(ANNOTATIONS_POSTED, size, null);
            } else {
                instrumentationService.updateCounter(ANNOTATIONS_DROPPED, size, null);
            }
        } finally {
            instrumentationService.updateTimer(ANNOTATIONS_PROCESS_LATENCY,
                    ((double)System.currentTimeMillis()-start),
                    null);
        }
    }

    Annotation transformToArgusAnnotation(com.salesforce.mandm.ajna.Annotation ajnaAnnotation) {
        logger.trace("Consumed Annotation: " + ajnaAnnotation.toString());

        try {
            String scopeSB = replaceUnsupportedChars(ajnaAnnotation.getScope().toString());
            //create Argus annotation.
            Annotation argusAnnotation = new Annotation(ajnaAnnotation.getSource().toString(),
                    ajnaAnnotation.getId().toString(),
                    ajnaAnnotation.getType().toString(),
                    scopeSB,
                    replaceUnsupportedChars(ajnaAnnotation.getRelatedMetric().toString()),
                    ajnaAnnotation.getTimestamp());

            if(blacklistService.isBlacklistedAnnotationScope(scopeSB)) {
                logger.trace("Encountered a blacklisted scope: " + scopeSB +  ". Dropping..." + argusAnnotation);
                Map<String,String> droppedScopes = new HashMap<>();
                droppedScopes.put("scope", scopeSB);
                instrumentationService.updateCounter(ANNOTATIONS_BLOCKED, 1, droppedScopes);
                return null;
            }
            //set tags
            extractAjnaTags(ajnaAnnotation.getTags(), argusAnnotation);

            Map<String, String> argusFields = new HashMap<>();
            for(Map.Entry<CharSequence, CharSequence> entry : ajnaAnnotation.getFields().entrySet()) {
                argusFields.put(entry.getKey().toString(), entry.getValue().toString());
            }
            argusAnnotation.setFields(argusFields);

            return argusAnnotation;

        } catch(Exception e) {
            logger.warn("Failed to transform Ajna Annotation to Argus Annotation. Dropping this ajna annotation: " + ajnaAnnotation, e);
            return null;
        }
    }

    static boolean isAnnotationSizeSafe(Annotation a) {
        return a == null || a.computeSizeBytes() <= MAX_ANNOTATION_SIZE_BYTES;
    }


}
