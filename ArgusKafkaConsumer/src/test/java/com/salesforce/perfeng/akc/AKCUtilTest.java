package com.salesforce.perfeng.akc;

import static org.junit.Assert.*;



import org.junit.Test;


import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;



public class AKCUtilTest {
    @Test
    public void testReplaceUnsupportedChars() {
        String expected = "mytag1";
        String actual = replaceUnsupportedChars("mytag1");
        assertEquals(expected, actual);

        expected = "ANCESTOR_DOMAINS__ANCESTORS";
        actual = replaceUnsupportedChars("ANCESTOR_DOMAINS|ANCESTORS");
        assertEquals(expected, actual);

        expected = "OU__0";
        actual = replaceUnsupportedChars("OU=0");
        assertEquals(expected, actual);

        expected = "url__//";
        actual = replaceUnsupportedChars("url://");
        assertEquals(expected, actual);

        expected = "ANCESTOR_DOMAINS__ANCESTORS";
        actual = replaceUnsupportedChars("ANCESTOR_DOMAINS__ANCESTORS");
        assertEquals(expected, actual);

        expected = "ANCESTOR_DOMAINS____ANCESTORS";
        actual = replaceUnsupportedChars("ANCESTOR_DOMAINS__|ANCESTORS");
        assertEquals(expected, actual);
    }

    @Test
    public void resolveCharSequence() {
        assertEquals("abc", AKCUtil.resolveCharSequence(null, "abc", cs -> cs.toString().toUpperCase()));
        assertEquals("abc", AKCUtil.resolveCharSequence("", "abc", cs -> cs.toString().toUpperCase()));
        assertEquals("ABC", AKCUtil.resolveCharSequence("abc", "xyz", cs -> cs.toString().toUpperCase()));
    }
}
