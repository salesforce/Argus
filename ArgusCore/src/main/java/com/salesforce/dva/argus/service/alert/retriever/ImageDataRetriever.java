package com.salesforce.dva.argus.service.alert.retriever;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.ImagePoints;
import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.ImageService;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.salesforce.dva.argus.entity.ImageProperties.ImageColors;
import static com.salesforce.dva.argus.service.AlertService.Notifier.NotificationStatus;

public class ImageDataRetriever {
    private static final Logger logger = LoggerFactory.getLogger(ImageDataRetriever.class);

    private ImageService imageService;
    protected final SystemConfiguration config;

    @Inject
    ImageDataRetriever(ImageService imageService, SystemConfiguration systemConfiguration) {
        this.imageService = imageService;
        this.config = systemConfiguration;
    }

    public String getImageURL(final Pair<String, byte[]> imageDetails) {
        if (imageDetails == null) {
            logger.error("Cannot fetch the Image URL if the details of the image is not present");
            return null;
        }
        String imageID = imageDetails.getLeft();
        if (imageID == null) {
            logger.error("Cannot fetch the Image URL if the image ID is not present");
            return null;
        }
        String template = config.getValue(AuditNotifier.Property.AUDIT_METRIC_IMAGE_URL_TEMPLATE.getName(),
                AuditNotifier.Property.AUDIT_METRIC_IMAGE_URL_TEMPLATE.getDefaultValue());
        return template.replaceAll("\\$imageID\\$", imageID);
    }

    public Pair<String, byte[]> getAnnotatedImage(final ImageDataRetrievalContext context) {
        if (context == null) {
            throw new IllegalArgumentException("The image data retrieval context cannot be null; The" +
                    " parameters are is required to fetch the image ");
        }

        ImageProperties imageProperties = new ImageProperties();
        String chartName = context.getTriggeredMetric().getDisplayName();
        if (!Strings.isNullOrEmpty(chartName)) {
            imageProperties.setChartName(chartName);
        }

        if (context.getNotificationStatus() == NotificationStatus.TRIGGERED) {
            Long triggerFiredTime = context.getTriggerFiredTime()
                    .orElseGet(() -> {
                        logger.error("Trigger fired time is required to generate the image for alert ID "+
                                context.getAlert().getId() +". Defaulting to use the current timestamp");
                        return System.currentTimeMillis();
                    });
            imageProperties.setShadeYAxisArea(getYAxisCoordinates(triggerFiredTime, context.getTrigger().getInertia()));
            imageProperties.setShadeXAxisArea(getXAxisCoordinates(context.getTrigger()));
        }

        return imageService.generateAndStoreImage(ImmutableList.of(context.getTriggeredMetric()), imageProperties, true);
    }

    private List<ImagePoints> getXAxisCoordinates(final Trigger trigger) {
        ImagePoints dataPoints1, dataPoints2;
        Double threshold = trigger.getThreshold();
        String label;
        Double maxPoint, minPoint, secondaryThreshold;

        switch (trigger.getType()) {
            case GREATER_THAN:
                Double boundary = Double.sum(threshold, 1);
                label = "value > "+ threshold;
                dataPoints1 = new ImagePoints(boundary, Double.MAX_VALUE, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1);
            case GREATER_THAN_OR_EQ:
                label = "value >= "+ threshold;
                dataPoints1 = new ImagePoints(threshold, Double.MAX_VALUE, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1);
            case LESS_THAN:
                label = "value < "+ threshold;
                dataPoints1 = new ImagePoints(Double.MIN_VALUE, threshold-1, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1);
            case LESS_THAN_OR_EQ:
                label = "value <= "+ threshold;
                dataPoints1 = new ImagePoints(Double.MIN_VALUE, threshold, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1);
            case EQUAL:
                label = "value = "+ threshold;
                dataPoints1 = new ImagePoints(threshold, threshold, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1);
            case NOT_EQUAL:
                label = "value != "+ threshold;
                dataPoints1 = new ImagePoints(Double.MIN_VALUE, threshold - 1, label, ImageColors.VERY_LIGHT_PINK);
                dataPoints2 = new ImagePoints(Double.sum(threshold, 1), Double.MAX_VALUE, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1, dataPoints2);
            case BETWEEN:
                secondaryThreshold = trigger.getSecondaryThreshold();
                maxPoint = Math.max(threshold, secondaryThreshold);
                minPoint = Math.min(threshold, secondaryThreshold);
                label = minPoint + " <= value <= "+ maxPoint;
                dataPoints1 = new ImagePoints(minPoint, maxPoint, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1);
            case NOT_BETWEEN:
                secondaryThreshold = trigger.getSecondaryThreshold();
                maxPoint = Math.max(threshold, secondaryThreshold);
                minPoint = Math.min(threshold, secondaryThreshold);
                label = "value >= "+ maxPoint + " or value <= " + minPoint;
                dataPoints1 = new ImagePoints(Double.MIN_VALUE, minPoint, ImageColors.VERY_LIGHT_PINK);
                dataPoints2 = new ImagePoints(maxPoint, Double.MAX_VALUE, label, ImageColors.VERY_LIGHT_PINK);
                return ImmutableList.of(dataPoints1, dataPoints2);
            case NO_DATA:
                return ImmutableList.of();
            default:
                throw new SystemException("Unsupported trigger type " + trigger.getType());
        }
    }

    private List<ImagePoints> getYAxisCoordinates(final Long triggerFiredTime, final Long inertia) {
        Long shadeStartPoint = triggerFiredTime - inertia;

        String label = "Inertia(seconds)=" + ((double)inertia/1000L) * 100;
        ImagePoints dataPoints = new ImagePoints(shadeStartPoint, triggerFiredTime, label, ImageColors.VERY_LIGHT_PINK);
        return ImmutableList.of(dataPoints);
    }

}
