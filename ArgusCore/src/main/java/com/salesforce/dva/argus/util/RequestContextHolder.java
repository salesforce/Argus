package com.salesforce.dva.argus.util;

/**
 * This thread local variable holds the context on user who is making the current request
 */
public class RequestContextHolder {

    private static ThreadLocal<RequestContext> currentRequestContext = new ThreadLocal<RequestContext>();

    public static RequestContext getRequestContext() {
        return currentRequestContext.get();
    }

    public static void setRequestContext(RequestContext context) {
        currentRequestContext.set(context);
    }
}
