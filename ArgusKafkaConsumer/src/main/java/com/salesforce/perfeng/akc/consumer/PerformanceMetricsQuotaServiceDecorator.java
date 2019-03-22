package com.salesforce.perfeng.akc.consumer;

import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.quota.IInfractionList;
import com.salesforce.quota.IPrincipal;
import com.salesforce.quota.IQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class PerformanceMetricsQuotaServiceDecorator implements IQuotaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceMetricsQuotaServiceDecorator.class);
    private IQuotaService qs;
    private InstrumentationService is;

    public PerformanceMetricsQuotaServiceDecorator(IQuotaService qs) {
        this.qs = qs;
        this.is = InstrumentationService.getInstance(
                SystemMain.getInstance().getServiceFactory().getTSDBService());
    }

    PerformanceMetricsQuotaServiceDecorator(IQuotaService qs, InstrumentationService is) {
        this.qs = qs;
        this.is = is;
    }

    @Override
    public IInfractionList evaluate(IPrincipal user, long timestamp, long counterIncrement) {
        long start = System.currentTimeMillis();
        try {
            return qs.evaluate(user, timestamp, counterIncrement);
        } finally {
            is.updateTimer(InstrumentationService.QUOTA_EVALUATE_LATENCY,
                    ((double)System.currentTimeMillis()-start),
                    null);
        }
    }

    @Override
    public void init(Properties properties) throws Exception {
        qs.init(properties);
    }

    @Override
    public IPrincipal getPrincipalFromName(String name) {
        return qs.getPrincipalFromName(name);
    }
}
