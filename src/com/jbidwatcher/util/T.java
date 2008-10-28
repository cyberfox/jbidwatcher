package com.jbidwatcher.util;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

import com.jbidwatcher.util.config.JConfig;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * @author mrs
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class T {
	private static ResourceBundle sResource = ResourceBundle.getBundle("ebay_com");
	/**
	 *
	 */
	private T() {
		//  Don't need to do anything here.
	}

  public static void setBundle(String bundleName) {
    sResource = ResourceBundle.getBundle(bundleName);
  }

  /**
	 * @param key - The key to get out of the properties file.
	 * @return - The value of the provided key in the properties file, or the overridden value if override.{key} is set.
	 */
	public static String s(String key) {
    String override = JConfig.queryConfiguration("override." + key);
    if(override != null) {
      return override;
    }

		try {
			return sResource.getString(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
}