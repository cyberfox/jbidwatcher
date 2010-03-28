package com.jbidwatcher.util.http;
/*
 * Copyright (c) 2000-2007, CyberFOX Software, Inc. All Rights Reserved.
 *
 * Developed by mrs (Morgan Schweers)
 */

/*!@class Cookie
 * @brief A single HTTP cookie, kept by the CookieJar.
 */
public class Cookie {
  private String _key;
  private String _value;
  private String _wholeCookie;

  public Cookie(String newCookie) {
    setCookie(newCookie);
  }

  public String getCookieKey(String inCookie) {
    int idxEquals = inCookie.indexOf('=');

    if(idxEquals == -1) {
      return null;
    }

    return(inCookie.substring(0,idxEquals));
  }

  public String toString() { return _wholeCookie; }

  public String getKey() { return _key; }

  public String getValue() { return _value; }

  public final void setCookie(String newCookie) {
    int idxEquals = newCookie.indexOf('=');

    _key = newCookie.substring(0,idxEquals);
    _value = newCookie.substring(idxEquals+1, newCookie.indexOf(';'));
    _wholeCookie = newCookie;
  }

  public boolean sameKey(String test_key) {
    return(_key.equals(test_key.substring(0,test_key.indexOf('='))));
  }
}
