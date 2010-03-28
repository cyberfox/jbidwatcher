package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/**
 * Created by IntelliJ IDEA.
 * User: Administrator
 * Date: Jun 27, 2004
 * Time: 10:51:49 PM
 *
 * An interface to objects that want to be informed when a token is added
 * during the scan of an HTML document.  (Think simplistic SAX parser.)
 */
public interface JHTMLListener {
  public abstract void addToken(htmlToken newToken, int contentIndex);
}
