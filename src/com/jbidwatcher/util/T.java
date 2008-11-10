package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;
import com.jbidwatcher.util.config.ErrorManagement;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Properties;
import java.io.InputStream;
import java.io.IOException;

/**
 * @author mrs
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class T {
  private static ClassLoader urlCL = (ClassLoader)T.class.getClassLoader();
  private static ResourceBundle sResource = ResourceBundle.getBundle("ebay_com");
  private static Properties overrideProperties = null;
  /**
   *
   */
  private T() {
    //  Don't need to do anything here.
  }

  public static boolean setBundle(String bundleName) {
    boolean successful = false;
    try {
      sResource = ResourceBundle.getBundle(bundleName);
      successful = true;
    } catch(Exception ignored) { }

    InputStream is = JConfig.bestSource(urlCL, bundleName + ".properties");
    if(is != null) {
      overrideProperties = new Properties();
      try {
        overrideProperties.load(is);
        successful = true;
      } catch (IOException e) {
        ErrorManagement.logDebug("Failed to load property override file for " + bundleName + ".");
      }
    }

    return successful;
  }

  /**
   * @param key - The key to get out of the properties file.
   * @return - The value of the provided key in the properties file, or the overridden value if override.{key} is set.
   */
  public static String s(String key) {
    String override = JConfig.queryConfiguration("override." + key);
    if(override == null && overrideProperties != null) override = overrideProperties.getProperty(key);
    if(override != null) {
      return override;
    }

    try {
      return sResource.getString(key);
    } catch (MissingResourceException e) {
      return '!' + key + '!';
    }
  }

  public static boolean setCountrySite(String country) {
    String bundle = country.replace('.', '_');
    boolean result = T.setBundle(bundle);
    if(result) {
      JConfig.setConfiguration("override.ebayServer.viewHost", "cgi." + country);
    }
    return result;
  }
}
