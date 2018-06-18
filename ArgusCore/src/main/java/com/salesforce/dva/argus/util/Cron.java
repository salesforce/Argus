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

import java.util.Date;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.quartz.CronScheduleBuilder;
import org.quartz.TriggerBuilder;

/**
 * CRON utilities.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Cron {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String ANNUALLY = "@ANNUALLY";
    private static final String YEARLY = "@YEARLY";
    private static final String MONTHLY = "@MONTHLY";
    private static final String WEEKLY = "@WEEKLY";
    private static final String DAILY = "@DAILY";
    private static final String MIDNIGHT = "@MIDNIGHT";
    private static final String HOURLY = "@HOURLY";

    //~ Constructors *********************************************************************************************************************************

    private Cron() { }

    //~ Methods **************************************************************************************************************************************

    /**
     * Determines if the given CRON entry is runnable at this current moment in time. This mimics the original implementation of the CRON table.
     *
     * <p>This implementation supports only the following types of entries:</p>
     *
     * <ol>
     *   <li>Standard Entries having form: &lt;minutes&gt; &lt;hours&gt; &lt;days&gt; &lt;months&gt; &lt;days of week&gt;
     *
     *     <ul>
     *       <li>* : All</li>
     *       <li>*\/n : Only mod n</li>
     *       <li>n : Numeric</li>
     *       <li>n-n : Range</li>
     *       <li>n,n,...,n : List</li>
     *       <li>n,n-n,...,n : List having ranges</li>
     *     </ul>
     *   </li>
     *   <li>Special Entries
     *
     *     <ul>
     *       <li>@annually : equivalent to "0 0 1 1 *"</li>
     *       <li>@yearly : equivalent to "0 0 1 1 *"</li>
     *       <li>@monthly : equivalent to "0 0 1 * *"</li>
     *       <li>@weekly : equivalent to "0 0 * * 0"</li>
     *       <li>@daily : equivalent to "0 0 * * *"</li>
     *       <li>@midnight : equivalent to "0 0 * * *"</li>
     *       <li>@hourly : equivalent to "0 * * * *"</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param   entry   The CRON entry to evaluate.
     * @param   atTime  The time at which to evaluate the entry.
     *
     * @return  true if the the current time is a valid runnable time with respect to the supplied entry.
     */
    public static boolean shouldRun(String entry, Date atTime) {
        entry = entry.trim().toUpperCase();
        if (ANNUALLY.equals(entry) || (YEARLY.equals(entry))) {
            entry = "0 0 1 1 *";
        } else if (MONTHLY.equals(entry)) {
            entry = "0 0 1 * *";
        } else if (WEEKLY.equals(entry)) {
            entry = "0 0 * * 0";
        } else if (DAILY.equals(entry) || (MIDNIGHT.equals(entry))) {
            entry = "0 0 * * *";
        } else if (HOURLY.equals(entry)) {
            entry = "0 * * * *";
        }
        return new CronTabEntry(entry).isRunnable(atTime);
    }

    /**
     * Indicates if a CRON entry should run at the current moment in time.
     *
     * @param   entry  The CRON entry to evaluate.
     *
     * @return  true if the the current time is a valid runnable time with respect to the supplied entry.
     */
    public static boolean shouldRun(String entry) {
        return Cron.shouldRun(entry, new Date());
    }

    /**
     * Determines if an entry is valid CRON syntax.
     *
     * @param   entry  The CRON entry.
     *
     * @return  True if the entry is valid CRON syntax.
     */
    public static boolean isValid(String entry) {
        boolean result = true;

        try {
            shouldRun(entry);
        } catch (Exception ex) {
            result = false;
        }
        return result;
    }
    
	public static boolean isCronEntryValid(String cronEntry) {
		String quartzCronEntry = convertToQuartzCronEntry(cronEntry);

		try {
			// throws runtime exception if the cronEntry is invalid
			TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(quartzCronEntry)).build();
		}catch(Exception e) {
			return false;
		}
		return true;
	}
	
    public static String convertToQuartzCronEntry(String cronEntry) {
       	return "0 " + cronEntry.substring(0, cronEntry.length() - 1) + "?";
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
