package com.cyberfox.util.platform;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.cyberfox.util.config.JConfig;

import javax.swing.*;
import java.awt.*;
import java.io.*;
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

  public static void setTrayEnabled(boolean b) {
    _trayEnabled = b;
  }

  public static boolean isTrayEnabled() {
    return _trayEnabled;
  }

  public static boolean supportsTray() {
    if(!traySupportDisabled && isWindows()) return true;
    if(isLinux() && !JConfig.queryConfiguration("tray.override", "false").equals("true")) return false;

    return SystemTray.isSupported();
  }

  public static boolean isRawMac() {
    return System.getProperty("os.name").contains("OS X");
  }

  public static boolean isUSBased() {
    Set<String> US_LOCALES = new HashSet<String>(Arrays.asList("", "US", "CA"));
    return US_LOCALES.contains(Locale.getDefault().getCountry());
  }
}
