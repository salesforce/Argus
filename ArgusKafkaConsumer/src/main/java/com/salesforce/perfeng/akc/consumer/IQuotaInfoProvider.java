package com.salesforce.perfeng.akc.consumer;

import com.salesforce.mandm.ajna.Metric;
import com.salesforce.quota.IInfractionList;
import com.salesforce.quota.IPrincipal;

import java.util.List;
import java.util.Properties;

public interface IQuotaInfoProvider {

    IPrincipal getGlobalPrincipal();

    void init(Properties properties);

    List<IPrincipal> extractPrincipalsFromMetric(Metric ajnaMetric);

    void logIfNeeded(IInfractionList infractionList);

    IPrincipal getGroupLevelPrincipal();
}
