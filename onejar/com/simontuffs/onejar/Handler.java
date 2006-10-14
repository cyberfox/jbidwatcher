/*
 * Copyright (c) 2004, P. Simon Tuffs (simon@simontuffs.com)
 * All rights reserved.
 *
 * See full license at http://one-jar.sourceforge.net/one-jar-license.txt
 * This license is also included in the distributions of this software
 * under doc/one-jar-license.txt
 */

package com.simontuffs.onejar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * @author simon@simontuffs.com
 *
 */
public class Handler extends URLStreamHandler {

	/**
	 * This procol name must match the name of the package in which this class
	 * lives.
	 */
	public static String PROTOCOL = "onejar";

	protected int len = PROTOCOL.length()+1;
	
	/** 
	 * @see java.net.URLStreamHandler#openConnection(java.net.URL)
	 */
	protected URLConnection openConnection(URL u) throws IOException {
		final String resource = u.toString().substring(len);
		return new URLConnection(u) {
			public void connect() {
			}
			public InputStream getInputStream() {
				// Use the Boot classloader to get the resource.  There
				// is only one per one-jar.
				JarClassLoader cl = Boot.getClassLoader();
				return cl.getByteStream(resource);
			}
		};
	}

}
