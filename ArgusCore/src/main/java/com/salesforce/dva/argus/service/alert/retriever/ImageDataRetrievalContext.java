package com.salesforce.dva.argus.service.alert.retriever;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Trigger;

import java.util.Optional;

import static com.salesforce.dva.argus.service.AlertService.Notifier.NotificationStatus;

public class ImageDataRetrievalContext {

    private Alert alert;
    private Trigger trigger;
    private Long triggerFiredTime;
    private Metric triggeredMetric;

    private NotificationStatus notificationStatus;

    public ImageDataRetrievalContext(Alert alert, Trigger trigger, Long triggerFiredTime, Metric triggeredMetric, NotificationStatus notificationStatus) {
        this.alert = alert;
        this.trigger = trigger;
        this.triggerFiredTime = triggerFiredTime;
        this.triggeredMetric = triggeredMetric;
        this.notificationStatus = notificationStatus;
    }

    public ImageDataRetrievalContext(Alert alert, Trigger trigger, Metric triggeredMetric, NotificationStatus notificationStatus) {
        this.alert = alert;
        this.trigger = trigger;
        this.triggeredMetric = triggeredMetric;
        this.notificationStatus = notificationStatus;
    }

    public Alert getAlert() {
        return alert;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public Optional<Long> getTriggerFiredTime() {
        return Optional.ofNullable(triggerFiredTime);
    }

    public Metric getTriggeredMetric() {
        return triggeredMetric;
    }

    public NotificationStatus getNotificationStatus() {
        return notificationStatus;
    }
}
