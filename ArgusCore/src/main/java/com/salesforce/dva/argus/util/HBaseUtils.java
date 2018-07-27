package com.salesforce.dva.argus.util;

public class HBaseUtils {

	/**
	 * Return a 9's complement of the given timestamp. Since AsyncHBase doesn't allow a reverse scan and we want to scan data in descending order
	 * of creation time.  
	 * 
	 * @param 	creationTime	creation time
	 * @return 	The 9's complement of the given timestamp. 
	 */
	public static long _9sComplement(long creationTime) {
		String time = String.valueOf(creationTime);
		char[] timeArr = time.toCharArray();
		StringBuilder sb = new StringBuilder();
		for(char c : timeArr) {
			sb.append(9 - Character.getNumericValue(c));
		}
		
		return Long.parseLong(sb.toString());
	}
	
	
	public static String _plusOne(String prefix) {
		char newChar = (char) (prefix.charAt(prefix.length() - 1) + 1);
    	    String end = prefix.substring(0, prefix.length() - 1) + newChar;
		return end;
	}
}
