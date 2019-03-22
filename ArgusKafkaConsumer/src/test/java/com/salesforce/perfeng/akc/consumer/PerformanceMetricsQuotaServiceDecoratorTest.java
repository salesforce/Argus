package com.salesforce.perfeng.akc.consumer;

import com.salesforce.quota.IInfractionList;
import com.salesforce.quota.IPrincipal;
import com.salesforce.quota.IQuotaService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class )
public class PerformanceMetricsQuotaServiceDecoratorTest {
    @Mock
    private InstrumentationService mockInstrumentationService;
    @Mock
    private IQuotaService mockQuotaService;
    @Mock
    private IPrincipal mockPrincipal;
    @Mock
    private IInfractionList mockInfractionList;
    private PerformanceMetricsQuotaServiceDecorator pmqsd;

    @Before
    public void setUp() {
        reset(mockInstrumentationService);
        reset(mockQuotaService);

        pmqsd = new PerformanceMetricsQuotaServiceDecorator(mockQuotaService, mockInstrumentationService);
    }

    @Test
    public void evaluate_test() {
        long now = System.currentTimeMillis();
        long counterIncrement = 1;
        when(mockQuotaService.evaluate(mockPrincipal, now, counterIncrement)).thenReturn(mockInfractionList);

        IInfractionList il = pmqsd.evaluate(mockPrincipal, now, counterIncrement);

        verify(mockQuotaService).evaluate(mockPrincipal, now, counterIncrement);
        ArgumentCaptor<Double> latencyMSCaptor = ArgumentCaptor.forClass(Double.class);
        verify(mockInstrumentationService).updateTimer(eq(InstrumentationService.QUOTA_EVALUATE_LATENCY),
                latencyMSCaptor.capture(),
                isNull());
        assertTrue(0 >= Double.compare(0, latencyMSCaptor.getValue()));
        assertEquals(mockInfractionList, il);
    }

    @Test
    public void init_test() throws Exception {
        Properties p = new Properties();
        pmqsd.init(p);

        verify(mockQuotaService).init(p);
    }

    @Test
    public void getPrincipalFromName_test() {
        String name = "TEST_NAME";
        when(mockQuotaService.getPrincipalFromName(name)).thenReturn(mockPrincipal);

        IPrincipal p = pmqsd.getPrincipalFromName(name);

        verify(mockQuotaService).getPrincipalFromName(name);
        assertEquals(mockPrincipal, p);
    }
}
