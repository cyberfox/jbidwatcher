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
 * To change this template use File | Settings | File Templates.
 */
public interface JHTMLListener {
  public abstract void addToken(htmlToken newToken, int contentIndex);
}
