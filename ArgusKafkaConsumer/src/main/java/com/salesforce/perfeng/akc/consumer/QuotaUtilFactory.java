package com.salesforce.perfeng.akc.consumer;

import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.DBRedisQuotaService;
import com.salesforce.quota.IQuotaService;
import com.salesforce.quota.NoOpQuotaService;
import com.salesforce.quota.IBlacklistService;
import com.salesforce.quota.DBBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuotaUtilFactory {
    private volatile static IQuotaInfoProvider quotaInfoProvider;
    private volatile static IQuotaService quotaService = null;
    private volatile static IBlacklistService blacklistService = null;
    private volatile static boolean quotaServiceInitialized = false;
    private volatile static boolean quotaInfoProviderInitialized = false;
    private volatile static boolean blacklistServiceInitialized = false;
    private static final Logger logger = LoggerFactory.getLogger(QuotaUtilFactory.class);

    public synchronized static IQuotaInfoProvider getQuotaInfoProvider() {
        if(!quotaInfoProviderInitialized) {
            quotaInfoProvider = new QuotaInfoProviderImpl(getQuotaService());
            quotaInfoProvider.init(AKCConfiguration.getConfiguration());
            quotaInfoProviderInitialized = true;
        }
        return quotaInfoProvider;
    }

    public synchronized static IQuotaService getQuotaService() {
        if (!quotaServiceInitialized) {
            if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_SWITCH).equals("ON")) {

                try {
                    quotaService = new DBRedisQuotaService();
                    quotaService.init(AKCConfiguration.getConfiguration());
                } catch (Exception e) {
                    logger.warn("Quota service init failed. Defaulting to No Quota. Error Message:" + e.getMessage());
                    quotaService = new NoOpQuotaService();
                }
            } else {
                quotaService = new NoOpQuotaService();
            }
            quotaServiceInitialized = true;
        }
        return quotaService;
    }

    public synchronized static IBlacklistService getBlacklistService() {
        if (!blacklistServiceInitialized) {
            blacklistService = new DBBlacklistService();
            blacklistService.init(AKCConfiguration.getConfiguration());
            blacklistServiceInitialized = true;
        }
        return blacklistService;
    }
}
