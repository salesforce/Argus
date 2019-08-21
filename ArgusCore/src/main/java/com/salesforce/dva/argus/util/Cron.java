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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.quartz.CronScheduleBuilder;
import org.quartz.TriggerBuilder;

/**
 * CRON utilities.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Cron {

	//~ Constructors *********************************************************************************************************************************

	private Cron() { }

	//~ Methods **************************************************************************************************************************************

	public static boolean isCronEntryValid(String cronEntry) {

		try {
			String quartzCronEntry = convertToQuartzCronEntry(cronEntry);
			TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(quartzCronEntry)).build();
		} catch(Exception e) {
			return false;
		}
		return true;
	}

	// Quartz Cron fields:
	// Seconds = 0
	// Minutes = 1
	// Hours   = 2
	private static final int DayOfMonth = 3;
	// Month   = 4
	private static final int DayOfWeek = 5;
	// Year    = 6

	/*  @name: convertToQuartzCronEntry
	    @description: Argus accepts and stores 5 field Quartz Cron entries because it is not designed to schedule
	                  alert evaluation more often than once per minute.  This function normalizes 5 
	                  field cron entries to 6 field cron entries by prepending the seconds field, and is a bit more
	                  permissive in accepting * where ? should be used in valid Quartz cron entries.

	    @param: cronEntry: A Quartz Cron Entry with seconds absent (normally 5 fields).
	               Day of week may also be absent (4 fields).  Year is optional.
	               Does NOT support special values (@yearly, @monthly, @daily, etc.)

	    @returns: A valid Quartz Cron entry.
	 */  
	// TODO - rename to convertArgusCronToQuartzCron(), normalizeArgusQuartzCron(), etc.

	public static String convertToQuartzCronEntry(String cronEntry) {

		String tmpCron = "0 " + cronEntry.trim();

		List<String> parts = new ArrayList<String>(Arrays.asList(tmpCron.split("\\s+")));
		if (parts.size() < 5 || parts.size() > 7 )
		{
			throw new RuntimeException("Invalid input cron expression: " + cronEntry + ", too many or too few fields");
		}

		// if day of week is not specified, add '?' so as to prevent conflict with the month field.
		if (parts.size() == 5) {
			parts.add("?");
		}

		// Quartz doesn't support specification of both DOM and DOW, but for some reason it thinks that
		// * is an explicit specification in these context, whereas ? is handled as any.
		// Translation table: dom,dow -> dom,dow
		// -------------------------------------------------
		//  DOM         *            ?            X
		//  DOW *     {?,*}         {*,?}        {X,?}
		//      ?     {*,?}         {?,*}        {X,?}
		//      X     {?,X}         {?,X}        {X,X}


		String dom   = parts.get(DayOfMonth);
		String dow   = parts.get(DayOfWeek);

		// NOTE - no adjustments to dom, dow because alerts can be modified by calls to the UI and the WS.

		if ( dow.equals("*") && dom.equals("*"))
		{
			dom = "?";
		}
		else if ( dow.equals("*") && !dom.equals("?") && !dom.equals("*"))
		{
			dow = "?";
		}
		else if (dom.equals("*") && !dow.equals("?") && !dow.equals("*"))
		{
			dom = "?";
		}
		else if (dom.equals("?") && dow.equals("?"))
		{
			dow = "*";
		}

		parts.set(DayOfMonth, dom);
		parts.set(DayOfWeek, dow);


		String quartzCron = String.join(" ", parts);
		return quartzCron;
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
