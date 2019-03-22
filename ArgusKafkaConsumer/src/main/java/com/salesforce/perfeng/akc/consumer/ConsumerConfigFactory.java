package com.salesforce.perfeng.akc.consumer;

import com.salesforce.perfeng.akc.AKCConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;


public class ConsumerConfigFactory {
    private static Logger logger = LoggerFactory.getLogger(ConsumerConfigFactory.class);

    private ConsumerConfigFactory() {
        throw new IllegalStateException("Utility class");
    }

    // Returns the whole akc.configuration file as a Properties for KafkaConsumer
    public static Properties createConsumerConfig() {
        Properties props = AKCConfiguration.getConfiguration();

        StringBuilder sb = new StringBuilder();

        Pattern pattern = Pattern.compile("pwd|secret|password|passwd|token");
        Set<String> names = new TreeSet<>(props.stringPropertyNames());
        names.stream().forEach(name -> {
                String value = pattern.matcher(name).find() ? "********" : props.get(name).toString();
                sb.append("\t").append(name).append(" : ").append(value).append("\n");
            });

        logger.info("Argus AjnaConsumer props=\n" + sb.toString());
        return props;
    }
}
