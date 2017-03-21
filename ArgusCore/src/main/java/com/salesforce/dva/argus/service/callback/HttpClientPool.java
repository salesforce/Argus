/*
 * Copyright (C) 2016 Kiwigrid GmbH (oss@kiwigrid.com)
 *
 * Licensed under the  Creative Commons - Attribution-NoDerivatives License, Version 4.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *         https://creativecommons.org/licenses/by-nd/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.salesforce.dva.argus.service.callback;

import java.util.concurrent.TimeUnit;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Manages a pool of HttpClients.
 *
 * @author svenkrause
 */
public class HttpClientPool extends Pool<HttpClient> {

        public HttpClientPool(int maxIdle, long validationInterval, TimeUnit timeUnit) {
                super(0, maxIdle, validationInterval, timeUnit);
        }

        @Override
        protected HttpClient createObject() {
                return HttpClients.createDefault();
        }
}
