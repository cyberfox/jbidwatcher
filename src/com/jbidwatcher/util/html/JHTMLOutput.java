package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2005 CyberFOX Software, Inc. All Rights Reserved.
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published
 * by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this library; if not, write to the
 *  Free Software Foundation, Inc.
 *  59 Temple Place
 *  Suite 330
 *  Boston, MA 02111-1307
 *  USA
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
