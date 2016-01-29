/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
     
package com.salesforce.dva.argus.util;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import java.util.Calendar;
import java.util.Random;

import static org.junit.Assert.*;

public class CronTest {

    private static final Random rng;
    private static final long seed;

    static {
        seed = System.currentTimeMillis();
        rng = new Random(seed);
    }

    @BeforeClass
    public static void beforeClass() {
        LoggerFactory.getLogger(CronTest.class).info("Using a random seed of " + seed);
    }

    @Test
    public void testIsValidWithSpecialEntry() {
        assertTrue(Cron.isValid("@monTHly"));
    }

    @Test
    public void testTablesAlways() {
        CronTabEntry cron = new CronTabEntry("* * * * *");

        for (int i = 0; i < cron.minutes.length; i++) {
            assertTrue(cron.minutes[i]);
        }
        for (int i = 0; i < cron.hours.length; i++) {
            assertTrue(cron.hours[i]);
        }
        for (int i = 0; i < cron.days.length; i++) {
            assertTrue(cron.days[i]);
        }
        for (int i = 0; i < cron.months.length; i++) {
            assertTrue(cron.months[i]);
        }
        for (int i = 0; i < cron.weekdays.length; i++) {
            assertTrue(cron.weekdays[i]);
        }
    }

    @Test
    public void testTablesStep() {
        String entry = "*/S */S */S */S */S";

        for (int step = 1; step < Calendar.SATURDAY; step++) {
            CronTabEntry cron = new CronTabEntry(entry.replaceAll("S", Integer.toString(step)));
            boolean[][] tables = new boolean[][] { cron.minutes, cron.hours, cron.days, cron.months, cron.weekdays };

            for (final boolean[] table : tables) {
                for (int i = 0; i < table.length; i++) {
                    assertEquals(i % step == 0, table[i]);
                }
            }
        }
    }

    @Test
    public void testStepModNotDiv() {
        int step = 12;
        String entry = "*/" + step + " * * * *";
        CronTabEntry cron = new CronTabEntry(entry);
        boolean[] table = cron.minutes;

        for (int i = 0; i < cron.minutes.length; i++) {
            assertEquals(i % step == 0, table[i]);
        }
    }

    @Test
    public void testTablesRange() {
        int boundA = rng.nextInt(23);
        int boundB = rng.nextInt(23);
        int start = Math.min(boundA, boundB);
        int end = Math.max(boundA, boundB);
        String entry = String.format("* %d-%d * * *", start, end);
        CronTabEntry cron = new CronTabEntry(entry);

        for (int i = 0; i < cron.hours.length; i++) {
            if (i >= start && i <= end) {
                assertTrue(cron.hours[i]);
            } else {
                assertFalse(cron.hours[i]);
            }
        }
    }

    @Test
    public void testTablesList() {
        int idxA = rng.nextInt(23);
        int idxB = rng.nextInt(23);
        int idxC = rng.nextInt(23);
        String entry = String.format("* %d,%d,%d * * *", idxA, idxB, idxC);
        CronTabEntry cron = new CronTabEntry(entry);

        for (int i = 0; i < cron.hours.length; i++) {
            if (i == idxA || i == idxB || i == idxC) {
                assertTrue(cron.hours[i]);
            } else {
                assertFalse(cron.hours[i]);
            }
        }
    }

    @Test
    public void testTablesMixedList() {
        int boundA = rng.nextInt(23);
        int boundB = rng.nextInt(23);
        int start = Math.min(boundA, boundB);
        int end = Math.max(boundA, boundB);
        int idxC = rng.nextInt(23);
        String entry = String.format("* %d-%d,%d * * *", start, end, idxC);
        CronTabEntry cron = new CronTabEntry(entry);

        for (int i = 0; i < cron.hours.length; i++) {
            if (((i >= start) && (i <= end)) || i == idxC) {
                assertTrue(cron.hours[i]);
            } else {
                assertFalse(cron.hours[i]);
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
