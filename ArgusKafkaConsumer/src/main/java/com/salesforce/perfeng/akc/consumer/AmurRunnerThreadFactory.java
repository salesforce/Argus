package com.salesforce.perfeng.akc.consumer;

import java.util.concurrent.ThreadFactory;

public class AmurRunnerThreadFactory implements ThreadFactory {
    private String threadName;
    private static int threadNo = 0;
    private int priority;

    public AmurRunnerThreadFactory(String threadName, int priority) {
        this.threadName = threadName;
        this.priority = priority;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r, threadName + "-" + threadNo++);
        t.setPriority(priority);
        return t;
    }
}
