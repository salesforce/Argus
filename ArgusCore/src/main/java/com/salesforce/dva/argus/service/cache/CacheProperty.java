package com.salesforce.dva.argus.service.cache;

/**
 * Enumerates the implementation specific configuration properties.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public enum CacheProperty {

    /** The global cache expiry in seconds. */
    REDIS_CACHE_EXPIRY_IN_SEC("service.property.cache.redis.cache.expiry.in.sec", "3600"),
    /** The cache endpoint. */
    REDIS_CLUSTER("service.property.cache.redis.cluster", "default_value"),
    /** The maximum number of cache connections. */
    REDIS_SERVER_MAX_CONNECTIONS("service.property.cache.redis.server.max.connections", "100");

    private final String _name;
    private final String _defaultValue;

    private CacheProperty(String name, String defaultValue) {
        _name = name;
        _defaultValue = defaultValue;
    }

    /**
     * Returns the property name.
     *
     * @return  The property name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Returns the default value for the property.
     *
     * @return The default value.
     */
    public String getDefaultValue() {
        return _defaultValue;
    }
}
