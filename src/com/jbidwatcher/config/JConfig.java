package com.jbidwatcher.config;//  -*- Java -*-
//
//  History:
//  mrs: 06-July-2003 12:47 - Added default return value for display configurations.
//  mrs: 28-June-2000 18:37 - Added a 'queryConfiguration' function with a default.
//  mrs: 24-March-2000 05:25 - Really remove the JNews specific stuff and make it a very generic configuration file.
//  Administrator: 11-October-1999 21:17 - Removed most JNews specific variables...
//  mrs: 24-July-1999 00:02 - Added display information saving/loading.
//  mrs: 23-July-1999 10:23 - Mostly comment changes.  Added a few more configurable items.
//  mrs: 23-July-1999 00:17 - Fixed up so it's an entirely static class.  Used
//                            purely as a central repository for all cfg info.
//  mrs: 22-July-1999 23:57 - First version.  Contains the  configuration
//                            information, theoretically loaded once on startup.

/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
 */

import com.jbidwatcher.platform.Platform;
import com.jbidwatcher.util.ErrorManagement;
import com.jbidwatcher.util.Base64;
import com.jbidwatcher.util.html.JHTML;
import com.jbidwatcher.Constants;

import java.io.*;
import java.util.*;
import java.awt.Point;
import java.awt.Dimension;
import java.awt.Toolkit;
import javax.swing.*;

public class JConfig {
  //  Only one property instance.  This class is never 'new'ed,
  //  it's purely used to keep track of important config info.
  protected static Properties soleProperty = new Properties();
  protected static Properties displayProperty;
  protected static Properties mAuxProps = null;
  protected static Runtime curRuntime = Runtime.getRuntime();

  protected static String _configFileName = null;

  //  All the available JConfig.<information> fields.  They should be
  //  set to 'sane' defaults initially, under the theory that the
  //  program may never actually do a loadconfig.  This is ONLY true
  //  for now, since the server is absolutely necessary.  -- note

  public static int screenx, screeny, height, width;

  //  Interesting point:  If I make this a function, and take an Object, I
  //  could xlate the Object passed in to a class name string, and instrument
  //  debugging messages on a class-by-class basis by using:
  //
  //  if(JConfig.debugging(this)) ...
  //
  public static volatile boolean debugging = true;

  //  A vector of ConfigListener classes who (once they've registered) will be told
  //  when a configuration change is made.
  private static Vector<ConfigListener> _listeners = new Vector<ConfigListener>();

  //  Were there any configuration changes since the last updateComplete()?
  private static boolean _anyUpdates = false;

  //  A core loader which loads from an InputStream.  Used so that we can
  //  load config files from a resource in a JAR file.
  public static void load(InputStream inConfigFile) {
    try {
      if(inConfigFile != null) {
        soleProperty.load(inConfigFile);
        inConfigFile.close();
      }
    } catch(IOException e) {
      ErrorManagement.handleException("Fatal error loading config file.", e);
      System.exit(1);
    }

    handleConfigLoading();
  }

  public interface ConfigListener {
    void updateConfiguration();
  }

  //  Eventually it might be possible to check the class, and
  //  instrument debugging on a per-class basis.
  public static boolean debugging() {
    return debugging;
  }

  public static void registerListener(ConfigListener jcl) {
    _listeners.add(jcl);
  }

  public static void killAll(String prefix) {
    Set ks = soleProperty.keySet();
    Iterator it = ks.iterator();

    while(it.hasNext()) {
      String key = (String)it.next();
      if(key.startsWith(prefix)) it.remove();
    }
  }

  public static void kill(String key) {
    Set ks = soleProperty.keySet();
    Iterator it = ks.iterator();

    while(it.hasNext()) {
      String stepKey = (String)it.next();
      if(stepKey.equals(key)) it.remove();
    }
  }

  /**
   * @brief Identify what OS the program is running on.
   * 
   * 
   * @return - A string containing windows, linux, or something else
   * identifying the OS.
   */
  public static String getOS() {
    String rawOSName = System.getProperty("os.name");
    int spaceIndex = rawOSName.indexOf(' ');
    String osName;

    if (spaceIndex == -1) {
      osName = rawOSName;
    } else {
      osName = rawOSName.substring(0, spaceIndex);
    }

    return osName;
  }

  public static void updateComplete() {
    setDebugging(queryConfiguration("debugging", "false").equals("true"));

    if(_anyUpdates) {
      for (ConfigListener jcl : _listeners) {
        jcl.updateConfiguration();
      }
    }
    _anyUpdates = false;
  }

  private static void handleConfigLoading() {
    passwordUnfixup_b64(soleProperty);

    if (soleProperty.containsKey("debugging")) {
      String debugFlag = soleProperty.getProperty("debugging");

      if (debugFlag.equalsIgnoreCase("true"))
        setDebugging(true);
      else if (debugFlag.equalsIgnoreCase("false"))
        setDebugging(false);
      else {
        ErrorManagement.logMessage("debugging flag is bad, only 'true' or 'false' allowed.  Presuming true.");
        setDebugging(true);
      }
    }
  }

  //  configFile can be null, in which case we use defaults.
  //  Not such a hot idea with the server.  -- note
  public static void load(String configFile) {
    _configFileName = configFile;

    if(configFile != null) {
      try {
        load(new FileInputStream(configFile));
      } catch (FileNotFoundException e) {
        ErrorManagement.handleException("Property file " + configFile + " not found.  Retaining default settings!\n", e);
      }
    }
  }

  public static void saveArbitrary(String cfgName, Properties arbProps) {
    try {
      FileOutputStream fos = new FileOutputStream(cfgName);
      arbProps.store(fos, "Configuration information.  Do not modify while running.");
      fos.close();
    } catch(IOException e) {
      //  D'oh.  It failed to write the display information...
      ErrorManagement.handleException("Failed to write configuration: " + cfgName, e);
    }
  }

  public static Properties loadArbitrary(InputStream inCfgStream) {
    Properties slopsProps;

    try {
      slopsProps = new Properties();
      slopsProps.load(inCfgStream);
      inCfgStream.close();
    } catch(IOException e) {
      ErrorManagement.handleException("Failed to load arbitrary stream configuration.", e);
      slopsProps = null;
    }

    return slopsProps;
  }

  public static Properties loadArbitrary(String cfgName) {
    File checkExistence = new File(cfgName);

    if(checkExistence.exists()) {
      try {
        FileInputStream fis = new FileInputStream(cfgName);
        return(loadArbitrary(fis));
      } catch(IOException e) {
        //  What do we do?  --  hackhack
        ErrorManagement.handleException("Failed to load configuration " + cfgName, e);
      }
    }
    return(null);
  }

  /** 
   * @brief Gets a path to the 'optimal' place to put application-specific files.
   *
   * @param dirname - The directory to add to the app-specific location.
   * 
   * @return - A String containing the OS-specific place to put our files.
   */
  public static String getHomeDirectory(String dirname) {
    String sep = System.getProperty("file.separator");
    String homePath;

    //noinspection ConstantConditions
    if(false && Platform.isMac()) {
      homePath = System.getProperty("user.home") +
                 sep + "Library" + sep + "Preferences" + sep + '.' + dirname;
    } else {
      homePath = System.getProperty("user.home") + sep + '.' + dirname;
    }

    File fp = new File(homePath);
    if(!fp.exists()) fp.mkdirs();

    return homePath;
  }

  /** 
   * @brief Find the 'best' location for a file.
   *
   * If the file has a path, presume it's correct.
   * If it's just a filename, try to find it at the users (application) home directory.
   * If it's not there, just load it from the current directory.
   * 
   * @param fname - The file name to hunt for.
   * @param dirname - The ending directory for this application.
   * @param mustExist - false if we just want to find out the best place to put it.
   * 
   * @return - A string containing the 'best' version of a given file.
   */
  public static String getCanonicalFile(String fname, String dirname, boolean mustExist) {
    String outName = fname;
    String pathSeparator = System.getProperty("file.separator");

    //  Is it a path?  If so, we don't want to override it!
    if(fname.indexOf(pathSeparator) == -1) {
      String configPathFile = getHomeDirectory(dirname) + pathSeparator + fname;

      if(mustExist) {
        File centralConfig = new File(configPathFile);

        if(centralConfig.exists() && centralConfig.isFile()) {
          outName = configPathFile;
        }
      } else {
        outName = configPathFile;
      }
    }

    return outName;
  }

  public static void snapshotDisplay(JFrame inFrame) {
    Point tempPoint = inFrame.getLocationOnScreen();
    setConfiguration("temp.last.screenx", Integer.toString(tempPoint.x));
    setConfiguration("temp.last.screeny", Integer.toString(tempPoint.y));
    setConfiguration("temp.last.height", Integer.toString(inFrame.getHeight()));
    setConfiguration("temp.last.width", Integer.toString(inFrame.getWidth()));
  }

  public static void saveDisplayConfig(JFrame inFrame, Properties auxProps) {
    Properties displayProps = new Properties();
    if(inFrame.isVisible()) {
      Point tempPoint = inFrame.getLocationOnScreen();

      displayProps.setProperty("screenx", Integer.toString(tempPoint.x));
      displayProps.setProperty("screeny", Integer.toString(tempPoint.y));

      displayProps.setProperty("height", Integer.toString(inFrame.getHeight()));
      displayProps.setProperty("width", Integer.toString(inFrame.getWidth()));
    } else {
      displayProps.setProperty("screenx", queryConfiguration("temp.last.screenx", "-1"));
      displayProps.setProperty("screeny", queryConfiguration("temp.last.screeny", "-1"));
      displayProps.setProperty("width", queryConfiguration("temp.last.width", "-1"));
      displayProps.setProperty("height", queryConfiguration("temp.last.height", "-1"));
    }

    try {
      String dispFile = getCanonicalFile("display.cfg", "jbidwatcher", false);

      FileOutputStream fos = new FileOutputStream(dispFile);
      displayProps.store(fos, "Display information.  Do not modify while running.");
      if(auxProps != null) {
        auxProps.store(fos, "Column header information.  Do not modify while running.");
      }
      if(mAuxProps != null) {
        mAuxProps.store(fos, "Search display information.  Do not modify while running.");
      }
    } catch(IOException e) {
      //  D'oh.  It failed to write the display information...
      ErrorManagement.handleException("Failed to write display configuration.", e);
    }
  }

  private static void passwordFixup(Properties _inProps) {
    Enumeration what_keys = _inProps.keys();

    while(what_keys.hasMoreElements()) {
      String key = what_keys.nextElement().toString();
      String lcKey = key.toLowerCase();
      if(lcKey.indexOf(JHTML.Form.FORM_PASSWORD) != -1 && lcKey.indexOf("_b64") == -1) {
        String val = _inProps.getProperty(key);

        _inProps.remove(key);
        _inProps.setProperty(key + "_b64", Base64.encodeString(val, false));
      }
    }
  }

  private static void passwordUnfixup_b64(Properties _inProps) {
    Enumeration<Object> what_keys = _inProps.keys();

    while(what_keys.hasMoreElements()) {
      String key = what_keys.nextElement().toString();
      String lcKey = key.toLowerCase();
      if(lcKey.indexOf("_b64") != -1) {
        int b64_start = lcKey.indexOf("_b64");
        String val = _inProps.getProperty(key);

        _inProps.remove(key);
        key = key.substring(0, b64_start) + key.substring(b64_start+4);
        _inProps.setProperty(key, Base64.decodeToString(val));
      }
    }
  }

  public static void setConfigurationFile(String cfgFile) {
    _configFileName = cfgFile;
  }

  public static void saveConfiguration() {
    passwordFixup(soleProperty);
    killAll("temp.");

    if(_configFileName != null) {
      saveArbitrary(_configFileName, soleProperty);
      ErrorManagement.logDebug("Saving to: " + _configFileName);
    } else {
      saveArbitrary("JBidWatch.cfg", soleProperty);
      ErrorManagement.logDebug("Just saving to: JBidWatch.cfg!");
    }
    passwordUnfixup_b64(soleProperty);
  }

  public static void saveConfiguration(String outFile) {
    _configFileName = outFile;
    passwordFixup(soleProperty);
    saveArbitrary(outFile, soleProperty);
    passwordUnfixup_b64(soleProperty);
  }

  public static InputStream bestSource(ClassLoader urlCL, String inConfig) {
    File realConfig = new File(inConfig);

    InputStream configStream = null;

    if(realConfig.exists()) {
      try {
        configStream = new FileInputStream(inConfig);
      } catch(FileNotFoundException ignore) {
        ErrorManagement.logMessage(inConfig + " deleted between existence check and loading!");
      }
    } else {
      //  Just use the files name as the index in the class loader.
      configStream = urlCL.getResourceAsStream(realConfig.getName());
    }
    return configStream;
  }

  public static void loadDisplayConfig(ClassLoader urlCL) {
    Properties displayProps = new Properties();
    boolean fileLoadFailed = false;
    String dispFile = getCanonicalFile("display.cfg", "jbidwatcher", true);

    File checkExistence = new File(dispFile);
    InputStream dispIS = null;
    if(checkExistence.exists()) {
      try {
        dispIS = new FileInputStream(dispFile);
      } catch(FileNotFoundException e) {
        ErrorManagement.handleException(dispFile + " deleted between existence check and loading!", e);
        fileLoadFailed = true;
      }
    } else {
      fileLoadFailed = true;
    }

    if(fileLoadFailed) {
      dispIS = urlCL.getResourceAsStream("/display.cfg");
    }

    //  Preset to zero, because we check this later.
    height = 0;
    width = 0;

    //  TODO -- Multimonitor fix.
    Dimension screensize = Toolkit.getDefaultToolkit().getScreenSize();

    boolean setOwnProps = false;
    if(dispIS != null) {
      try {
        displayProps.load(dispIS);

        //  We could save/load Font, Locale, Background Color too... -- note
        screenx = Integer.parseInt(displayProps.getProperty("screenx", "0"));
        screeny = Integer.parseInt(displayProps.getProperty("screeny", "0"));

        //  If somehow screenx / y have become negative or outside the
        //  screen, we need to reset the program position.
        if(screenx < 0 || screeny < 0 ||
           screenx > screensize.width || screeny > screensize.height) {
          setOwnProps = true;
        }
        height = Integer.parseInt(displayProps.getProperty("height", "0"));
        width = Integer.parseInt(displayProps.getProperty("width", "0"));

        //  If either is invalid, reset it via later code.
        if(height < 0 || width < 0) {
          height = 0;
          width = 0;
          setOwnProps = true;
        }
      } catch (IOException e) {
        ErrorManagement.handleException("Error loading display properties.", e);
        setOwnProps = true;
      }
    } else {
      setOwnProps = true;
    }

    if(setOwnProps) {
      // Plonk it on center of screen
      if(height == 0 || width == 0) {
        height = screensize.height / 2;
        width = screensize.width / 2;
      }
      screenx = (screensize.width - width) / 2;
      screeny = (screensize.height - height) / 2;
      displayProps.setProperty("screenx", Integer.toString(screenx));
      displayProps.setProperty("screeny", Integer.toString(screeny));
      displayProps.setProperty("height", Integer.toString(height));
      displayProps.setProperty("width", Integer.toString(width));
    }
    displayProperty = displayProps;
  }

  public static void setDebugging(boolean doDebug) {
    //  You CANNOT turn off debugging in a pre-release now.
    debugging = Constants.PROGRAM_VERS.indexOf("pre") != -1 || doDebug;
  }

  public static void setDisplayConfiguration(String key, String value) {
    displayProperty.setProperty(key, value);
  }

  public static void setAuxConfiguration(String key, String value) {
    if(mAuxProps == null) mAuxProps = new Properties();
    mAuxProps.setProperty(key, value);
  }

  public static String queryAuxConfiguration(String key, String inDefault) {
    if(mAuxProps == null) return inDefault;
    return mAuxProps.getProperty(key, inDefault);
  }

  public static String queryDisplayProperty(String query) {
    return displayProperty.getProperty(query, null);
  }

  public static String queryDisplayProperty(String query, String inDefault) {
    String retVal = queryDisplayProperty(query);

    if(retVal == null) return inDefault;
    return retVal;
  }

  public static void setConfiguration(String key, String value) {
    _anyUpdates = true;
    soleProperty.setProperty(key, value);
  }

  public static String queryConfiguration(String query, String inDefault) {
    String retVal = queryConfiguration(query);

    if(retVal == null) return inDefault;
    return retVal;
  }

  public static String queryConfiguration(String query) {
    return soleProperty.getProperty(query, null);
  }

  public static List getAllKeys() {
    List<String> keyList = new ArrayList<String>(soleProperty.stringPropertyNames());
    Collections.sort(keyList);
    return keyList;
  }

  public static List<String> getMatching(String prefix) {
    Set keySet = soleProperty.keySet();
    List<String> results = null;
    int prefixLen = prefix.length();

    for (Object aKeySet : keySet) {
      String s = (String) aKeySet;
      if (s.startsWith(prefix)) {
        if (results == null) results = new ArrayList<String>();
        results.add(s.substring(prefixLen));
      }
    }

    return results;
  }

  public static void killDisplay(String key) {
    displayProperty.remove(key);
  }

  public static void addAllToDisplay(Properties replace) {
    displayProperty.putAll(replace);
  }
}
