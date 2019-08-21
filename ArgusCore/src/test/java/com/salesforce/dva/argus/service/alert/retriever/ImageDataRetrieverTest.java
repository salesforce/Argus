package com.salesforce.dva.argus.service.alert.retriever;

import com.google.common.collect.ImmutableList;
import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.ImageProperties;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AlertService.Notifier.NotificationStatus;
import com.salesforce.dva.argus.service.ImageService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.system.SystemMain;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.List;

import static com.salesforce.dva.argus.TestUtils.generateAlert;
import static com.salesforce.dva.argus.TestUtils.getHistory;
import static com.salesforce.dva.argus.TestUtils.getMetric;
import static com.salesforce.dva.argus.TestUtils.getNotification;
import static com.salesforce.dva.argus.TestUtils.getTrigger;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ImageDataRetrieverTest {

    private static final String IMAGE_URL_PREFIX = "http://localhost:8080/argus/#/images/";
    private static final String IMAGE_ID = "img1";

    @Mock
    private ImageService imageServiceMock;

    @Mock
    private PrincipalUser principalUserMock;

    static private SystemMain system;

    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    private ImageDataRetriever imageDataRetriever;

    @Before
    public void setup() {
        imageDataRetriever = new ImageDataRetriever(imageServiceMock, system.getConfiguration());
    }

    @Test
    public void testGetImageUrl() {
        String imageURL = imageDataRetriever.getImageURL(Pair.of(IMAGE_ID, "Test String".getBytes()));
        assertEquals(imageURL, IMAGE_URL_PREFIX + IMAGE_ID);
    }

    @Test
    public void testGetImageUrlWhenImageDetailsIsNull() {
        String imageURL = imageDataRetriever.getImageURL(null);
        assertEquals(imageURL, null);
    }

    @Test
    public void testGetImageUrlWhenImageIDIsNull() {
        String imageURL = imageDataRetriever.getImageURL(Pair.of(null, null));
        assertEquals(imageURL, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRetrievingAnnotatedImageWhenNotificationContextIsNull() {
        Pair<String, byte[]> imageDetails = imageDataRetriever.getAnnotatedImage(null);

    }

    @Test
    public void testRetrievingAnnotatedImageWhenTriggerTypeIsGreaterThan() {
        Double triggerThreshold = 1D;
        Long triggerFiredTime = 1563692460000L;
        Long inertia = 1800*1000L;
        ImageDataRetrievalContext context = getImageDataRetrievalContext(Trigger.TriggerType.GREATER_THAN, triggerFiredTime, inertia, triggerThreshold, NotificationStatus.TRIGGERED);

        ArgumentMatcher<ImageProperties> imageProperties = new ArgumentMatcher<ImageProperties>() {
            @Override
            public boolean matches(ImageProperties imageProperties) {
                return imageProperties.getShadeXAxisArea().size() == 1 && imageProperties.getShadeYAxisArea().size() == 1;
            }
        };

        ArgumentMatcher<List<Metric>> metricList = new ArgumentMatcher<List<Metric>>() {
            @Override
            public boolean matches(List<Metric> metricList) {
                return metricList.size() == 1;
            }
        };
        when(imageServiceMock.generateAndStoreImage(argThat(metricList), argThat(imageProperties),
                eq(true))).thenReturn(Pair.of("1", "TestString".getBytes()));
        Pair<String, byte[]> imageDetails = imageDataRetriever.getAnnotatedImage(context);

        verify(imageServiceMock, times(1)).generateAndStoreImage(argThat(metricList),
                argThat(imageProperties), eq(true));
    }

    @Test
    public void testRetrievingAnnotatedImageWhenTriggerStatusIsCleared() {
        Double triggerThreshold = 1D;
        Long triggerFiredTime = 1563692460000L;
        Long inertia = 1800*1000L;
        ImageDataRetrievalContext context = getImageDataRetrievalContext(Trigger.TriggerType.GREATER_THAN, triggerFiredTime, inertia, triggerThreshold, NotificationStatus.CLEARED);

        ArgumentMatcher<ImageProperties> imageProperties = new ArgumentMatcher<ImageProperties>() {
            @Override
            public boolean matches(ImageProperties imageProperties) {
                return imageProperties.getShadeYAxisArea() == null && imageProperties.getShadeXAxisArea() == null;
            }
        };
        when(imageServiceMock.generateAndStoreImage(eq(ImmutableList.of(context.getTriggeredMetric())), argThat(imageProperties),
                eq(true))).thenReturn(Pair.of("1", "TestString".getBytes()));
        Pair<String, byte[]> imageDetails = imageDataRetriever.getAnnotatedImage(context);

        verify(imageServiceMock, times(1)).generateAndStoreImage(
                eq(ImmutableList.of(context.getTriggeredMetric())), argThat(imageProperties), eq(true));
    }

    private ImageDataRetrievalContext getImageDataRetrievalContext(Trigger.TriggerType triggerType,
                                                                           long triggerFiredTime,
                                                                           long inertiaMillis,
                                                                           double triggerThreshold, NotificationStatus notificationStatus) {
        Alert alert = generateAlert("TestName", principalUserMock, "-1h:argus.jvm:cores.active:max");
        alert.setEnabled(true);

        Trigger trigger = getTrigger(alert, triggerType, "TestTrigger", String.valueOf(triggerThreshold),
                String.valueOf(inertiaMillis));
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification("TEST EMAIL NOTIFICATION",
                "TEST_EMAIL_NOTIFIER", alert, Arrays.asList("test-subscription"));
        alert.addNotification(notification);

        Metric metric = getMetric();
        History history = getHistory();

        return new ImageDataRetrievalContext(alert, trigger, triggerFiredTime, metric, notificationStatus);
    }

}
