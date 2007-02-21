package com.jbidwatcher.util;

import java.net.URL;
import java.net.MalformedURLException;

public class StringTools {
  public static final int HIGHBIT_ASCII = 0x80;

  public static String stripHigh(String inString, String fmtString) {
    char[] stripOut = new char[inString.length()];

    inString.getChars(0, inString.length(), stripOut, 0);
    char[] format = new char[fmtString.length()];
    fmtString.getChars(0, fmtString.length(), format, 0);
    String legalString = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-:,";
    for(int i=0; i<stripOut.length; i++) {
      if(stripOut[i] > HIGHBIT_ASCII) stripOut[i] = ' ';

      if(i < format.length) {
        if( (format[i] == ' ') && (legalString.indexOf(stripOut[i]) == -1)) {
            stripOut[i] = ' ';
        }
      }
    }
    return new String(stripOut);
  }

  /**
   * @brief Convert an auction item description URL in String format into a java.net.URL.
   * This is a brutally simple utility function, so it's static, and should be referred
   * to via the AuctionServer class directly.
   *
   * @param siteAddress - The string URL to convert into a 'real' URL on the given site.
   *
   * @return - A java.net.URL that points to what we consider the 'official' item URL on the site.
   */
  public static URL getURLFromString(String siteAddress) {
    URL auctionURL=null;

    try {
      auctionURL = new URL(siteAddress);
    } catch(MalformedURLException e) {
      ErrorManagement.handleException("getURLFromString failed on " + siteAddress, e);
    }

    return auctionURL;
  }

  /**
   * @brief Determine if the provided string is all digits, a commonly
   * needed check for auction ids.
   *
   * @param checkVal - The string to check for digits.
   *
   * @return - true if all characters in checkVal are digits, false
   * otherwise or if the string is empty.
   */
  public static boolean isNumberOnly(String checkVal) {
    int strLength = checkVal.length();

    if(strLength == 0) return(false);

    for(int i = 0; i<strLength; i++) {
      if(!Character.isDigit(checkVal.charAt(i))) return(false);
    }

    return(true);
  }
}
