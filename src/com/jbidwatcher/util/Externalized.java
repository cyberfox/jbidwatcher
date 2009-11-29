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
public class Externalized {
	private static final String BUNDLE_NAME = "jbidwatcher";//$NON-NLS-1$
	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);
	/**
	 *
	 */
	private Externalized() {
		//  Don't need to do anything here.
	}
	/**
	 * @param key - The key to get out of the properties file.
	 * @return - The value of the provided key in the properties file, or the overridden value if override.{key} is set.
	 */
	public static String getString(String key) {
    String override = JConfig.queryConfiguration("replace." + JConfig.getVersion() + '.' + key);
    if(override != null) {
      return override;
    }

		try {
			return RESOURCE_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
}
