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

/**
 * User: Administrator
 * Date: Jun 26, 2004
 * Time: 3:05:47 PM
 *
 * @class A storage class to keep track of token information.  Each htmlToken is a piece of the document,
 * a tag, endtag, singletag, content, or a single EOF at the end.
 */
public class htmlToken {
  public static final int HTML_TAG = 1, HTML_ENDTAG = 2, HTML_SINGLETAG = 3;
  public static final int HTML_CONTENT = 4, HTML_EOF = 5;
  private String token;
  private int tokenType;

  public htmlToken(String tok, int tokType) {
    token = tok;
    tokenType = tokType;
  }

  public String getToken() { return token; }

  public int getTokenType() { return tokenType; }

  public String toString() {
    if ((tokenType != HTML_CONTENT) &&
      (tokenType != HTML_EOF)) {
      return "<" + token + ">";
    } else {
      return token;
    }
  }
}
