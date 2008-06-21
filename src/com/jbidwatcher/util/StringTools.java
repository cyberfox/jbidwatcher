package com.jbidwatcher.util;

import com.jbidwatcher.util.config.ErrorManagement;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringTools {
  private static final int YEAR_BASE = 1990;
  private static GregorianCalendar sMidpointDate = new GregorianCalendar(YEAR_BASE, Calendar.JANUARY, 1);
  public static final int HIGHBIT_ASCII = 0x80;

  public static String decodeLatin(String latinString) {
    //  Why?  Because it seems to Just Work on Windows.  Argh.
    return decode(latinString, "ISO-8859-1");
  }

  public static String decode(String original, String charset) {
//    if (!JConfig.queryConfiguration("mac", "false").equals("true")) return original;
    if(charset == null) charset = "UTF-8";
    try {
      return new String(original.getBytes(), charset);
    } catch (UnsupportedEncodingException ignore) {
      return original;
    }
  }

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

  /**
   * @param sb         - The stringbuffer to delete from.
   * @param desc_start - The start point to delete from.
   * @param desc_end   - The endpoint to delete to.
   * @return - true if a deletion occurred, false if the parameters
   *         were invalid in any way.
   * @brief Delete characters from a range within a stringbuffer, safely.
   */
  public static boolean deleteRange(StringBuffer sb, int desc_start, int desc_end) {
    if (desc_start < desc_end &&
        desc_start != -1 &&
        desc_end != -1) {
      sb.delete(desc_start, desc_end);
      return true;
    }
    return false;
  }

  /**
   * @param sb          - The StringBuffer to delete from, In/Out.
   * @param startStr    - The start string to delete from.
   * @param altStartStr - An alternate start string, in case the startStr isn't found.
   * @param endStr      - The end string to delete to.
   * @param altEndStr   - An alternate end string in case the endStr is found before the start string.
   * @return - true if a delete occurred, false otherwise.
   * @brief Delete a block of text, indicated by a start and end
   * string pair, with alternates.
   */
  public static boolean deleteFirstToLast(StringBuffer sb, String startStr, String altStartStr, String endStr, String altEndStr) {
    String fullBuff = sb.toString();
    int desc_start = fullBuff.indexOf(startStr);

    if (desc_start == -1) {
      desc_start = fullBuff.indexOf(altStartStr);
    }

    int desc_end = fullBuff.lastIndexOf(endStr);

    if (desc_start > desc_end) desc_end = fullBuff.lastIndexOf(altEndStr);

    return deleteRange(sb, desc_start, desc_end);
  }

  /**
   * @param sb       - The buffer to delete from.
   * @param startStr - The string to delete starting at.
   * @param endStr   - The string to delete up until.
   * @return - true if the delete happened, false otherwise.
   * @brief Simple utility to delete from a stringbuffer starting
   * from a string, until the next following string.
   */
  public static boolean deleteRegexPair(StringBuffer sb, String startStr, String endStr) {
    Matcher start = Pattern.compile(startStr, Pattern.CASE_INSENSITIVE).matcher(sb);
    Matcher end = Pattern.compile(endStr, Pattern.CASE_INSENSITIVE).matcher(sb);

    if (start.find() &&
        end.find(start.start() + 1)) {
      int desc_start = start.start();
      int desc_end = end.end();

      return deleteRange(sb, desc_start, desc_end);
    }
    return false;
  }

  public static ZoneDate figureDate(String rawTime, String siteDateFormat) {
      return figureDate(rawTime, siteDateFormat, true, true);
  }

  /**
   * @param endTime        - The string containing the human-readable time to be parsed.
   * @param siteDateFormat - The format describing the human-readable time.
   * @param strip_high     - Whether or not to strip high characters.
   * @param ignore_badformat - Return null if the date is in a bad format.
   *
   * @return - The date/time in Date format that was represented by
   *         the human readable date string.
   * @brief Use the date parsing code to figure out the time an
   * auction ends (also used to parse the 'official' time) from the
   * web page.
   */
  public static ZoneDate figureDate(String endTime, String siteDateFormat, boolean strip_high, boolean ignore_badformat) {
    String endTimeFmt = endTime;
    SimpleDateFormat sdf = new SimpleDateFormat(siteDateFormat, Locale.US);

    sdf.set2DigitYearStart(sMidpointDate.getTime());

    if (endTime == null) return sNullZoneDate;

    if (strip_high) {
      endTimeFmt = StringTools.stripHigh(endTime, siteDateFormat);
    }
    return parseDateZone(sdf, endTimeFmt, ignore_badformat);
  }

  private static final ZoneDate sNullZoneDate = new ZoneDate(null, null);

  private static ZoneDate parseDateZone(SimpleDateFormat sdf, String endTimeFmt, boolean ignore_badformat) {
    Date endingDate;
    TimeZone tz;

    try {
      endingDate = sdf.parse(endTimeFmt);
      tz = sdf.getCalendar().getTimeZone();
    } catch (java.text.ParseException e) {
      if(!ignore_badformat) {
        ErrorManagement.handleException("Error parsing date (" + endTimeFmt + "), setting to completed.", e);
        endingDate = new Date();
      } else {
        endingDate = null;
      }
      tz = null;
    }
    return (new ZoneDate(tz, endingDate));
  }

  public static String cat(URL loadFrom) {
    if(loadFrom == null) return null;
    byte[] buf = new byte[65536];
    try {
      InputStream is = loadFrom.openStream();
      int bytes_read = is.read(buf);
      if(bytes_read == buf.length) {
        ErrorManagement.logDebug("File to load is exactly 64K or larger than 64K.  This method does not support that.");
        return null;
      }
      return new String(buf, 0, bytes_read);
    } catch (IOException e) {
      ErrorManagement.handleException("Failed to load " + loadFrom.toString(), e);
    }
    return null;
  }

  public static String cat(String filename) {
    File fp = new File(filename);
    byte[][] buffer = new byte[1][];

    cat(fp, buffer);

    return new String(buffer[0]);
  }

  public static void cat(File fp, byte[][] buf) {
    try {
      buf[0] = new byte[(int)fp.length()];
      FileInputStream fis = new FileInputStream(fp);
      int read = fis.read(buf[0], 0, (int)fp.length());
      if(read != fp.length()) ErrorManagement.logDebug("Couldn't read any data from " + fp.getName());
      fis.close();
    } catch(IOException e) {
      ErrorManagement.handleException("Can't read file " + fp.getName(), e);
    }
  }

  public static String comma(Object[] list) {
    boolean first = true;
    String rval = "";
    if (list == null || list.length == 0) return rval;
    for (Object o : list) {
      if (!first) rval += ", ";
      else first = false;
      rval += o.toString();
    }

    return rval;
  }

  public static boolean startsWithIgnoreCase(String base, String match) {
    return base.regionMatches(true, 0, match, 0, match.length());
  }

  public static String stripHigh(String inString) {
    char[] stripOut = new char[inString.length()];

    inString.getChars(0, inString.length(), stripOut, 0);
    for(int i=0; i<stripOut.length; i++) {
      if(stripOut[i] > 0x80) stripOut[i] = ' ';
    }
    return new String(stripOut);
  }
}
