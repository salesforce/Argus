package com.salesforce.dva.argus.service.mq.kafka;

import java.io.Serializable;
import java.util.List;

public interface Producer {
    <T extends Serializable> int enqueue(final String topic, List<T> objects);

    void shutdown();
}