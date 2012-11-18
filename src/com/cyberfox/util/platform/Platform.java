package com.cyberfox.util.platform;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.config.JConfig;

import javax.swing.*;
import java.io.*;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class Platform {
  private static boolean _trayEnabled=false;
  private static boolean traySupportDisabled = false;

  /**
   * @brief Clears up a PMD warning, as this class is entirely static,
   * but is NOT intended to be used as a Singleton.
   */
  private Platform() {
    //  Does nothing.
  }

  /** 
   * @brief Is the current machine a Mac?
   *
   * Note: Since MacOS 9 and prior never had a Java runtime later than
   * 1.1.8, we MUST be running on OSX.  It's an assumption, but a good
   * one.
   * 
   * @return - true if we are running on a Mac, false otherwise.
   */
  public static boolean isMac() {
    return JConfig.queryConfiguration("mac", "false").equals("true");
  }

  public static boolean isLinux() {
    return JConfig.getOS().equalsIgnoreCase("linux");
  }

  public static boolean isWindows() {
    return JConfig.getOS().equalsIgnoreCase("windows");
  }

  public static boolean isVista() {
    return System.getProperty("os.name").startsWith("Windows Vista");
  }

  public static boolean usingSparkle() {
    return Platform.isMac() &&
        (JConfig.queryConfiguration("config.firstrun", "false").equals("true") ||
            JConfig.queryConfiguration("temp.sparkle", "false").equals("true"));
  }

  /**
   * @brief Set up the Mac UI information, based on the configuration.
   */
  public static void setupMacUI() {
    if(isRawMac()) {
      JConfig.setConfiguration("mac", "true");

      //  Set the old and new forms for the screen-menu-bar preference.
      System.setProperty("apple.laf.useScreenMenuBar", "true");
      System.setProperty("com.apple.macos.useScreenMenuBar", "true");

      System.setProperty("Quaqua.tabLayoutPolicy","wrap");

      //  Allow users to override the brushed metal look.
      //      if(JConfig.queryConfiguration("mac.useMetal", "true").equals("false")) {
      //        System.setProperty("apple.awt.brushMetalLook", "false");
      //      }
    }
  }

  // set the Quaqua Look and Feel in the UIManager
  public static boolean setQuaquaFeel(JFrame inFrame) {
    String whatLaF = "ch.randelshofer.quaqua.QuaquaLookAndFeel";
    try {
      UIManager.setLookAndFeel(whatLaF);
      if (inFrame != null) {
        SwingUtilities.updateComponentTreeUI(inFrame);
      }
      return true;
    } catch (Exception exMe) {
      JConfig.log().handleException("Can't set Quaqua UI (" + whatLaF + ")", exMe);
      return false;
    }
  }

  public static void checkLaF(String lookAndFeel) {
    if (isRawMac()) {
      if (javax.swing.UIManager.getLookAndFeel().getClass().getName().equals(lookAndFeel)) {
        JConfig.setConfiguration("mac.aqua", "true");
      } else {
        JConfig.setConfiguration("mac.aqua", "false");
      }
    } else {
      JConfig.setConfiguration("mac", "false");
      JConfig.setConfiguration("mac.aqua", "false");
    }
  }

  public static boolean extractAndLoadLibrary() {
    String outDll = JConfig.queryConfiguration("platform.path", null);
    File fp = new File(outDll);
    File output = new File(fp, "tray.dll");

    //  If tray.dll doesn't exist, or is zero bytes long, then we want to write it out.
    if(!output.exists() || output.length() == 0) {
      //  If it does exist, it must be zero bytes; delete it.
      if(output.exists()) output.delete();
      if(!dumpFile("/platform/tray.dll", output.getAbsolutePath())) return false;
    }

    try {
      //  Re-point to the file, just to be safe.
      File doubleCheck = new File(fp, "tray.dll");
      //  Before trying to load it, make sure it exists AND was written to.
      if(doubleCheck.exists() && doubleCheck.length() != 0) {
        System.load(output.getAbsolutePath());
        return true;
      }
    } catch(Exception e) {
      traySupportDisabled = true;
      JConfig.setConfiguration("temp.trayDLL", "false");
      JConfig.log().logMessage("Couldn't set up tray access: " + e.getLocalizedMessage());
    } catch (UnsatisfiedLinkError ule) {
      traySupportDisabled = true;
      JConfig.setConfiguration("temp.trayDLL", "false");
      JConfig.log().logMessage("Couldn't set up tray access: " + ule.getLocalizedMessage());
    }
    return false;
  }

  public static boolean dumpFile(String inJarName, String destination) {
    File f = new File(destination);
    try {
      if(!f.createNewFile()) {
        JConfig.log().logDebug("Could not create new file: " + destination);
      }
    } catch(Exception e) {
      JConfig.log().handleException("Can't create output file to copy from JAR.", e);
      return false;
    }
    InputStream source = Platform.class.getClassLoader().getResourceAsStream(inJarName);
    if(source == null) {
      if(inJarName.charAt(0) == '/') {
        source = Platform.class.getClassLoader().getResourceAsStream(inJarName.substring(1));
      }
      if (source == null) {
        JConfig.log().logDebug("Failed to open internal resource " + inJarName + "!");
        return false;
      }
    }
    BufferedInputStream in = new BufferedInputStream(source);
    try {
      FileOutputStream out = new FileOutputStream(f);
      byte[] buffer = new byte[16384];
      int read;

      while((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }

      try {
        in.close();
        out.close();
      } catch(Exception e) {
        //  I really don't care about *close* exceptions.
      }
    } catch (IOException e) {
      JConfig.log().handleException("Couldn't extract file (" + inJarName + " from jar to " + destination + ".", e);
      return false;
    }
    return true;
  }

  public static void setTrayEnabled(boolean b) {
    _trayEnabled = b;
  }

  public static boolean isTrayEnabled() {
    return _trayEnabled;
  }

  public static boolean supportsTray() {
    if(!traySupportDisabled && isWindows()) return true;
    if(isLinux() && !JConfig.queryConfiguration("tray.override", "false").equals("true")) return false;
    if(isMac()) return false;

    try {
      Class java6TrayClass = Class.forName("java.awt.SystemTray");
      Method isSupported = java6TrayClass.getMethod("isSupported");
      Object rval = isSupported.invoke(null);
      boolean supported = rval instanceof Boolean && (Boolean) rval;
      if(supported) {
        JConfig.setConfiguration("temp.tray.java6", "true");
      }
      return supported;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean isRawMac() {
    return System.getProperty("mrj.version") != null;
  }

  public static boolean isUSBased() {
    Set<String> US_LOCALES = new HashSet<String>(Arrays.asList("", "US", "CA"));
    return US_LOCALES.contains(Locale.getDefault().getCountry());
  }
}
