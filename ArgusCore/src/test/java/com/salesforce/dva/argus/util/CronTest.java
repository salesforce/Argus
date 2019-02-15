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
import java.util.Date;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;

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

    private final static
    String[] inputCrons = {
            "* * * * *",
            "* * ? * 2-6",
            "* */1 * * *",
            "* */12 * * *",
            "* */15 * * *",
            "* */2 * * *",
            "* */3 * * *",
            "* */4 * * *",
            "* */6 * * *",
            "* */8 * * *",
            "* 0-12 ? * 2-6",
            "* 11-23 ? * 2-6",
            "* 12-0 ? * 2-6",
            "* 21-11 ? * 2-6",
            "* 21-9 ? * 2-6",
            "* 4-18 ? * 2-6",
            "* 5-17 ? * 2-6",
            "* 6-18 ? * 2-6",
            "*/1 * * * *",
            "*/10 * * * *",
            "*/10 * ? * 2-6",
            "*/10 0-12 ? * 2-6",
            "*/10 11-23 ? * 2-6",
            "*/10 12-0 ? * 2-6",
            "*/10 21-11 ? * 2-6",
            "*/10 21-9 ? * 2-6",
            "*/10 4-18 ? * 2-6",
            "*/10 5-17 ? * 2-6",
            "*/10 6-18 ? * 2-6",
            "*/11 * * * *",
            "*/12 */5 * * *",
            "*/13 * * * *",
            "*/15 * * * *",
            "*/17 * * * *",
            "*/19 * * * *",
            "*/2 * * * *",
            "*/20 * * * *",
            "*/23 * * * *",
            "*/25 * * * *",
            "*/29 * * * *",
            "*/3 * * * *",
            "*/3 * ? * 2-6",
            "*/3 11-23 ? * 2-6",
            "*/3 12-0 ? * 2-6",
            "*/3 21-11 ? * 2-6",
            "*/3 21-9 ? * 2-6",
            "*/3 4-18 ? * 2-6",
            "*/3 5-17 ? * 2-6",
            "*/3 6-18 ? * 2-6",
            "*/30 * * * *",
            "*/30 * ? * 1-5",
            "*/30 * ? * 2-6",
            "*/30 */6 * * *",
            "*/30 0-12 ? * 2-6",
            "*/30 11-21 ? * 2-6",
            "*/30 11-23 ? * 1-5",
            "*/30 11-23 ? * 2-6",
            "*/30 12-0 ? * 1-5",
            "*/30 12-0 ? * 2-6",
            "*/30 17-5 ? * 2-6",
            "*/30 18-4 ? * 2-6",
            "*/30 18-6 ? * 2-6",
            "*/30 21-11 ? * 1-5",
            "*/30 21-11 ? * 2-6",
            "*/30 21-9 ? * 1-5",
            "*/30 21-9 ? * 2-6",
            "*/30 23-11 ? * 2-6",
            "*/30 4-18 ? * 1-5",
            "*/30 4-18 ? * 2-6",
            "*/30 5-17 ? * 1-5",
            "*/30 5-17 ? * 2-6",
            "*/30 6-18 ? * 1-5",
            "*/30 6-18 ? * 2-6",
            "*/30 9-21 ? * 2-6",
            "*/31 * * * *",
            "*/35 * * * *",
            "*/4 * * * *",
            "*/40 * * * *",
            "*/45 * * * *",
            "*/5 * * * *",
            "*/5 * ? * 2-6",
            "*/5 0-12 ? * 2-6",
            "*/5 11-23 ? * 2-6",
            "*/5 12-0 ? * 2-6",
            "*/5 21-11 ? * 2-6",
            "*/5 21-9 ? * 2-6",
            "*/5 4-18 ? * 2-6",
            "*/5 5-17 ? * 2-6",
            "*/5 6-18 ? * 2-6",
            "*/53 * * * *",
            "*/55 * ? * 2-6",
            "*/55 0-12 ? * 2-6",
            "*/55 11-23 ? * 2-6",
            "*/55 12-0 ? * 2-6",
            "*/55 21-11 ? * 2-6",
            "*/55 21-9 ? * 2-6",
            "*/55 4-18 ? * 2-6",
            "*/55 5-17 ? * 2-6",
            "*/55 6-18 ? * 2-6",
            "*/59 * * * *",
            "*/6 * * * *",
            "*/7 * * * *",
            "*/9 * * * *",
            "/10 * * * *",
            "0 * * * *",
            "0 */1 * * *",
            "0 */10 * * *",
            "0 */12 * * *",
            "0 */15 * * *",
            "0 */19 * * *",
            "0 */2 * * *",
            "0 */3 * * *",
            "0 */4 * * *",
            "0 */5 * * *",
            "0 */6 * * *",
            "0 */8 * * *",
            "0 0 * * *",
            "0 1 * * *",
            "0 1,7,13,19 * * *",
            "0 10 * * *",
            "0 11 * * *",
            "0 14 * * *",
            "0 16 * * *",
            "0 16 */1 * *",
            "0 17 * * *",
            "0 18 * * *",
            "0 2 * * *",
            "0 2,14 * * *",
            "0 20 * * *",
            "0 3,15 * * *",
            "0 4,16 * * *",
            "0 5 * * *",
            "0 6 * * *",
            "0 7 * * *",
            "0 8 * * *",
            "0 8,17 * * *",
            "0,10,20,30,40,50 * * * *",
            "0,15,30,45 * * * *",
            "0/1 * * * *",
            "0 0 1 1 *",  // Anually
            "0 0 1 * *",  // Monthly
            "0 0 * * 1",  // Weekly
            "0 0 * * *",  // Daily or Midnight
            "0 * * * *",  // hourly
    };

    String[] invalidInputCrons = {
            "0 0 * * 0",  // used as Weekly  // not valid - fix DB? might be no instances
            "0 0 * * 0", // not valid - found in DB
    };

    // TODO - contact teams using these and have them fix up the crons - in Quartz these are Sun-Thu but it looks
    //        like the intent may be Mon-Fri
    // TODO - FIRST you must check that the UI validator maps 1-7 as Sun-Sat as documented. If it can't be restricted, add back the 0->1 mapping for dow,dom
    String[] wrongCrons = {
            "*/30 * ? * 1-5",
            "*/30 11-23 ? * 1-5",
            "*/30 12-0 ? * 1-5",
            "*/30 21-11 ? * 1-5",
            "*/30 21-9 ? * 1-5",
            "*/30 4-18 ? * 1-5",
            "*/30 5-17 ? * 1-5",
            "*/30 6-18 ? * 1-5",
    };

    @Test
    public void testConvertCronEntryToQuartzCronEntry() {

        assertEquals(Cron.convertToQuartzCronEntry("0 0 * * 0"), "0 0 0 ? * 0");  // TODO - not valid unless we restore 0->1 for dow,dom
        assertEquals(Cron.convertToQuartzCronEntry("0 0 0 * 0"), "0 0 0 0 * 0");  // NOTE - not valid for quartz unless we restore 0->1 for dow,dom
        assertEquals(Cron.convertToQuartzCronEntry(" * 5-17 * * * "), "0 * 5-17 ? * *");
        assertEquals(Cron.convertToQuartzCronEntry("* 5-17 * * 1-5"), "0 * 5-17 ? * 1-5");
        assertEquals(Cron.convertToQuartzCronEntry("0 0 * *"), "0 0 0 * * ?");
        assertEquals(Cron.convertToQuartzCronEntry("0 */30 * * *"), "0 0 */30 ? * *");
        assertEquals(Cron.convertToQuartzCronEntry("* 5-17 * * 1-5"), "0 * 5-17 ? * 1-5");
        assertEquals(Cron.convertToQuartzCronEntry("* 5-17 ? * ?"), "0 * 5-17 ? * *");
        assertEquals(Cron.convertToQuartzCronEntry("* 5-17 ? * *"), "0 * 5-17 ? * *");

        // Special conversions
        assertEquals(Cron.convertToQuartzCronEntry("0 0 1 1 *"), "0 0 0 1 1 ?"); // ANNUALLY or YEARLY
        assertEquals(Cron.convertToQuartzCronEntry("0 0 1 * *"), "0 0 0 1 * ?"); // MONTHLY
        assertEquals(Cron.convertToQuartzCronEntry("0 0 * * 1"), "0 0 0 ? * 1"); // WEEKLY
        assertEquals(Cron.convertToQuartzCronEntry("0 0 * * *"), "0 0 0 ? * *"); // DAILY or MIDNIGHT
        assertEquals(Cron.convertToQuartzCronEntry("0 * * * *"), "0 0 * ? * *"); // HOURLY

        // Convert Argus alert cron expressions known in Dec 2018.
        for (int i = 0 ; i < inputCrons.length; i++)
        {
            String inputCron = inputCrons[i];
            String quartzCron = "";
            boolean converted = false;
            boolean isValid = false;
            try
            {
                quartzCron = Cron.convertToQuartzCronEntry(inputCrons[i]);
                converted = true;
                isValid = Cron.isCronEntryValid(inputCron);
                if (!isValid)
                    System.out.println(String.format("%d: '%s' -> '%s' is %sa valid quartz cron.", i, inputCron, quartzCron, isValid ? "": "NOT "));
            }
            catch (Exception e)
            {
                System.out.println(String.format("%d: '%s' -> '%s' is invalid or can't be converted to a quartz cron: %s", i, inputCron, quartzCron, e.getMessage()));
            }
            assertTrue(converted);
            assertTrue(isValid);
        }
    }

    @Test
    public void testValidCronEntry() {
        assertFalse(Cron.isCronEntryValid("0 0 * * 0"));   // NOT Valid Quartz TODO - if UI can't validate we need to re-enable conversion of dow, dom 0->1
        assertFalse(Cron.isCronEntryValid("0 0 0 * 0"));   // NOT Valid Quartz
        assertFalse(Cron.isCronEntryValid("0 0 1 * 0"));   // NOT Valid Quartz
        assertFalse(Cron.isCronEntryValid("0 0 1 * 1"));   // NOT Valid Quartz
        assertTrue(Cron.isCronEntryValid(" * 5-17 * * * "));

        assertTrue(Cron.isCronEntryValid("* 5-17 * * 1-5"));
        assertTrue(Cron.isCronEntryValid("* 5-17 ? * 1-5"));
        assertTrue(Cron.isCronEntryValid("* 5-17 ? * ?"));
        assertTrue(Cron.isCronEntryValid("* 5-17 * * ?"));
        assertTrue(Cron.isCronEntryValid("* 5-17 ? * *"));

        assertTrue(Cron.isCronEntryValid("0 0 * *"));

        // Special conversions
        assertTrue(Cron.isCronEntryValid("0 0 1 1 *")); // ANNUALLY or YEARLY
        assertTrue(Cron.isCronEntryValid("0 0 1 * *")); // MONTHLY
        assertTrue(Cron.isCronEntryValid("0 0 * * 1")); // WEEKLY  -> should become 0 0 0 ? * 1
        assertTrue(Cron.isCronEntryValid("0 0 * * *")); // DAILY or MIDNIGHT
        assertTrue(Cron.isCronEntryValid("0 * * * *")); // HOURLY

        for (int i = 1; i < 24; i++)
        {
            String cron=String.format("0 */%d * * *", i);
            assertTrue(Cron.isCronEntryValid(cron));
        }

        for (int i = 1; i < 60; i++)
        {
            String cron=String.format("*/%d 0 * * *", i);
            assertTrue(Cron.isCronEntryValid(cron));
        }

    }

    /*
    Old vs new cron translation test.
    Iterate through all known expressions.
    Iterate through some time period in 1 minute steps.
    Compare old vs new implementation.

     */

    // This is the original conversion function which we are using for comparison with the new one.
    private static String convertToOldQuartzCronEntry(String cronEntry) {
        // adding seconds field
        cronEntry = "0 " + cronEntry.trim();

        // if day of the week is not specified, substitute it with ?, so as to prevent conflict with month field
        if(cronEntry.charAt(cronEntry.length() - 1) == '*') {
            return cronEntry.substring(0, cronEntry.length() - 1) + "?";
        }else {
            return cronEntry;
        }
    }

    private CronTrigger makeCronTrigger(String quartzExpr)
    {
        CronTrigger trigger = null;
        try
        {
            trigger = TriggerBuilder.newTrigger().
                    withSchedule(CronScheduleBuilder.cronSchedule(quartzExpr)).
                    build();
        }
        catch (Exception e) {}
        return trigger;
    }


    private boolean compareTranslations(String cronExpr, int steps)
    {
        boolean success = true;
        String oldQuartz = null;
        String newQuartz = null;
        CronTrigger oldTrigger = null;
        CronTrigger newTrigger = null;
        Date oldPrev = null;
        Date newPrev = null;
        Date oldWhen = null;
        Date newWhen = null;
        int i = 0;

        try
        {
            oldQuartz = convertToOldQuartzCronEntry(cronExpr);
            newQuartz = Cron.convertToQuartzCronEntry(cronExpr);

            oldTrigger = makeCronTrigger(oldQuartz);
            newTrigger = makeCronTrigger(newQuartz);

            if (oldTrigger == null)
            {
                System.out.println(String.format("Old Translation of Cron expr '%s' was NOT VALID. Skipping comparison...", cronExpr));
            }
            else
            {
                assertNotNull(newTrigger);

                oldWhen = new Date();
                newWhen = (Date) oldWhen.clone();

                for (i = 0; i < steps; i++)
                {
                    oldPrev = (Date) oldWhen.clone();
                    newPrev = (Date) newWhen.clone();

                    oldWhen = oldTrigger.getFireTimeAfter(oldWhen);
                    newWhen = newTrigger.getFireTimeAfter(newWhen);

                    if (oldWhen == null || newWhen == null)
                    {
                        System.out.println("Reached maximum time, end of comparison.");
                        assertTrue( oldWhen == newWhen );
                        break;
                    }

                    boolean isSame = oldWhen.equals(newWhen);

                    if (!isSame)
                    {
                        System.out.println(String.format("Testing Cron '%s' -> old = '%s', new = '%s' --> Comparison failed at step %d", cronExpr, oldQuartz, newQuartz, i ));
                        System.out.println(String.format("%s %s %s", i,
                                oldWhen.toString(), isSame ? "==" : "!=", newWhen.toString()));
                        success = false;
                        break;
                    }
                }
            }
        }
        catch (RuntimeException e)
        {
            System.out.println(String.format("Error in test: %s", e.getMessage()));
            e.printStackTrace();
            success = false;
        }

        return success;
    }

    @Test
    public void verifyCronTranslation() throws Exception
    {
        boolean allPassed = true;

        for (int i = 0; i < inputCrons.length; i++) {
            boolean passed = compareTranslations(inputCrons[i], 10000);
            allPassed = passed && allPassed;
        }

        assertTrue(allPassed);
    }
}
/* Copyright (c) 2016-2019, Salesforce.com, Inc.  All rights reserved. */
