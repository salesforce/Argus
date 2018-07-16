package com.salesforce.dva.argus.service.tsdb;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.service.TSDBService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class AbstractTSDBServiceTest extends AbstractTest {
    static final int RUNS = 100;
    static final int THREADS = 20;

    @Test
    public void testCyclingIterator() {
        AbstractTSDBService service = new AbstractTSDBService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
        String[][] endpointTrials = {
                {"1"},
                {"1", "2", "3", "4"}
        };

        for (int j = 0; j < endpointTrials.length; j++) {
            String[] endpoints = endpointTrials[j];
            Iterator iter = service.constructCyclingIterator(endpoints);
            List<Thread> threads = new ArrayList<>();
            System.out.println(String.format("Trying %d .next() calls with %d threads calling cycling iterator on endpoints %s", RUNS, THREADS, String.join(", ", endpoints)));
            for (int i = 0; i < THREADS; i++) {
                Thread thread = new Thread(new IterateTask(iter));
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
        }
    }

    class IterateTask implements Runnable {
        Random random = new Random();
        Iterator iter;

        IterateTask(Iterator iterator) {
            this.iter = iterator;
        }
        @Override
        public void run() {
            try {
                for (int i = 0; i < RUNS; i++) {
                    iter.next();
                    Thread.sleep(3 + random.nextInt(5));
                }
            } catch (InterruptedException ex) {
                return;
            }
        }
    }
}
