package com.salesforce.dva.argus.service.mq.kafka;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.system.SystemConfiguration;

@Singleton
public class AjnaMessageService extends KafkaMessageService {

    @Inject
    public AjnaMessageService(SystemConfiguration config) {
        super(config);
    }

    public enum AjnaProperty {
        KAFKA_CUSTOM_SECURITY_INIT_CLASS("service.property.mq.kafka.security.init.customClassName", ""),
        KAFKA_CUSTOM_SECURITY_INIT_CLASS_METHOD("service.property.mq.kafka.security.init.customClassMethod", ""),
        KAFKA_CUSTOM_SECURITY_PROVIDER_NAME("service.property.mq.kafka.security.provider.name", ""),
        KAFKA_CUSTOM_SECURITY_PROVIDER_CLASS("service.property.mq.kafka.security.provider.class", "");

        private final String _name;
        private final String _defaultValue;

        AjnaProperty(String name, String defaultValue) {
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
         * Returns the default property value.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
}
