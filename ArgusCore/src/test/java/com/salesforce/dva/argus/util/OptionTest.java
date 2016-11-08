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
import java.util.Arrays;
import java.util.TreeMap;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class OptionTest {

    private static Option[] templates;
    private static Option[] failTemplates;

    public OptionTest() { }

    public static Option[] getFailTemplates() {
        return new Option[] {
                Option.createFlag("-a", "a"), Option.createOption("-b", "b"), Option.createOption("-c", 5, "c"), Option.createOption("-d", 2, "d")
            };
    }

    public static Option[] getTemplates() {
        return new Option[] {
                Option.createFlag("-a", "a"), Option.createOption("-b", "b"), Option.createOption("-c", 5, "c"), Option.createOption("-d", 2, "d"),
                Option.createList("e")
            };
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
        templates = getTemplates();
        failTemplates = getFailTemplates();
    }

    @Test
    public void testBadParse() {
        try {
            Option.parseCLArgs("-z".split("\\s+"), failTemplates);
        } catch (IllegalArgumentException ex) {
            return;
        }
        fail("Unexpected command line args should through an IllegalArgumentException");
    }

    @Test
    public void testExerciseForCoverage() {
        Option[] options = Option.parseCLArgs("-a -b b -c c c c c c -d d d".split("\\s+"), templates);

        assertNull(Option.findOption("-z", options));
        assertNull(Option.findListOption(options));
        assertTrue("a".equals(Option.findOption("-a", options).getDescription()));
        assertTrue(Option.findOption("-a", options).getValues().length == 0);
        assertTrue("".equals(Option.findOption("-a", options).getValue()));
    }

    @Test
    public void testFlagAfterListFail() {
        try {
            Option.parseCLArgs("-a e f g -a".split("\\s+"), templates);
        } catch (IllegalArgumentException ex) {
            return;
        }
        fail("List options (which have no identifier) must occur after all other flags having an identifier.");
    }

    @Test
    public void testOptionTypes() {
        TreeMap<String, Option.Type> values = new TreeMap<String, Option.Type>();

        for (final Option.Type type : Option.Type.values()) {
            values.put(type.toString(), type);
        }
        assertTrue(values.containsKey("FLAG"));
        assertTrue(values.containsKey("OPTION"));
        assertTrue(values.containsKey("LIST"));
        assertTrue(values.size() == 3);
        assertTrue(Option.Type.valueOf("FLAG").equals(values.get("FLAG")));
        assertTrue(Option.Type.valueOf("OPTION").equals(values.get("OPTION")));
        assertTrue(Option.Type.valueOf("LIST").equals(values.get("LIST")));
    }

    @Test
    public void testParseCLArgs() {
        Option[] options = Option.parseCLArgs("-a -b b -c c c c c c -d d d e f g".split("\\s+"), templates);
        Option a = Option.findOption("-a", options);

        assertTrue("".equals(a.getValue()));

        Option b = Option.findOption("-b", options);

        assertTrue("b".equals(b.getValue()));
        assertTrue("b".equals(b.getValues()[0]));
        assertTrue(b.getValues().length == 1);

        Option c = Option.findOption("-c", options);

        assertTrue("c c c c c".equals(c.getValue()));
        assertTrue(Arrays.equals(new String[] { "c", "c", "c", "c", "c" }, c.getValues()));
        assertTrue(c.getValues().length == 5);

        Option d = Option.findOption("-d", options);

        assertTrue("d d".equals(d.getValue()));
        assertTrue(Arrays.equals(new String[] { "d", "d" }, d.getValues()));
        assertTrue(d.getValues().length == 2);

        Option list = Option.findListOption(options);

        assertTrue("e f g".equals(list.getValue()));
        assertTrue(Arrays.equals(new String[] { "e", "f", "g" }, list.getValues()));
        assertTrue(list.getValues().length == 3);
    }

    @Test
    public void testToString() {
        Option list = Option.createList("list");

        assertEquals("INT_LIST", list.toString());
        assertEquals("", list.getValue());
        assertArrayEquals(new String[] {}, list.getValues());
        list.setValue("a b c");
        assertEquals("INT_LIST:a b c", list.toString());
        assertArrayEquals(new String[] { "a", "b", "c" }, list.getValues());

        Option flag = Option.createFlag("flag", "flag");

        assertEquals("flag", flag.toString());
        assertEquals("", flag.getValue());
        assertArrayEquals(new String[] {}, flag.getValues());
        flag.setValue("a");
        assertEquals("flag:a", flag.toString());
        assertArrayEquals(new String[] { "a" }, flag.getValues());

        Option option = Option.createOption("option", "option");

        assertEquals("option", option.toString());
        assertEquals("", option.getValue());
        assertArrayEquals(new String[] {}, option.getValues());
        option.setValue("a");
        assertEquals("option:a", option.toString());
        assertArrayEquals(new String[] { "a" }, option.getValues());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
