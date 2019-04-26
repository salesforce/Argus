package com.salesforce.dva.argus.service.annotation;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class DefaultAnnotationServiceTest {
    @Mock
    private TSDBService tsdbService;
    @Mock
    private MonitorService monitorService;
    private SystemConfiguration config;
    private DefaultAnnotationService defaultAnnotationService;

    /* TEST DATA */
    Annotation a;
    Annotation tooLargeAnnotation;
    PrincipalUser pu;

    @Before
    public void setUp() {
        config = new SystemConfiguration(new Properties());
        defaultAnnotationService = new DefaultAnnotationService(tsdbService, monitorService, config);

        a = new Annotation("source",
                "id",
                "type",
                "scope",
                "metric",
                System.currentTimeMillis());
        tooLargeAnnotation = new Annotation("source2",
                "id2",
                "type2",
                "scope2",
                "metric2",
                System.currentTimeMillis());
        // set up annotation with size larger than max size allowed
        final int TAG_SIZE = 100;
        final int NUM_TAGS = DefaultAnnotationService.MAX_ANNOTATION_SIZE_BYTES / TAG_SIZE / 2;
        for (int i = 0; i < NUM_TAGS; i++) {
            tooLargeAnnotation.setTag(RandomStringUtils.random(TAG_SIZE), RandomStringUtils.random(TAG_SIZE));
        }
        final Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            fields.put(RandomStringUtils.random(10), RandomStringUtils.random(10));
        }
        tooLargeAnnotation.setFields(fields);

        pu = new PrincipalUser(null, "username", "email");
    }

    @Test
    public void updateAnnotations_testAnnotationSizeLessThanMax() {
        // test
        defaultAnnotationService.updateAnnotations(ImmutableMap.of(a, pu));

        // verify
        verify(monitorService).modifyCounter(Counter.ANNOTATION_WRITES, 1, null);
        ArgumentCaptor<List> annotationListCaptor = ArgumentCaptor.forClass(List.class);
        verify(tsdbService).putAnnotations(annotationListCaptor.capture());
        assertEquals(1, annotationListCaptor.getValue().size());
        assertTrue(annotationListCaptor.getValue().contains(a));
    }

    @Test
    public void updateAnnotations_testListContainingOneAnnotationSizeGreaterThanMax() {
        // test
        defaultAnnotationService.updateAnnotations(ImmutableMap.of(a, pu, tooLargeAnnotation, pu));

        // verify
        verify(monitorService).modifyCounter(Counter.ANNOTATION_DROPS_MAXSIZEEXCEEDED, 1, ImmutableMap.of("source", tooLargeAnnotation.getSource()));
        verify(monitorService).modifyCounter(Counter.ANNOTATION_WRITES, 1, null);
        ArgumentCaptor<List> annotationListCaptor = ArgumentCaptor.forClass(List.class);
        verify(tsdbService).putAnnotations(annotationListCaptor.capture());
        assertEquals(1, annotationListCaptor.getValue().size());
    }

    @Test
    public void updateAnnotations_testOnlyOneAnnotationSizeGreaterThanMax() {
        // test
        defaultAnnotationService.updateAnnotations(ImmutableMap.of(tooLargeAnnotation, pu));

        // verify
        verify(monitorService).modifyCounter(Counter.ANNOTATION_DROPS_MAXSIZEEXCEEDED, 1, ImmutableMap.of("source", tooLargeAnnotation.getSource()));
        verify(monitorService).modifyCounter(Counter.ANNOTATION_WRITES, 0, null);
        verify(tsdbService, never()).putAnnotations(any());
    }
}
