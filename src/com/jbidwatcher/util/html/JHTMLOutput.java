package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*!@class JHTMLOutput
 *
 * @brief Dump a stringbuffer out, surrounded by appropriate HTML tags.
 *
 */

public class JHTMLOutput {
  private static final int FOURK = 4096;
  private StringBuffer _page = new StringBuffer(FOURK);

  public JHTMLOutput(String title, String body) {
    buildPage(title, new StringBuffer(body));
  }

  public JHTMLOutput(String title, StringBuffer body) {
    buildPage(title, body);
  }

  private void buildPage(String title, StringBuffer body) {
    _page.append("<HTML><HEAD><TITLE>");
    _page.append(title);
    _page.append("</TITLE></HEAD>\n");
    _page.append("<BODY>\n");
    _page.append(body.toString());
    _page.append("</BODY>\n</HTML>\n");
  }

  public StringBuffer getStringBuffer() { return _page; }
}
