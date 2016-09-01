package com.salesforce.dva.warden.client;

abstract class AbstractService {

    protected final WardenHttpClient client;

    protected AbstractService(WardenHttpClient client) {
        this.client = client;
    }

}
