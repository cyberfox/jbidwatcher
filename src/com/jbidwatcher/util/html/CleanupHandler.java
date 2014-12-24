package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

public interface CleanupHandler {
  /**
   * Clean up the page loaded and prepare it for parsing later.
   *
   * @param sb The object containing the entire HTML source for the page to parse.
   */
  public void cleanup(StringBuffer sb);
}
