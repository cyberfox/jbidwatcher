package com.jbidwatcher.util.config;
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
	 * @param key
	 * @return
	 */
	public static String getString(String key) {
    String override = JConfig.queryConfiguration("override." + key);
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