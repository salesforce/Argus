package com.salesforce.dva.argus.entity;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TSDBEntityTest {
	@Test
	public void testReplaceUnsupportedChars() {
	    String expected = "mytag1";
	    String actual = TSDBEntity.replaceUnsupportedChars("mytag1");
	    assertEquals(expected, actual);
	    
	    expected = "ANCESTOR_DOMAINS__ANCESTORS";
	    actual = TSDBEntity.replaceUnsupportedChars("ANCESTOR_DOMAINS|ANCESTORS");
	    assertEquals(expected, actual);
	    
	    expected = "OU__0";
	    actual = TSDBEntity.replaceUnsupportedChars("OU=0");
	    assertEquals(expected, actual);
	    
	    expected = "url__//";
	    actual = TSDBEntity.replaceUnsupportedChars("url://");
	    assertEquals(expected, actual);
	    
	    expected = "ANCESTOR_DOMAINS__ANCESTORS";
	    actual = TSDBEntity.replaceUnsupportedChars("ANCESTOR_DOMAINS__ANCESTORS");
	    assertEquals(expected, actual);
	    
	    expected = "ANCESTOR_DOMAINS____ANCESTORS";
	    actual = TSDBEntity.replaceUnsupportedChars("ANCESTOR_DOMAINS__|ANCESTORS");
	    assertEquals(expected, actual);
	    
	    expected = "";
	    actual = TSDBEntity.replaceUnsupportedChars("");
	    assertEquals(expected, actual);
	    
	    expected = null;
	    actual = TSDBEntity.replaceUnsupportedChars(null);
	    assertEquals(expected, actual);
	}
}