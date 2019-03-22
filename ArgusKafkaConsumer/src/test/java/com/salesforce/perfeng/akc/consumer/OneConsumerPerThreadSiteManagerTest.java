package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.system.SystemMain;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.lang.Runnable;
import java.util.concurrent.Callable;
import org.apache.commons.lang3.StringUtils;
import static org.junit.Assert.assertEquals;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.dva.argus.service.TSDBService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import com.salesforce.dva.argus.system.SystemMain;

import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.ArgumentCaptor;

import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.atLeast;



@RunWith(PowerMockRunner.class )
public class OneConsumerPerThreadSiteManagerTest {
    OneConsumerPerThreadSiteManager oneMgr;
    SystemMain system;
    ExecutorService _siteExecutor;


    @Before
    public void setUp() {
        System.setProperty("akc.common.configuration", "src/test/resources/akc.config");
    }

    @Test
    public void testSubmitter() {

        TSDBService mockTsdbService = PowerMockito.mock(TSDBService.class);

        AmurKafkaRunner akrMock = PowerMockito.mock(AmurKafkaRunner.class);
        try {
            PowerMockito.whenNew(AmurKafkaRunner.class).withAnyArguments().thenReturn(akrMock);
        } catch (Exception e) {
            e.printStackTrace();
        }

        ExecutorService mockExecService = mock(ExecutorService.class);
        Properties props = ConsumerConfigFactory.createConsumerConfig();

        int numStreams = Integer.parseInt(props.getProperty(AKCConfiguration.Parameter.NUM_STREAMS.getKeyName()));
        List<AmurKafkaRunner> amurRunners = new ArrayList<>();
        for (int i=0; i < numStreams; i++) {
            AmurKafkaRunner akr = new AmurKafkaRunner<byte[], byte[]>(ConsumerType.METRICS,
                                                                      props,
                                                                      system,
                                                                      new AtomicInteger(0),
                                                                      AjnaConsumerTask.class);
            amurRunners.add(akr);
        }

        oneMgr = new OneConsumerPerThreadSiteManager(system,
                                                     props,
                                                     mockExecService,
                                                     amurRunners,
                                                     mockTsdbService);
        _siteExecutor = Executors.newFixedThreadPool(1);
        _siteExecutor.execute(oneMgr);
        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
        }
        verify(mockExecService, atLeast(1)).submit(any(AmurKafkaRunner.class));

    }
}
