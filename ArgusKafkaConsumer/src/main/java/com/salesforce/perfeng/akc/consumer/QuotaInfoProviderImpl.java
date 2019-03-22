package com.salesforce.perfeng.akc.consumer;


import com.salesforce.mandm.ajna.Metric;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IQuotaService;
import com.salesforce.quota.IPrincipal;
import com.salesforce.quota.IInfractionList;
import com.salesforce.quota.IInfraction;
import com.salesforce.quota.PolicyTrigger;
import com.salesforce.quota.DBPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The type Quota info provider.
 */
public class QuotaInfoProviderImpl implements IQuotaInfoProvider {

    private static final String PREFIX_QUOTA_GROUP = "GG";
    private static final String SEPARATOR = "`";
    private static final String PREFIX_SVC_DC = "SD";

    private final Logger logger = LoggerFactory.getLogger(QuotaInfoProviderImpl.class);

    private final static int MIN_LOG_INTERVAL_MS=60000;

    private Map<String,Long> loggedPrincipalPolicyMap= new ConcurrentHashMap<>();

    private final IQuotaService quotaService;
    private String quotaGroup;
    private IPrincipal quotaGroupPrincipal;
    private IPrincipal globalPrincipal;

    public synchronized void logIfNeeded(IInfractionList il) {
        if (!il.hasInfraction()) return;
        for (PolicyTrigger policyAction : il.getInfractionCount().keySet()) {
            for (IInfraction infraction : il.getInfractionByPolicyAction(policyAction)) {
                String key = infraction.getUser().getName()
                        + "_" + infraction.getPolicy().getNamespace() + "_" + infraction.getPolicy().getName()
                        + "_" + infraction.getTimestamp();

                //log only if the same principal-policy-timestamp pair was not logged within MIN_LOG_INTERVAL_MS
                if (!loggedPrincipalPolicyMap.containsKey(key)
                        || (loggedPrincipalPolicyMap.containsKey(key) && (loggedPrincipalPolicyMap.get(key) - System.currentTimeMillis() > MIN_LOG_INTERVAL_MS))) {
                    logger.info("New Infraction for User: " + infraction.getUser().getName()
                            + " Policy Namespace: " + infraction.getPolicy().getNamespace()
                            + " Policy Scope: " + infraction.getPolicy().getScope()
                            + " Policy Name: " + infraction.getPolicy().getName()
                            + " Policy Duration: " + infraction.getPolicy().getDuration()
                            + " Policy Threshold: " + infraction.getPolicy().getThreshold()
                            + " Policy Action: " + infraction.getPolicy().getAction()
                            + " Counter at Infraction: " + infraction.getCounter()
                            + " Timestamp at Infraction: " + infraction.getTimestamp()
                    );
                    loggedPrincipalPolicyMap.put(key, System.currentTimeMillis());
                }
            }
        }
    }

    @Override
    public IPrincipal getGroupLevelPrincipal() {
        return quotaGroupPrincipal;
    }

    @Override
    public IPrincipal getGlobalPrincipal() {
        return globalPrincipal;
    }

    /**
     * Instantiates a new Quota info provider.
     *
     * @param quotaService the quota service
     */
    public QuotaInfoProviderImpl(IQuotaService quotaService) {
        this.quotaService=quotaService;
    }

    @Override
    public void init(Properties properties) {
        IPrincipal defaultPrincipal = quotaService.getPrincipalFromName(PREFIX_QUOTA_GROUP + SEPARATOR + "default");

        // initialize group principal instance
        quotaGroup = AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_GROUP);
        quotaGroupPrincipal = quotaService.getPrincipalFromName(PREFIX_QUOTA_GROUP + SEPARATOR + quotaGroup);
        if (quotaGroupPrincipal == null) {
            //can't find the group, create one
            quotaGroupPrincipal = new DBPrincipal();
            quotaGroupPrincipal.setName(PREFIX_QUOTA_GROUP + SEPARATOR + quotaGroup);
            if (defaultPrincipal != null) {
                //if default policy is not in the database, then go unlimited
                quotaGroupPrincipal.setPolicies(defaultPrincipal.getPolicies());
            }
        }

        // initialize global principal instance
        String globalPrincipalName = AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_GLOBAL_PRINCIPAL);
        globalPrincipal = quotaService.getPrincipalFromName(PREFIX_QUOTA_GROUP + SEPARATOR + globalPrincipalName);
        if (globalPrincipal == null) {
            //can't find the global principal, create one
            globalPrincipal = new DBPrincipal();
            globalPrincipal.setName(PREFIX_QUOTA_GROUP + SEPARATOR + globalPrincipalName);
            if (defaultPrincipal != null) {
                //if default policy is not in the database, then go unlimited
                quotaGroupPrincipal.setPolicies(defaultPrincipal.getPolicies());
            }
        }
    }

    public List<IPrincipal> extractPrincipalsFromMetric(Metric ajnaMetric) {

        List<IPrincipal> principalList = new ArrayList<>();

        CharSequence svc = ajnaMetric.getService();
        CharSequence dc = ajnaMetric.getTags().get(SchemaField.TAG_KEY_DATACENTER);

        String svcDc=(svc == null ? "noSVC" : svc.toString()) +SEPARATOR+ (dc == null ? "noDC" : dc.toString().toUpperCase());

        IPrincipal principal = quotaService.getPrincipalFromName(PREFIX_SVC_DC+SEPARATOR+svcDc);

        if(principal==null) {
            //not in the db, so create one in memory and assign default quota policies
            principal=new DBPrincipal();
            principal.setName("SD`"+svcDc);
            IPrincipal defaultPrincipal = quotaService.getPrincipalFromName(PREFIX_SVC_DC+SEPARATOR+"default"+SEPARATOR+"default");
            if(defaultPrincipal!=null) {
                principal.setPolicies(defaultPrincipal.getPolicies());
            }
        }

        principalList.add(principal);

        return principalList;
    }

}
