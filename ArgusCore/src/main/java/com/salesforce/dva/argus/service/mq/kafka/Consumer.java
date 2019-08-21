package com.salesforce.dva.argus.service.mq.kafka;

import com.fasterxml.jackson.databind.JavaType;

import java.io.Serializable;
import java.util.List;

public interface Consumer {
    <T extends Serializable> List<T> dequeueFromBuffer(String topic, Class<T> type, int timeout, int limit);

    <T extends Serializable> List<T> dequeueFromBuffer(String topic, JavaType type, int timeout, int limit);

    void shutdown();
}