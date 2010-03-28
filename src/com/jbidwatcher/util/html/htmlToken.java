package com.jbidwatcher.util.html;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/**
 * User: Administrator
 * Date: Jun 26, 2004
 * Time: 3:05:47 PM
 *
 * A storage class to keep track of token information.  Each htmlToken is a piece of the document,
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
