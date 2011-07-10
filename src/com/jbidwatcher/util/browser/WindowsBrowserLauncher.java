package com.jbidwatcher.util.browser;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;

/*
 * WindowsBrowserLauncher provides Windows-specific registry-based
 * default browser launching if available, but uses reflection, so
 * it can run under non-Windows, non-1.4.x Java versions.
 * <p>
 * It's a companion to the BrowserLauncher class, modified slightly
 * to support a separate way to determine the default browser if
 * running under Windows.
 * <o>
 * This code is Copyright 2001-2004 by Morgan Schweers (cyberfox@jbidwatcher.com) and may be
 * redistributed or modified in any form without restrictions as long as the portion of this
 * comment from this paragraph through the end of the comment is not removed.  The author
 * requests that he be notified of any application, applet, or other binary that makes use of
 * this code, but that's more out of curiosity than anything and is not required.  This software
 * includes no warranty.  The author is not repsonsible for any loss of data or functionality
 * or any adverse or unexpected effects of using this software.
 * <p>
 * Credits:
 * Spawned to improve the behavior of the code from
 *         http://browserlauncher.sourceforge.net
 * under Windows, so I owe a great deal to Eric Albert
 *
 * @author Morgan Schweers (<a href="mailto:cyberfox@jbidwatcher.com">cyberfox@jbidwatcher.com</a>)
 * @version 1.0 (Released January 30, 2004)
 */
public class WindowsBrowserLauncher {
  /* Windows security masks */
  private final static int KEY_QUERY_VALUE = 1;

  /* Constants used to interpret returns of native functions    */
  private final static int NATIVE_HANDLE = 0;
  private final static int ERROR_CODE = 1;

  /*  Windows error codes. */
  private final static int ERROR_SUCCESS = 0;

  private final static boolean _debug = false;

  public static String replace(String inSource, String search, String replacement) {
    String result = inSource;
    int srch_index = result.indexOf(search);

    while(srch_index != -1) {
      result = result.substring(0,srch_index) +
        replacement +
        result.substring(srch_index + search.length());

      srch_index = result.indexOf(search, srch_index + replacement.length());
    }

    return result;
  }

  /** 
   * Split a command line into component parts (path to program,
   * parameters, etc.) supporting quoted strings (for parameters with
   * spaces in them), and one extra parameter (%1), which will be
   * quoted if it's not already.
   * 
   * @param cmd - The command to split out and build an array from.
   * @param replace - A string to replace any "%1" or %1 parameter with.
   *
   * @return - An array of String values which are directly passed to Runtime.exec(...)
   */
  public static String[] splitCommandLine(String cmd, String replace) {
    cmd = replace(cmd, "\\", "\\\\");
    StreamTokenizer str = new StreamTokenizer(new StringReader(cmd));
    List<String> result = new ArrayList<String>();
    boolean found_param = false;

    str.resetSyntax();
    str.wordChars(0,255);
    str.whitespaceChars(32,32);
    str.quoteChar('\"');

    try {
      while(str.nextToken() != StreamTokenizer.TT_EOF) {
        result.add(str.sval);
        if(str.sval.equals("\"%1\"") ||
           str.sval.equals("%1")) {
          found_param = true;
        }
      }
    } catch(IOException ioe) {
      //  Can't catch an IO exception from a String!
    }

    String[] output;
    if(found_param || replace == null) {
      output = new String[result.size()];
    } else {
      output = new String[result.size() + 1];
    }

    for(int i=0; i<result.size(); i++) {
      output[i] = result.get(i);
      if(replace != null) {
        if(output[i].equals("\"%1\"") ||
           output[i].equals("%1")) {
          output[i] = "\"" + replace + "\"";
        }
      }
    }

    if(!found_param && replace != null) {
      output[result.size()] = "\"" + replace + "\"";
    }

    return output;
  }

  public static String[] splitCommandLine(String cmd) { return splitCommandLine(cmd, null); }

  public static String getIndirect(String primary) {
    String clientName, command;

    clientName = getKeyDefault("HKEY_CLASSES_ROOT\\\\" + primary);
    if(clientName == null) return null;
    
    command = getKeyDefault("HKEY_CLASSES_ROOT\\\\" + clientName + "\\shell\\open\\command");
    return command;
  }

  /** 
   * Under the Windows registry, each key (path) has a default value.
   * Given a key, return the default string value.
   * 
   * @param key - The key (HKEY_CLASSES_ROOT\\http\shell\open\command)
   * to get the default value of.
   * 
   * @return - A string representation of the default value for the
   * given key.  Will return 'null' if the JVM doesn't support
   * registry operations.
   */
  public static String getKeyDefault(String key) {
    try {
      int hkey = getHKEY(key);
      byte[] rootpath = stripHKEY(key);
      return getValue(hkey, rootpath, "");
    } catch(Exception e) {
      //  For debugging, if necessary.
      if(_debug) {
        e.printStackTrace();
      }
      return null;
    }
  }

  /** 
   * Given a protocol, return the launch command needed to execute it.
   * 
   * @param protocol - A protocol (http, https, mailto, rtsp, ftp, etc.)
   * 
   * @return - A string given the pathspec and parameters to launch
   * the program that handles the given protocol.  Will return 'null' if
   * it can't find an adequate program, or if the JVM doesn't support
   * registry operations.
   */
  public static String getBrowser(String protocol) {
    String clientName;

    clientName = getKeyDefault("HKEY_CLASSES_ROOT\\\\" + protocol + "\\shell\\open\\command");
    if(clientName == null) {
      clientName = getIndirect(protocol);
      if(clientName == null) return null;
    }
    return clientName;
  }

  /** 
   * Returns the control value associated with each of the toplevel
   * registry elements in a given registry path.
   * 
   * @param path - A registry key that starts with a known root.
   * 
   * @return - An int representing the value to pass into registry
   * functions to retrieve the registry values along the given path.
   *
   * @throws Exception - An Exception with info on what failed.
   */
  public static int getHKEY(String path) throws Exception {
    if (path.startsWith("HKEY_CURRENT_USER")) {
      return 0x80000001;
    }
    else if (path.startsWith("HKEY_LOCAL_MACHINE")) {
      return 0x80000002;
    }
    else if (path.startsWith("HKEY_CLASSES_ROOT")) {
      return 0x80000000;
    }
    else {
      throw new Exception("Path should start with HKEY_CURRENT_USER " + 
                          "or HKEY_LOCAL_MACHINE");
    }
  }

  /** 
   * Remove the registry root from a registry path, and turn the rest
   * of the path into a format compatible with the registry functions.
   * 
   * @param path - The path to strip the root off of.
   * 
   * @return - The registry path turned into a registry-compatible byte array.
   */
  public static byte[] stripHKEY(String path) {
    int beginIndex = path.indexOf("\\\\");
    return stringToByteArray(path.substring(beginIndex+2));
  }

  /** 
   * Get a string return from a registry key and value search.  Uses
   * reflection to keep from bollixing up older JVMs.
   * 
   * @param hkey - The integer identifier of the branch of the registry to search.
   * @param WINDOWS_ROOT_PATH - The path (in bytearray form) as returned from stripHKEY.
   * @param key - The value at that point in the registry to return.
   * 
   * @return - The data associated with the given key at the W_R_P
   * point in the registry, under the root node hkey.
   *
   * @throws Exception - An Exception with info on what failed.
   */
  public static String getValue(int hkey, byte[] WINDOWS_ROOT_PATH, String key) throws Exception {
    Class theClass = Class.forName("java.util.prefs.WindowsPreferences");
    byte[] windowsName;
    int nativeHandle;
    Object value;
    int[] result;
    Method m;

    result = openKey1(hkey, windowsAbsolutePath(WINDOWS_ROOT_PATH),
                      KEY_QUERY_VALUE);
    if (result[ERROR_CODE] != ERROR_SUCCESS) {
      throw new Exception("Path not found!");
    }
    nativeHandle = result[NATIVE_HANDLE];

    m = theClass.getDeclaredMethod("WindowsRegQueryValueEx", int.class, byte[].class);
    m.setAccessible(true);

    windowsName = toWindowsName(key);
    value = m.invoke(null, nativeHandle, windowsName);
    WindowsRegCloseKey(nativeHandle);

    if (value == null) {
      throw new Exception("Path found.  Key not found.");
    }

    byte[] origBuffer = (byte[]) value;
    if(origBuffer.length == 0) return null;

    byte[] destBuffer = new byte[origBuffer.length - 1];
    System.arraycopy(origBuffer, 0, destBuffer, 0, origBuffer.length - 1);

    return new String(destBuffer);
  }

  /** 
   * Using reflection, call the windows registry close key function to
   * close the current key.
   * 
   * @param nativeHandle - The system native value used to refer to the current key.
   * 
   * @return - The return value from the WindowsRegCloseKey reflected call.
   *
   * @throws Exception - An Exception with info on what failed.
   */
  public static int WindowsRegCloseKey(int nativeHandle) throws Exception {
    Class theClass = Class.forName("java.util.prefs.WindowsPreferences");
    Method m = theClass.getDeclaredMethod("WindowsRegCloseKey", int.class);
    Object ret;

    m.setAccessible(true);

    ret = m.invoke(null, nativeHandle);
    return (Integer) ret;
  }

  /** 
   * Using reflection, call the windows registry open key function to
   * open a new key to get data from.
   * 
   * @param hkey - The int value of the registry root tree to look in.
   * @param windowsAbsolutePath - The path of the key to look up
   * @param securityMask - What is being done, so the security system can interrupt
   * 
   * @return - An array where [0] is a handle to the key, and [1] is
   * an error code, if any errors were found, or ERROR_SUCCESS if no
   * errors.
   *
   * @throws Exception - An Exception with info on what failed.
   */
  public static int[] openKey1(int hkey, byte[] windowsAbsolutePath, int securityMask) throws Exception {
    Class theClass = Class.forName("java.util.prefs.WindowsPreferences");
    Method m = theClass.getDeclaredMethod("WindowsRegOpenKey", int.class, byte[].class, int.class);
    m.setAccessible(true);
    Object ret = m.invoke(null, hkey, windowsAbsolutePath, securityMask);
    return (int[]) ret;
  }

  /** 
   * Convert a string into a byte[] for use with the actual registry functions.
   * 
   * @param str - The string to convert.
   * 
   * @return - An array (byte[]) containing the characters from the string.
   */
  private static byte[] stringToByteArray(String str) {
    byte[] result = new byte[str.length() + 1];
    for (int i = 0; i < str.length(); i++) {
      result[i] = (byte) str.charAt(i);
    }
    result[str.length()] = 0;
    return result;
  } 

  /** 
   * Returns a byte array with the absolute path stored into it,
   * potentially with a terminating '\' added.
   * 
   * @param WINDOWS_ROOT_PATH - The root path to translate.
   * 
   * @return - The absolute version of the translated path.
   */
  private static byte[] windowsAbsolutePath(byte[] WINDOWS_ROOT_PATH) {
    ByteArrayOutputStream bstream = new ByteArrayOutputStream();
    bstream.write(WINDOWS_ROOT_PATH, 0, WINDOWS_ROOT_PATH.length - 1);
    StringTokenizer tokenizer = new StringTokenizer("/", "/");
    while (tokenizer.hasMoreTokens()) {
      bstream.write((byte) '\\');
      String nextName = tokenizer.nextToken();
      byte[] windowsNextName = toWindowsName(nextName);
      bstream.write(windowsNextName, 0, windowsNextName.length - 1);
    }
    bstream.write(0);
    return bstream.toByteArray();
  }

  /** 
   * Process a name into a Windows name from a Java name (path
   * conversion?)
   * 
   * @param javaName - The name to translate
   * 
   * @return - A byte array containing the Windows translated name.
   */
  private static byte[] toWindowsName(String javaName) {
    StringBuffer windowsName = new StringBuffer();
    for (int i = 0; i < javaName.length(); i++) {
      char ch = javaName.charAt(i);
      if ((ch < 0x0020) || (ch > 0x007f)) {
        throw new RuntimeException("Unable to convert to Windows name");
      }
      if (ch == '\\') {
        windowsName.append("//");
      } else if (ch == '/') {
        windowsName.append('\\');
      } else if ((ch >= 'A') && (ch <= 'Z')) {
        windowsName.append("/").append(ch);
      } else {
        windowsName.append(ch);
      }
    }
    return stringToByteArray(windowsName.toString());
  }

  public static Process Launch(String destURL) throws IOException {
    String extractor = destURL.substring(0,destURL.indexOf(':'));
    String launcher=getBrowser(extractor);
    String cmdLine[];

    if(launcher == null) launcher = getBrowser("http");
    if(launcher == null) return null;

    cmdLine = splitCommandLine(launcher, destURL);
    return Runtime.getRuntime().exec(cmdLine);
  }
}
