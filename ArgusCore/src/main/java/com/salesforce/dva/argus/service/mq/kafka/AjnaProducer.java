package com.salesforce.dva.argus.service.mq.kafka;

import com.salesforce.dva.argus.service.mq.kafka.AjnaMessageService.AjnaProperty;
import com.salesforce.dva.argus.service.mq.kafka.KafkaMessageService.Property;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.security.Provider;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;

public class AjnaProducer extends DefaultProducer {
    private final Logger _logger = LoggerFactory.getLogger(getClass());

    /**
     * Default AjnaProducer constructor; does not use the super(config) constructor so that the overriden createProducer()
     * can be called on construction
     *
     * @param config
     */
    public AjnaProducer(SystemConfiguration config) {
        _configuration = config;
        _producer = createProducer();
        _executorService = createExecutorService();
    }

    @Override
    protected KafkaProducer<String, String> createProducer() {
        Map<String, Object> producerConfig = new HashMap<String, Object>();

        String securityInitClassName = _configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS.getDefaultValue());
        String securityInitClassMethod = _configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS_METHOD.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_INIT_CLASS_METHOD.getDefaultValue());
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
        String customProviderName = _configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_NAME.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_NAME.getDefaultValue());
        String customProviderClass = _configuration.getValue(AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_CLASS.getName(), AjnaProperty.KAFKA_CUSTOM_SECURITY_PROVIDER_CLASS.getDefaultValue());
        if (StringUtils.isNotEmpty(customProviderName) && StringUtils.isNotEmpty(customProviderClass)) {
            if (Security.getProvider(customProviderName) == null) {
                try {
                    Security.addProvider((Provider) Class.forName(customProviderClass).newInstance());
                    _logger.info("Successfully added custom Kafka security provider named {} bound to class {}", customProviderName, customProviderClass);
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
                    _logger.error("Skipping Kafka custom security provider from: {}", ex);
                }
            } else {
                _logger.info("Custom Kafka security provider named {} already present", customProviderName);
            }
        }

        producerConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                _configuration.getValue(Property.KAFKA_BROKERS.getName(), Property.KAFKA_BROKERS.getDefaultValue()));
        producerConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        producerConfig.put(ProducerConfig.CLIENT_ID_CONFIG, "argus.producer");
        producerConfig.put(ProducerConfig.BUFFER_MEMORY_CONFIG,
                Long.parseLong(
                        _configuration.getValue(Property.KAFKA_PRODUCER_BUFFER_MEMORY.getName(), Property.KAFKA_PRODUCER_BUFFER_MEMORY.getDefaultValue())));
        producerConfig.put(ProducerConfig.BATCH_SIZE_CONFIG,
                Integer.parseInt(
                        _configuration.getValue(Property.KAFKA_PRODUCER_BATCH_SIZE.getName(), Property.KAFKA_PRODUCER_BATCH_SIZE.getDefaultValue())));
        // Set security props
        producerConfig.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG,
                _configuration.getValue(Property.KAFKA_SECURITY_PROTOCOL.getName(), Property.KAFKA_SECURITY_PROTOCOL.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_PROVIDER_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_PROVIDER.getName(), Property.KAFKA_SSL_PROVIDER.getDefaultValue()));

        producerConfig.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_TYPE.getName(), Property.KAFKA_SSL_KEYSTORE_TYPE.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_LOCATION.getName(), Property.KAFKA_SSL_KEYSTORE_LOCATION.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEYSTORE_PASSWORD.getName(), Property.KAFKA_SSL_KEYSTORE_PASSWORD.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_KEY_PASSWORD.getName(), Property.KAFKA_SSL_KEY_PASSWORD.getDefaultValue()));

        producerConfig.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_TYPE.getName(), Property.KAFKA_SSL_TRUSTSTORE_TYPE.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_LOCATION.getName(), Property.KAFKA_SSL_TRUSTSTORE_LOCATION.getDefaultValue()));
        producerConfig.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                _configuration.getValue(Property.KAFKA_SSL_TRUSTSTORE_PASSWORD.getName(), Property.KAFKA_SSL_TRUSTSTORE_PASSWORD.getDefaultValue()));
        return new KafkaProducer<String, String>(producerConfig, new StringSerializer(), new StringSerializer());
    }
}
