package com.salesforce.dva.argus.service.mq.kafka;

import com.salesforce.dva.argus.service.mq.kafka.AjnaMessageService.AjnaProperty;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.security.Security;

public class AjnaConsumer extends DefaultConsumer {
    private final Logger _logger = LoggerFactory.getLogger(getClass());

    public AjnaConsumer(SystemConfiguration configuration) {
        super(configuration);

        String securityInitClassName = configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS.getDefaultValue());
        String securityInitClassMethod = configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS_METHOD.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS_METHOD.getDefaultValue());
        if (StringUtils.isNotEmpty(securityInitClassName) && StringUtils.isNotEmpty(securityInitClassMethod)) {
            try {
                Class.forName(securityInitClassName).getMethod(securityInitClassMethod).invoke(null);
                _logger.info("Successfully ran custom Kafka security initialization using method: {}.{}()", securityInitClassName, securityInitClassMethod);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                _logger.error("Skipping custom Kafka security initialization from: {}", ex);
            }
        } else {
            _logger.info("Skipped custom Kafka security initialization");
        }
        String customProviderName = configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_NAME.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_NAME.getDefaultValue());
        String customProviderClass = configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_CLASS.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_CLASS.getDefaultValue());
        if (StringUtils.isNotEmpty(customProviderName) && StringUtils.isNotEmpty(customProviderClass)) {
            if (Security.getProvider(customProviderName) == null) {
                try {
                    Security.addProvider((Provider) Class.forName(customProviderClass).newInstance());
                    _logger.info("Successfully added custom Kafka security provider named {} bound to class {}", customProviderName, customProviderClass);
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
                    _logger.error("Skipping Kafka custom security provider from: {}", ex);
                }
            }  else {
                _logger.info("Custom Kafka security provider named {} already present", customProviderName);
            }
        }
    }
}
