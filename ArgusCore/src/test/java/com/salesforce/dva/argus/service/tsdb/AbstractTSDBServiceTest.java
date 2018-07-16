package com.salesforce.dva.argus.service.tsdb;

import com.salesforce.dva.argus.AbstractTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;

public class AbstractTSDBServiceTest extends AbstractTest {
    static final int RUNS = 100;
    static final int THREADS = 20;

    @Test
    public void testCyclingIterator() {
        AbstractTSDBService service = new AbstractTSDBService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
        String[][] endpointTrials = {
                {"1"},
                {"1", "2", "3", "4"},
                {"4", "6", "133", "200", "1133", "2244", "4466", "8989", "19200", "320"}
        };

        for (int j = 0; j < endpointTrials.length; j++) {
            String[] endpoints = endpointTrials[j];
            Iterator<String> iter = service.constructCyclingIterator(endpoints);
            List<Thread> threads = new ArrayList<>();
            ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
            System.out.println(String.format("Trying %d .next() calls with %d threads calling cycling iterator on endpoints %s", RUNS, THREADS, String.join(", ", endpoints)));
            for (int i = 0; i < THREADS; i++) {
                Thread thread = new Thread(new IterateTask(iter, queue));
                threads.add(thread);
                thread.start();
            }
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException ex) {
                    return;
                }
            }
            Map<String, Integer> counts = new HashMap<>();
            String[] actuals = queue.toArray(new String[0]);
            for (String s: actuals) {
                Integer count = counts.get(s);
                if (count == null) {
                    counts.put(s, 1);
                } else {
                    counts.put(s, count + 1);
                }
            }
            /* The inserted items should each have been inserted ( #insertions / #element) times */
            for (int count: counts.values()) {
                assertEquals(RUNS * THREADS / endpoints.length, count);
            }
        }
    }

    class IterateTask implements Runnable {
        Random random = new Random();
        Iterator<String> iter;
        ConcurrentLinkedQueue<String> queue;

        IterateTask(Iterator<String> iterator, ConcurrentLinkedQueue<String> queue) {
            this.iter = iterator;
            this.queue = queue;
        }
        @Override
        public void run() {
            try {
                for (int i = 0; i < RUNS; i++) {
                    queue.add(iter.next());
                    Thread.sleep(3 + random.nextInt(5));
                }
            } catch (InterruptedException ex) {
                return;
            }
        }
    }
}
