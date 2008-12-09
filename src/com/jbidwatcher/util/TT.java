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
public class TT {
  private static ClassLoader urlCL = (ClassLoader) TT.class.getClassLoader();
  private ResourceBundle sResource = ResourceBundle.getBundle("ebay_com");
  private Properties countryProperties = null;

  /**
   * Create a new country-site specific properties matching object.
   *
   * @param countrySite - The name of the site to load.
   */
  public TT(String countrySite) {
    setCountrySite(countrySite);
  }

  public boolean setBundle(String bundleName) {
    boolean successful = false;
    try {
      sResource = ResourceBundle.getBundle(bundleName);
      successful = true;
    } catch(Exception ignored) { }

    InputStream is = JConfig.bestSource(urlCL, bundleName + ".properties");
    if(is != null) {
      countryProperties = new Properties();
      try {
        countryProperties.load(is);
        successful = true;
      } catch (IOException e) {
        ErrorManagement.logDebug("Failed to load country property file for " + bundleName + ".");
      }
    }

    return successful;
  }

  /**
   * @param key - The key to get out of the properties file.
   * @return - The value of the provided key in the properties file, or the overridden value if override.{key} is set.
   */
  public String s(String key) {
    String override = JConfig.queryConfiguration("override." + key);
    if(override == null && countryProperties != null) override = countryProperties.getProperty(key);
    if(override != null) {
      return override;
    }

    try {
      return sResource.getString(key);
    } catch (MissingResourceException e) {
      // Fall back to the generic if it's there...
      return Externalized.getString(key);
    }
  }

  public boolean setCountrySite(String country) {
    String bundle = country.replace('.', '_');
    return setBundle(bundle);
  }
}
